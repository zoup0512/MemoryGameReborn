package com.memorygames.pk.ws;

import com.memorygames.pk.admin.AdminMetrics;
import com.memorygames.pk.admin.AdminState;
import com.memorygames.pk.admin.EventRing;
import com.memorygames.pk.admin.SessionStats;
import com.memorygames.pk.protocol.ProtocolCodec;
import com.memorygames.pk.config.PkProperties;
import com.memorygames.pk.match.MatchRecord;
import com.memorygames.pk.match.MatchRecordRepository;
import com.memorygames.pk.match.MatchRoom;
import com.memorygames.pk.match.Matchmaker;
import com.memorygames.pk.match.MatchManager;
import com.memorygames.pk.match.PlayerEndpoint;
import com.memorygames.pk.match.PlayerLifecycle;
import com.memorygames.pk.rating.Rating;
import com.memorygames.pk.season.SeasonService;
import com.memorygames.pk.user.UserAccount;
import com.memorygames.pk.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket gateway (endpoint /ws): guest register/auth, session registry
 * with single-session kick, matchmaking control and in-match event routing.
 *
 * Hardening (P1): registrations/auths/room-joins are rate-limited per
 * session and per IP, oversized frames are dropped, every matchmaking
 * entry path goes through {@link PlayerLifecycle} so one account can never
 * fork itself into two matches, and the register balance field is ignored.
 */
