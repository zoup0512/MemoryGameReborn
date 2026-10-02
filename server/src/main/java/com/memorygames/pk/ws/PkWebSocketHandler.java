package com.memorygames.pk.ws;

import com.memorygames.pk.protocol.ProtocolCodec;
import com.memorygames.pk.config.PkProperties;
import com.memorygames.pk.match.MatchRoom;
import com.memorygames.pk.match.Matchmaker;
import com.memorygames.pk.match.MatchManager;
import com.memorygames.pk.match.PlayerEndpoint;
import com.memorygames.pk.rating.Rating;
import com.memorygames.pk.user.UserAccount;
import com.memorygames.pk.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket gateway (endpoint /ws): guest register/auth, session registry
 * with single-session kick, matchmaking control and in-match event routing.
 */
@Component
public class PkWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(PkWebSocketHandler.class);

    private final UserService users;
    private final Matchmaker matchmaker;
    private final MatchManager manager;
    private final Rating rating;
    private final PkProperties properties;

    /** ws session id -> authenticated user id. */
    private final Map<String, Long> authenticated = new ConcurrentHashMap<>();
    /** user id -> ws session id (single live session per account). */
    private final Map<Long, String> sessionByUser = new ConcurrentHashMap<>();
    /** user id -> last known token (for re-resolution after auth). */
    private final Map<Long, String> tokenByUser = new ConcurrentHashMap<>();
    /** ws session id -> shared send endpoint (one lock per session). */
    private final Map<String, PlayerEndpoint.WebSocket> endpoints = new ConcurrentHashMap<>();

    private PlayerEndpoint.WebSocket endpointFor(WebSocketSession session) {
        return this.endpoints.computeIfAbsent(session.getId(), id -> new PlayerEndpoint.WebSocket(session));
    }

    public PkWebSocketHandler(UserService users, Matchmaker matchmaker, MatchManager manager,
                              Rating rating, PkProperties properties) {
        this.users = users;
        this.matchmaker = matchmaker;
        this.manager = manager;
        this.rating = rating;
        this.properties = properties;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.debug("ws connected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        ProtocolCodec.Envelope envelope = ProtocolCodec.parse(message.getPayload());
        if (envelope == null) {
            return;
        }
        Long userId = this.authenticated.get(session.getId());
        switch (envelope.t) {
            case ProtocolCodec.T_PING:
                endpointFor(session).send(ProtocolCodec.encode(ProtocolCodec.T_PONG, new Object()));
                return;
            case ProtocolCodec.T_REGISTER: {
                if (userId != null) {
                    reply(session, ProtocolCodec.T_ERROR, error(409, "already authenticated"));
                    return;
                }
                ProtocolCodec.Register register = ProtocolCodec.convert(envelope.d, ProtocolCodec.Register.class);
                if (register == null || register.uuid == null || register.uuid.isBlank()) {
                    reply(session, ProtocolCodec.T_ERROR, error(400, "bad register"));
                    return;
                }
                UserAccount account = this.users.register(register.uuid, register.nickname, register.balance);
                bindSession(session, account);
                reply(session, ProtocolCodec.T_AUTH_OK, authOk(account));
                return;
            }
            case ProtocolCodec.T_AUTH: {
                if (userId != null) {
                    return;
                }
                ProtocolCodec.Auth auth = ProtocolCodec.convert(envelope.d, ProtocolCodec.Auth.class);
                Optional<UserAccount> account =
                        auth != null && auth.token != null ? this.users.byToken(auth.token) : Optional.empty();
                if (account.isEmpty()) {
                    reply(session, ProtocolCodec.T_ERROR, error(401, "unknown token"));
                    return;
                }
                bindSession(session, account.get());
                reply(session, ProtocolCodec.T_AUTH_OK, authOk(account.get()));
                // Re-attach to a live match after a reconnect (PRD 4.6).
                MatchRoom room = this.manager.roomFor(account.get().id);
                if (room != null) {
                    room.onReconnect(account.get().id, endpoint(session));
                }
                return;
            }
            default:
                break;
        }
        if (userId == null) {
            reply(session, ProtocolCodec.T_ERROR, error(401, "not authenticated"));
            return;
        }
        UserAccount account = this.users.byToken(tokenOfSession(userId)).orElse(null);
        if (account == null) {
            reply(session, ProtocolCodec.T_ERROR, error(401, "unknown token"));
            return;
        }
        MatchRoom room = this.manager.roomFor(userId);
        switch (envelope.t) {
            case ProtocolCodec.T_MATCH_JOIN: {
                if (room != null) {
                    reply(session, ProtocolCodec.T_ERROR, error(409, "already in a match"));
                    return;
                }
                boolean accepted = this.matchmaker.enqueue(account, endpoint(session));
                if (!accepted) {
                    reply(session, ProtocolCodec.T_ERROR, error(402,
                            "not enough coins (need " + this.properties.getEconomy().getEntryFee() + ")"));
                }
                return;
            }
            case ProtocolCodec.T_MATCH_CANCEL:
                this.matchmaker.cancel(userId);
                return;
            case ProtocolCodec.T_LEVEL_WIN:
                if (room != null) {
                    ProtocolCodec.LevelWin msg = ProtocolCodec.convert(envelope.d, ProtocolCodec.LevelWin.class);
                    if (msg != null) {
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
            case ProtocolCodec.T_RESIGN:
                if (room != null) {
                    room.onResign(userId);
                }
                return;
            case ProtocolCodec.T_RESUME:
                // M1: resume is implicit in auth; kept for protocol evolution.
                return;
            default:
                // Unknown types are ignored for forward compatibility.
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = this.authenticated.remove(session.getId());
        this.endpoints.remove(session.getId());
        if (userId == null) {
            return;
        }
        this.sessionByUser.remove(userId, session.getId());
        this.matchmaker.cancel(userId);
        MatchRoom room = this.manager.roomFor(userId);
        if (room != null) {
            room.onDisconnect(userId);
        }
        log.debug("ws closed: {} user {}", session.getId(), userId);
    }

    // ------------------------------------------------------------- helpers

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

    private void reply(WebSocketSession session, String type, Object payload) {
        endpointFor(session).send(ProtocolCodec.encode(type, payload));
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
        return ok;
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