@Component
public class PkWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(PkWebSocketHandler.class);

    private static final int MAX_FRAME_BYTES = 32 * 1024;
    private static final int MAX_UUID_LENGTH = 64;

    private final UserService users;
    private final Matchmaker matchmaker;
    private final MatchManager manager;
    private final PlayerLifecycle lifecycle;
    private final Rating rating;
    private final PkProperties properties;
    private final MatchRecordRepository records;
    private final SeasonService seasons;
    private final AdminState adminState;
    private final EventRing events;
    private final SessionStats stats;
    private final AdminMetrics metrics;

    /** ws session id -> authenticated user id. */
    private final Map<String, Long> authenticated = new ConcurrentHashMap<>();
    /** user id -> ws session id (single live session per account). */
    private final Map<Long, String> sessionByUser = new ConcurrentHashMap<>();
    /** user id -> last known token (for re-resolution after auth). */
    private final Map<Long, String> tokenByUser = new ConcurrentHashMap<>();
    /** ws session id -> shared send endpoint (one bounded queue per session). */
    private final Map<String, PlayerEndpoint.WebSocket> endpoints = new ConcurrentHashMap<>();
    /** user id -> room_join failures inside the current window (code guessing). */
    private final Map<Long, int[]> joinFailures = new ConcurrentHashMap<>();

    private final WsRateLimiter registerPerSession = new WsRateLimiter(60_000L, 5);
    private final WsRateLimiter registerPerIp = new WsRateLimiter(60_000L, 30);
    private final WsRateLimiter authPerSession = new WsRateLimiter(60_000L, 15);
    private final WsRateLimiter messagesPerSession = new WsRateLimiter(10_000L, 200);

    private PlayerEndpoint.WebSocket endpointFor(WebSocketSession session) {
        return this.endpoints.computeIfAbsent(session.getId(),
                id -> new PlayerEndpoint.WebSocket(session, () -> {
                    PkWebSocketHandler.this.metrics.sendOverflow.incrementAndGet();
                    PkWebSocketHandler.this.events.emit("SEND_OVERFLOW", null,
                            "session=" + id);
                }));
    }

    public PkWebSocketHandler(UserService users, Matchmaker matchmaker, MatchManager manager,
                              PlayerLifecycle lifecycle, Rating rating, PkProperties properties,
                              MatchRecordRepository records, SeasonService seasons,
                              AdminState adminState, EventRing events, SessionStats stats,
                              AdminMetrics metrics) {
        this.users = users;
        this.matchmaker = matchmaker;
        this.manager = manager;
        this.lifecycle = lifecycle;
        this.rating = rating;
        this.properties = properties;
        this.records = records;
        this.seasons = seasons;
        this.adminState = adminState;
        this.events = events;
        this.stats = stats;
        this.metrics = metrics;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        this.stats.connected();
        log.debug("ws connected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if (message.getPayloadLength() > MAX_FRAME_BYTES) {
            log.warn("oversized frame ({} bytes) from session {}; closing",
                    message.getPayloadLength(), session.getId());
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        if (!this.messagesPerSession.tryAcquire(session.getId())) {
            // A flood from one session must never reach the room engine.
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        ProtocolCodec.Envelope envelope = ProtocolCodec.parse(message.getPayload());
        if (envelope == null) {
            reply(session, null, ProtocolCodec.T_ERROR, error(ProtocolCodec.ERR_BAD_FRAME, "bad frame"));
            return;
        }
        // Version gate (P2-7): unknown protocol versions fail fast instead of
        // being silently misinterpreted.
        if (envelope.v != ProtocolCodec.VERSION) {
            reply(session, envelope.rid, ProtocolCodec.T_ERROR,
                    error(ProtocolCodec.ERR_UNSUPPORTED_VERSION,
                            "unsupported protocol version " + envelope.v + " (server speaks "
                                    + ProtocolCodec.VERSION + ")"));
            return;
        }
        final String rid = envelope.rid;
        Long userId = this.authenticated.get(session.getId());
        switch (envelope.t) {
            case ProtocolCodec.T_PING:
                endpointFor(session).send(ProtocolCodec.encode(ProtocolCodec.T_PONG, new Object(), rid));
                return;
            case ProtocolCodec.T_REGISTER: {
                if (userId != null) {
                    reply(session, rid, ProtocolCodec.T_ERROR, error(409, "already authenticated"));
                    return;
                }
                String ip = remoteIp(session);
                if (!this.registerPerSession.tryAcquire(session.getId())
                        || !this.registerPerIp.tryAcquire(ip)) {
                    reply(session, rid, ProtocolCodec.T_ERROR, error(429, "too many attempts"));
                    return;
                }
                ProtocolCodec.Register register = ProtocolCodec.convert(envelope.d, ProtocolCodec.Register.class);
                if (register == null || register.uuid == null || register.uuid.isBlank()
                        || register.uuid.length() > MAX_UUID_LENGTH) {
                    reply(session, rid, ProtocolCodec.T_ERROR, error(400, "bad register"));
                    return;
                }
                // The balance field is deliberately ignored: fresh accounts
                // start from the server-side welcome grant only (P1-3).
                UserAccount account = this.users.register(register.uuid, register.nickname);
                if (account.deleted) {
                    reply(session, rid, ProtocolCodec.T_ERROR, error(403, "account deleted"));
                    return;
                }
                if (account.bannedUntil > System.currentTimeMillis()) {
                    reply(session, rid, ProtocolCodec.T_ERROR, error(403, "account banned"));
                    return;
                }
                this.events.emit("AUTH_OK", null,
                        "user=" + account.id + " session=" + session.getId() + " register");
                this.stats.authed();
                bindSession(session, account);
                reply(session, rid, ProtocolCodec.T_AUTH_OK, authOk(account));
                return;
            }
            case ProtocolCodec.T_AUTH: {
                if (userId != null) {
                    return;
                }
                if (!this.authPerSession.tryAcquire(session.getId())) {
                    reply(session, rid, ProtocolCodec.T_ERROR, error(429, "too many attempts"));
                    return;
                }
                ProtocolCodec.Auth auth = ProtocolCodec.convert(envelope.d, ProtocolCodec.Auth.class);
                Optional<UserAccount> account =
                        auth != null && auth.token != null ? this.users.byToken(auth.token) : Optional.empty();
                if (account.isEmpty()) {
                    reply(session, rid, ProtocolCodec.T_ERROR, error(401, "unknown token"));
                    return;
                }
                UserAccount accountGet = account.get();
                if (accountGet.deleted) {
                    reply(session, rid, ProtocolCodec.T_ERROR, error(403, "account deleted"));
                    return;
                }
                if (accountGet.bannedUntil > System.currentTimeMillis()) {
                    reply(session, rid, ProtocolCodec.T_ERROR, error(403, "account banned"));
                    return;
                }
                this.events.emit("AUTH_OK", null,
                        "user=" + accountGet.id + " session=" + session.getId());
                this.stats.authed();
                bindSession(session, accountGet);
                reply(session, rid, ProtocolCodec.T_AUTH_OK, authOk(accountGet));
                // Re-attach to a live match after a reconnect (PRD 4.6).
                MatchRoom room = this.manager.roomFor(accountGet.id);
                if (room != null) {
                    room.onReconnect(accountGet.id, endpoint(session));
                }
                return;
            }
            default:
                break;
        }
        if (userId == null) {
            reply(session, rid, ProtocolCodec.T_ERROR, error(401, "not authenticated"));
            return;
        }
        UserAccount account = this.users.byToken(tokenOfSession(userId)).orElse(null);
        if (account == null) {
            reply(session, rid, ProtocolCodec.T_ERROR, error(401, "unknown token"));
            return;
        }
        MatchRoom room = this.manager.roomFor(userId);
        switch (envelope.t) {
            case ProtocolCodec.T_MATCH_JOIN: {
                // session id on this event is the key trace for diagnosing
                // the intermittent phantom match_join (admin design 4.8).
                this.events.emit("MATCH_JOIN", null,
                        "user=" + userId + " session=" + session.getId());
                if (this.adminState.isMaintenance() || !this.manager.acceptingMatches()) {
                    reply(session, rid, ProtocolCodec.T_ERROR, error(503,
                            this.manager.acceptingMatches()
                                    ? this.adminState.getMaintenanceMessage()
                                    : "server shutting down"));
                    return;
                }
                if (room != null) {
                    reply(session, rid, ProtocolCodec.T_ERROR, error(409, "already in a match"));
                    return;
                }
                Matchmaker.EnqueueResult result = this.matchmaker.enqueue(account, endpoint(session));
                switch (result) {
                    case INSUFFICIENT_COINS:
                        reply(session, rid, ProtocolCodec.T_ERROR, error(402,
                                "not enough coins (need " + this.properties.getEconomy().getEntryFee() + ")"));
                        return;
                    case BUSY:
                        reply(session, rid, ProtocolCodec.T_ERROR, error(409,
                                "already queued or in a match"));
                        return;
                    default:
                        break;
                }
                // Immediate receipt: the client's search watchdog treats a
                // join without this frame as lost to a zombie socket.
                reply(session, rid, ProtocolCodec.T_MATCH_QUEUED, new Object());
                return;
            }
            case ProtocolCodec.T_MATCH_CANCEL:
                this.matchmaker.cancel(userId);
                return;
            case ProtocolCodec.T_BOT_MATCH_REQUEST: {
                // Explicit play-vs-bot duel (docs/bot-battle-prd.md): no queue,
                // the room leaves immediately with mode=PRACTICE.
                this.events.emit("BOT_MATCH_REQUEST", null,
                        "user=" + userId + " session=" + session.getId());
                if (this.adminState.isMaintenance() || !this.manager.acceptingMatches()) {
                    reply(session, rid, ProtocolCodec.T_ERROR, error(503,
                            this.manager.acceptingMatches()
                                    ? this.adminState.getMaintenanceMessage()
                                    : "server shutting down"));
                    return;
                }
                if (!this.lifecycle.claim(userId, PlayerLifecycle.MATCHING)) {
                    reply(session, rid, ProtocolCodec.T_ERROR, error(409, "already queued or in a match"));
                    return;
                }
                ProtocolCodec.BotMatchRequest request =
                        ProtocolCodec.convert(envelope.d, ProtocolCodec.BotMatchRequest.class);
                PkProperties.BotDifficulty difficulty = this.properties.practiceDifficulty(
                        request != null ? request.difficulty : null);
                try {
                    this.manager.createPracticeRoom(account, endpoint(session), difficulty);
                } catch (RuntimeException e) {
                    this.lifecycle.release(userId);
                    reply(session, rid, ProtocolCodec.T_ERROR, error(409, "match launch failed"));
                }
                return;
            }
            case ProtocolCodec.T_LEVEL_WIN:
                if (room != null) {
                    ProtocolCodec.LevelWin msg = ProtocolCodec.convert(envelope.d, ProtocolCodec.LevelWin.class);
                    if (msg != null) {
                        log.info("level_win user={} match={} round={} level={} score={}",
                                userId, room.matchId, msg.round, msg.level, msg.score);
                        room.onLevelWin(userId, msg);
                    }
                }
                return;
            case ProtocolCodec.T_ROUND_COMPLETE:
                if (room != null) {
                    ProtocolCodec.RoundComplete msg = ProtocolCodec.convert(envelope.d, ProtocolCodec.RoundComplete.class);
                    if (msg != null) {
                        room.onRoundComplete(userId, msg);
                    }
                }
                return;
            case ProtocolCodec.T_ROOM_CREATE: {
                if (this.adminState.isMaintenance() || !this.manager.acceptingMatches()) {
                    reply(session, rid, ProtocolCodec.T_ERROR, error(503, "server unavailable"));
                    return;
                }
                if (this.lifecycle.isClaimed(userId)) {
                    reply(session, rid, ProtocolCodec.T_ERROR, error(409, "already queued or in a match"));
                    return;
                }
                String code = this.matchmaker.createFriendRoom(account, endpoint(session));
                if (code == null) {
                    reply(session, rid, ProtocolCodec.T_ERROR, error(409, "already queued or in a match"));
                    return;
                }
                ProtocolCodec.RoomCreated created = new ProtocolCodec.RoomCreated();
                created.code = code;
                reply(session, rid, ProtocolCodec.T_ROOM_CREATED, created);
                return;
            }
            case ProtocolCodec.T_ROOM_JOIN: {
                if (this.adminState.isMaintenance() || !this.manager.acceptingMatches()) {
                    reply(session, rid, ProtocolCodec.T_ERROR, error(503, "server unavailable"));
                    return;
                }
                if (joinThrottled(userId)) {
                    reply(session, rid, ProtocolCodec.T_ERROR, error(429, "too many join attempts"));
                    return;
                }
                if (this.lifecycle.isClaimed(userId)) {
                    reply(session, rid, ProtocolCodec.T_ERROR, error(409, "already queued or in a match"));
                    return;
                }
                ProtocolCodec.RoomJoin join = ProtocolCodec.convert(envelope.d, ProtocolCodec.RoomJoin.class);
                Matchmaker.JoinResult joined = join != null && join.code != null
                        ? this.matchmaker.joinFriendRoom(join.code.trim(), account, endpoint(session))
                        : Matchmaker.JoinResult.NO_SUCH_ROOM;
                if (joined == Matchmaker.JoinResult.NO_SUCH_ROOM) {
                    recordJoinFailure(userId);
                    reply(session, rid, ProtocolCodec.T_ERROR, error(404, "room not found"));
                } else if (joined == Matchmaker.JoinResult.BUSY) {
                    reply(session, rid, ProtocolCodec.T_ERROR, error(409, "already queued or in a match"));
                }
                return;
            }
            case ProtocolCodec.T_ROOM_CANCEL:
                this.matchmaker.cancelFriendRoom(userId);
                return;
            case ProtocolCodec.T_HISTORY_REQUEST: {
                java.util.List<MatchRecord> recent = this.records.findRecentForUser(userId,
                        org.springframework.data.domain.PageRequest.of(0, 20));
                ProtocolCodec.HistoryResponse response = new ProtocolCodec.HistoryResponse();
                response.matches = new ProtocolCodec.HistoryEntry[recent.size()];
                for (int i = 0; i < recent.size(); i++) {
                    MatchRecord record = recent.get(i);
                    boolean asA = record.playerA == userId;
                    ProtocolCodec.HistoryEntry entry = new ProtocolCodec.HistoryEntry();
                    entry.ts = record.settledAt;
                    entry.opponent = asA ? record.nicknameB : record.nicknameA;
                    entry.mode = record.mode;
                    entry.result = asA ? record.resultA : mirrorResult(record.resultA);
                    entry.myScore = asA ? record.scoreA : record.scoreB;
                    entry.oppScore = asA ? record.scoreB : record.scoreA;
                    // The bot always sits in seat B, so the viewer's bot tag
                    // applies exactly when the viewer is seat A.
                    entry.vsBot = record.botOpponent && asA;
                    response.matches[i] = entry;
                }
                reply(session, rid, ProtocolCodec.T_HISTORY_RESPONSE, response);
                return;
            }
            case ProtocolCodec.T_MATCH_START:
                if (room != null) {
                    room.onMatchStart(userId);
                }
                return;
            case ProtocolCodec.T_TOP_REQUEST: {
                java.util.List<UserAccount> top = this.users.topByElo();
                ProtocolCodec.TopResponse response = new ProtocolCodec.TopResponse();
                response.entries = new ProtocolCodec.TopEntry[top.size()];
                for (int i = 0; i < top.size(); i++) {
                    UserAccount ranked = top.get(i);
                    ProtocolCodec.TopEntry entry = new ProtocolCodec.TopEntry();
                    entry.rank = i + 1;
                    entry.nickname = ranked.nickname;
                    entry.elo = ranked.elo;
                    entry.online = this.manager.roomFor(ranked.id) != null;
                    response.entries[i] = entry;
                }
                reply(session, rid, ProtocolCodec.T_TOP_RESPONSE, response);
                return;
            }
            case ProtocolCodec.T_RESIGN:
                log.info("resign received user={} room={}", userId, room != null ? room.matchId : "none");
                if (room != null) {
                    room.onResign(userId);
                }
                return;
            case ProtocolCodec.T_RESUME:
                // M1: resume is implicit in auth; kept for protocol evolution.
                return;
            default:
                // Unknown types are refused loudly (P2-7): a newer client
                // deserves a fast, correlatable failure instead of hanging
                // on a silently dropped command.
                reply(session, rid, ProtocolCodec.T_ERROR,
                        error(ProtocolCodec.ERR_UNKNOWN_TYPE, "unknown message type: " + envelope.t));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = this.authenticated.remove(session.getId());
        this.stats.disconnected();
        if (userId != null) {
            this.stats.deauthed();
        }
        PlayerEndpoint.WebSocket endpoint = this.endpoints.remove(session.getId());
        if (endpoint != null) {
            endpoint.shutdown();
        }
        if (userId == null) {
            return;
        }
        this.sessionByUser.remove(userId, session.getId());
        this.matchmaker.cancel(userId);
        this.matchmaker.cancelFriendRoom(userId);
        MatchRoom room = this.manager.roomFor(userId);
        if (room != null) {
            room.onDisconnect(userId);
        }
        log.debug("ws closed: {} user {}", session.getId(), userId);
    }

    // ------------------------------------------------------------- helpers

    /** 4-digit codes have a small keyspace; failed guesses must throttle. */
    private boolean joinThrottled(long userId) {
        int[] failures = this.joinFailures.get(userId);
        return failures != null && failures[0] >= 10
                && System.currentTimeMillis() - failures[1] < 60_000L;
    }

    private void recordJoinFailure(long userId) {
        long now = System.currentTimeMillis();
        this.joinFailures.compute(userId, (k, v) -> {
            if (v == null || now - v[1] >= 60_000L) {
                return new int[]{1, (int) now};
            }
            v[0]++;
            return v;
        });
        if (this.joinFailures.size() > 10000) {
            this.joinFailures.clear();
        }
    }

    private static String remoteIp(WebSocketSession session) {
        InetSocketAddress remote = session.getRemoteAddress();
        return remote != null && remote.getAddress() != null
                ? remote.getAddress().getHostAddress() : "unknown";
    }

    private void bindSession(WebSocketSession session, UserAccount account) {
        String previousId = this.sessionByUser.put(account.id, session.getId());
        this.authenticated.put(session.getId(), account.id);
        if (previousId != null && !previousId.equals(session.getId())) {
            this.authenticated.remove(previousId);
            log.info("user {} re-authenticated; previous session kicked", account.id);
        }
    }

    private String tokenOfSession(long userId) {
        String token = this.tokenByUser.get(userId);
        return token != null ? token : "";
    }

    private void reply(WebSocketSession session, String rid, String type, Object payload) {
        endpointFor(session).send(ProtocolCodec.encode(type, payload, rid));
    }

    private ProtocolCodec.AuthOk authOk(UserAccount account) {
        this.tokenByUser.put(account.id, account.token);
        ProtocolCodec.AuthOk ok = new ProtocolCodec.AuthOk();
        ok.token = account.token;
        ok.userId = account.id;
        ok.nickname = account.nickname;
        ok.elo = account.elo;
        ok.rank = this.rating.rankIndex(account.elo);
        ok.wins = account.wins;
        ok.losses = account.losses;
        ok.draws = account.draws;
        ok.balance = account.balance;
        ok.entryFee = this.properties.getEconomy().getEntryFee();
        ok.winCoins = this.properties.getEconomy().getWinCoins();
        ok.drawCoins = this.properties.getEconomy().getDrawCoins();
        try {
            SeasonService.Snapshot snapshot = this.seasons.snapshot();
            ok.seasonIndex = snapshot.index;
            ok.seasonEndsAt = snapshot.endAt;
        } catch (RuntimeException ignored) {
            ok.seasonIndex = 1;
            ok.seasonEndsAt = System.currentTimeMillis() + 30L * 86400000L;
        }
        // Practice (vs-bot) extras: streak for the picker card, and the live
        // tier table so its rows mirror the admin's hot config.
        ok.practiceStreak = account.practiceStreak != null ? account.practiceStreak : 0;
        java.util.List<PkProperties.BotDifficulty> tiers =
                this.properties.getPractice().getDifficulties();
        ok.practiceTiers = new ProtocolCodec.PracticeTier[tiers.size()];
        for (int i = 0; i < tiers.size(); i++) {
            PkProperties.BotDifficulty tier = tiers.get(i);
            ProtocolCodec.PracticeTier view = new ProtocolCodec.PracticeTier();
            view.id = tier.getId();
            view.elo = tier.getElo();
            view.winCoins = tier.getWinCoins();
            ok.practiceTiers[i] = view;
        }
        return ok;
    }

    private static String mirrorResult(String resultA) {
        if (ProtocolCodec.RESULT_WIN.equals(resultA)) {
            return ProtocolCodec.RESULT_LOSE;
        }
        if (ProtocolCodec.RESULT_LOSE.equals(resultA)) {
            return ProtocolCodec.RESULT_WIN;
        }
        return ProtocolCodec.RESULT_DRAW;
    }

    private static ProtocolCodec.ErrorMsg error(int code, String message) {
        ProtocolCodec.ErrorMsg err = new ProtocolCodec.ErrorMsg();
        err.code = code;
        err.message = message;
        return err;
    }

    private PlayerEndpoint endpoint(WebSocketSession session) {
        return endpointFor(session);
    }
}
