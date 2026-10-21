package com.cube.memorygames.api.online;

import com.cube.memorygames.diagnostics.DiagnosticLog;
import com.cube.memorygames.diagnostics.DiagnosticSanitizer;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.cube.memorygames.api.local.LocalDataManager;
import com.cube.memorygames.api.online.OnlineMessages.AuthOk;
import com.cube.memorygames.api.online.OnlineMessages.LevelWin;
import com.cube.memorygames.api.online.OnlineMessages.MatchEnd;
import com.cube.memorygames.api.online.OnlineMessages.MatchFound;
import com.cube.memorygames.api.online.OnlineMessages.OpponentScore;
import com.cube.memorygames.api.online.OnlineMessages.RoundComplete;
import com.cube.memorygames.api.online.OnlineMessages.RoundEnd;
import com.cube.memorygames.api.online.OnlineMessages.RoundStart;

/**
 * Application-level online PK session. Owns the WebSocket connection, the
 * guest auth handshake, matchmaking state and the active match snapshot.
 *
 * The game activities talk to it through three bridge calls that are wired
 * into the original online branches of Game1MemoryGridActivity:
 * {@link #reportLevelWin(int, int)} (from updateOnlineScore),
 * {@link #onRoundFinished(int)} (from showOnlineReplayDialog) and
 * {@link #resign()} (from the online back-press confirmation).
 */
public class OnlineSession {

    /** Intent extras understood by Game1MemoryGridActivity subclasses. */
    public static final String EXTRA_ONLINE_GAME = "onlineGameMode";
    public static final String EXTRA_ONLINE_SEED = "onlineGameSeed";
    public static final String EXTRA_ONLINE_ROUND = "onlineGameRound";

    public static final int STATE_DISCONNECTED = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_AUTHORIZING = 2;
    public static final int STATE_IDLE = 3;
    public static final int STATE_QUEUE = 4;
    public static final int STATE_MATCH = 5;

    /** Match settlement results (wire values). */
    public static final String RESULT_WIN = "W";
    public static final String RESULT_LOSE = "L";
    public static final String RESULT_DRAW = "D";

    /** Match modes (MatchFound.mode). */
    public static final String MODE_RANKED = "RANKED";
    public static final String MODE_FRIEND = "FRIEND";
    public static final String MODE_PRACTICE = "PRACTICE";

    private static final long RESIGN_RETRY_MS = 3000L;
    private static final int RESIGN_RETRY_MAX = 6;
    private static final long RECONNECT_BASE_MS = 1000L;
    private static final long RECONNECT_MAX_MS = 15000L;
    private static final long RECONNECT_WINDOW_MS = 90000L;
    /** Server accept timeout (30s) plus one second for the cancellation frame. */
    private static final long MATCH_ACCEPT_FALLBACK_MS = 31000L;
    private static final long POST_AUTH_MATCH_GRACE_MS = 3000L;

    /** UI callback invoked on the main thread. */
    public interface MatchListener {
        void onStateChanged(int state);

        void onAuthOk(AuthOk profile);

        void onMatchFound(MatchFound match);

        void onMatchCancelled(MatchAcceptance.Action action, String mode);

        void onRoundStart(RoundStart round);

        void onOpponentScore(OpponentScore score);

        void onRoundEnd(RoundEnd round);

        void onMatchEnd(MatchEnd end);

        /** Both START presses are in (origin's "opponent is ready"). */
        void onOpponentStart();

        void onRoomCreated(String code);

        void onHistory(OnlineMessages.HistoryEntry[] entries);

        void onTop(OnlineMessages.TopEntry[] entries);

        void onOpponentDisconnected();

        void onOpponentReconnected();

        void onError(int code, String message);
    }

    /** Lightweight hook used by the in-game HUD (single consumer). */
    public interface ScoreListener {
        void onScoreChanged(int myScore, int opponentScore);
    }

    private static OnlineSession instance;

    private Context appContext;
    private OnlineClient client;
    private final Handler main = new Handler(Looper.getMainLooper());
    private MatchListener listener;
    private ScoreListener scoreListener;

    private volatile int state = STATE_DISCONNECTED;
    private volatile boolean reconnectBlocked;
    private boolean v2Connection;
    private boolean authRefreshAttempted;
    private boolean queueRequestPending;
    private long connectionEpoch;
    private long roomCommandEpoch;
    private volatile AuthOk lastProfile;
    /** Set by match_end, gates the resign retry loop. */
    private volatile boolean matchEndSeen;
    /** Queued when the socket died mid-matchmaking; rejoins after auth. */
    private volatile boolean pendingQueueRejoin;
    private int resignRetries;
    /** Notified on the main thread whenever a fresh profile arrives. */
    private volatile Runnable profileCallback;
    private final RoundClock roundClock = new RoundClock();
    private RoundStart lastRoundStart;
    /**
     * True while a friend-room pairing is pending (host is waiting with a
     * code, or a join code has been sent). Participants sit in STATE_IDLE —
     * never STATE_QUEUE — so match_found must be accepted for them too.
     */
    private volatile boolean friendRoomPending;
    /**
     * True while an explicit practice (play-vs-bot) request is in flight —
     * same shape as friendRoomPending: the pairing leaves immediately, the
     * client never enters STATE_QUEUE, so match_found must be admitted.
     */
    private volatile boolean practicePending;
    /** Fired when a room command gets no server answer in time (zombie socket). */
    private Runnable pendingRoomAck;
    /** Live economy values from the last auth_ok (server runtime config). */
    private volatile int serverEntryFee = LocalDataManager.ONLINE_ENTRY_FEE;
    private volatile int serverWinCoins = LocalDataManager.ONLINE_WIN_COINS;
    private volatile int serverDrawCoins = LocalDataManager.ONLINE_DRAW_COINS;
    /** Practice win streak from the last auth_ok / match_end. */
    private volatile int practiceStreak;
    private String matchId;
    private OnlineMessages.OpponentInfo opponent;
    private boolean friendMode;
    /** True while the running match is an explicit practice duel (vs bot). */
    private volatile boolean practiceMode;
    private final RoundIntermission intermission = new RoundIntermission();
    private OnlineMessages.RoundDesc[] rounds;
    private volatile int currentRound = -1;
    private int myScore;
    private int myCompletedScore;
    private int oppScore;
    private boolean roundReported;
    /** Exact entry fee mirrored locally for the accepted non-practice match. */
    private int localEntryFeeCharged;
    private final MatchAcceptance matchAcceptance = new MatchAcceptance();
    /** Match id whose resign retry chain is currently active. */
    private String resigningMatchId;
    /** Local fallback for a lost match_cancelled frame during the START gate. */
    private Runnable acceptFallback;
    private long acceptDeadlineElapsed;
    private long disconnectedAt;
    private int reconnectAttempt;
    private final Runnable reconnectRunnable = new Runnable() {
        @Override
        public void run() {
            // The reconnect window schedules us; anything left alive when the
            // window expires stops being rescheduled, so reconnect whenever
            // we are still down.
            if (state == STATE_DISCONNECTED) {
                connect();
            }
        }
    };

    private OnlineSession() {
    }

    public static synchronized OnlineSession get() {
        if (instance == null) {
            instance = new OnlineSession();
        }
        return instance;
    }

    // ---------------------------------------------------------- lifecycle

    public void init(Context context) {
        this.appContext = context.getApplicationContext();
        PartySession.get().init(context);
    }

    public int getState() {
        return state;
    }

    public boolean canChangeAccount() {
        return !PartySession.get().occupied() && matchId == null && state != STATE_MATCH && state != STATE_QUEUE
                && !pendingQueueRejoin && !queueRequestPending && !friendRoomPending
                && !practicePending && pendingRoomAck == null;
    }

    public int getOnlineCoins() {
        AuthOk profile=lastProfile;
        return profile == null ? 0 : Math.max(0,profile.balance);
    }

    public boolean canAutoConnect() {
        return !reconnectBlocked && (appContext == null || !AccountManager.get(appContext).isBusy());
    }

    public boolean requiresAccountLogin() {
        return reconnectBlocked || (appContext != null && AccountManager.get(appContext).needsLogin());
    }

    public void resetForAccountChange() {
        PartySession.get().reset();
        disconnect();
        reconnectBlocked=false;
        lastProfile=null;
        practiceStreak=0;
        authRefreshAttempted=false;
        v2Connection=false;
        Runnable callback=profileCallback;
        if (callback != null) callback.run();
    }

    public boolean isInMatch() {
        return this.matchId != null;
    }

    public void setMatchListener(MatchListener l) {
        this.listener = l;
    }

    /** Currently registered match listener (null-safe lifecycle checks). */
    public MatchListener getMatchListener() {
        return listener;
    }

    public void setScoreListener(ScoreListener l) {
        this.scoreListener = l;
    }

    public String getMatchId() {
        return matchId;
    }

    /** Last authenticated profile snapshot for lobby display (null if never). */
    public AuthOk getLastProfile() {
        return lastProfile;
    }

    /** Refresh only the existing matching socket snapshot; never invent REST economy/season data. */
    public void profileUpdated(AccountManager.UserProfile user) {
        AuthOk current=lastProfile;
        if(current!=null && current.userId==user.userId) {
            current.nickname=user.nickname; current.avatarValue=user.avatarValue;
            current.accountType=user.accountType;
            Runnable callback=profileCallback; if(callback!=null) callback.run();
            MatchListener target=listener; if(target!=null) target.onAuthOk(current);
        }
    }

    /**
     * Authoritative round duration for the running round: the server
     * configures round length at runtime (admin API) and pushes it with
     * round_start, so both sides always agree. Falls back to the debug pref
     * / production defaults when no server value is known.
     */
    public long roundDurationMs(Context context, long defaultMs) {
        if (context instanceof android.app.Activity && PartySession.isParty(((android.app.Activity) context).getIntent())) {
            return Math.max(1L, PartySession.get().remaining());
        }
        if (roundClock.hasStarted()) {
            return Math.max(1L, roundRemainingMs());
        }
        if (roundClock.durationMs() > 0L) {
            return roundClock.durationMs();
        }
        return OnlineDebug.roundMs(context, defaultMs);
    }

    /**
     * Estimated remaining time of the running round: the server pushes the
     * round duration with round_start and expires the round on the same
     * clock, so duration minus local elapsed time approximates what the
     * opponent may still be playing. 0 when no round is running.
     */
    public long roundRemainingMs() {
        return roundClock.remainingMs(SystemClock.elapsedRealtime());
    }

    public RoundEnd getLastRoundEnd() {
        return intermission.getRoundEnd();
    }

    public long intermissionRemainingMs() {
        return intermission.remainingMs(SystemClock.elapsedRealtime());
    }

    public boolean hasIntermissionCountdown() {
        return intermission.hasCountdown();
    }

    public int getServerEntryFee() {
        return serverEntryFee;
    }

    public int getServerWinCoins() {
        return serverWinCoins;
    }

    public int getServerDrawCoins() {
        return serverDrawCoins;
    }

    /** Consecutive practice (vs-bot) wins, from auth_ok / match_end. */
    public int getPracticeStreak() {
        return practiceStreak;
    }

    /** Server's live difficulty tier table (null until the first auth_ok). */
    public OnlineMessages.PracticeTier[] getPracticeTiers() {
        AuthOk profile = this.lastProfile;
        return profile != null ? profile.practiceTiers : null;
    }

    public void setProfileCallback(Runnable callback) {
        this.profileCallback = callback;
    }

    public void clearProfileCallback(Runnable callback) {
        if (this.profileCallback == callback) {
            this.profileCallback = null;
        }
    }

    /** True while the running match is a friend-room duel. */
    public boolean isFriendMode() {
        return this.friendMode;
    }

    /** True while the running match is an explicit practice duel (vs bot). */
    public boolean isPracticeMode() {
        return this.practiceMode;
    }

    public OnlineMessages.OpponentInfo getOpponent() {
        return opponent;
    }

    public OnlineMessages.RoundDesc[] getRounds() {
        return rounds;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public int getMyScore() {
        return myScore;
    }

    public int getOpponentScore() {
        return oppScore;
    }

    // ---------------------------------------------------------- connection

    public synchronized void connect() {
        diagnostic("session_connect", "epoch", connectionEpoch+1);
        if (appContext == null) {
            throw new IllegalStateException("OnlineSession.init() not called");
        }
        if (!canAutoConnect()) return;
        final String endpoint;
        try { endpoint=OnlineConfig.getEndpoint(appContext); }
        catch (RuntimeException e) { stopAuthorization(e.getMessage()); return; }
        final long attempt=++connectionEpoch;
        main.removeCallbacks(reconnectRunnable);
        if (client != null) {
            client.close();
            client = null;
        }
        setState(state == STATE_MATCH ? STATE_MATCH : STATE_CONNECTING);
        client = new OnlineClient(new OnlineClient.Listener() {
            @Override
            public void onConnected() {
                if (attempt == connectionEpoch) authorize();
            }

            @Override
            public void onDisconnected(int code, String reason) {
                if (attempt == connectionEpoch) handleDisconnected(code, reason);
            }

            @Override
            public void onMessage(String json) {
                if (attempt == connectionEpoch) handleMessage(json);
                else diagnostic("frame_dropped", "reason", "stale_epoch", "epoch", attempt);
            }
        });
        client.connect(endpoint);
    }

    public synchronized void disconnect() {
        diagnostic("session_disconnect", "reason", "framework");
        ++connectionEpoch;
        ++roomCommandEpoch;
        pendingQueueRejoin=false;
        queueRequestPending=false;
        disconnectedAt=0L;
        cancelQueuedWatchdog();
        clearMatchSnapshot("framework");
        matchId = null;
        currentRound = -1;
        roundClock.reset(0L);
        lastRoundStart = null;
        friendRoomPending = false;
        practicePending = false;
        cancelAcceptFallback();
        cancelRoomAck();
        main.removeCallbacks(reconnectRunnable);
        if (client != null) {
            client.close();
            client = null;
        }
        setState(STATE_DISCONNECTED);
    }

    private void authorize() {
        setState(STATE_AUTHORIZING);
        try {
            AccountManager manager=AccountManager.get(appContext);
            if (OnlineConfig.accountsAllowed(appContext) || manager.usesV2()) {
                v2Connection=true;
                authorizeAccount(false,false);
                return;
            }
            v2Connection=false;
            String token=OnlineIdentity.getToken(appContext);
            if (token != null && !token.isEmpty()) sendAuth(token);
            else {
                OnlineMessages.Register register=new OnlineMessages.Register();
                register.uuid=OnlineIdentity.getOrCreateUuid(appContext);
                // New guests use the server's safe generator. Never publish a device-only draft.
                register.nickname=null;
                register.balance=0; // Offline coins are never uploaded to the online wallet.
                send(OnlineMessages.T_REGISTER,register);
            }
        } catch (RuntimeException e) { stopAuthorization(e.getMessage()); }
    }

    private void sendAuth(String token) {
        OnlineMessages.Auth auth=new OnlineMessages.Auth(); auth.token=token;
        send(OnlineMessages.T_AUTH,auth);
    }

    private void authorizeAccount(boolean refresh, boolean reconnect) {
        final long attempt=connectionEpoch;
        AccountManager.get(appContext).ensureAccess(refresh,new AccountManager.Callback<String>() {
            public void onSuccess(String token) {
                if (attempt != connectionEpoch || reconnectBlocked) return;
                if (reconnect) connect(); else sendAuth(token);
            }
            public void onError(String message) {
                if (attempt == connectionEpoch) stopAuthorization(message);
            }
        });
    }

    private void stopAuthorization(String message) {
        reconnectBlocked=true;
        PartySession.get().reset();
        disconnect();
        notifyError(401,message == null ? "会话失效，请打开账户中心重新登录" : message);
    }

    private void handleDisconnected(int code, String reason) {
        if ("session_replaced".equals(reason)) {
            stopAuthorization("当前在线连接已被另一设备替换，请明确重新登录后再连接");
            return;
        }
        if (reconnectBlocked) return;
        OnlineClient old=client;
        client=null;
        ++connectionEpoch;
        if (old != null) old.close();
        boolean inMatchFlow = matchId != null || state == STATE_QUEUE
                || friendRoomPending || practicePending;
        // Every drop enters the same reconnect window (PRD 4.6). An idle
        // lobby socket used to stay dead until the user tapped something —
        // the "lobby nickname empty until restart" bug — because only
        // in-match and queued states reconnected.
        if (disconnectedAt == 0L) {
            disconnectedAt = System.currentTimeMillis();
            reconnectAttempt = 0;
            if (state == STATE_QUEUE) {
                // Rejoin the queue after the fresh socket authorizes.
                this.pendingQueueRejoin = true;
            }
            if (inMatchFlow) {
                notifyOpponentDisconnected();
            }
        }
        long elapsed = System.currentTimeMillis() - disconnectedAt;
        if (elapsed < RECONNECT_WINDOW_MS) {
            long delay = Math.min(RECONNECT_BASE_MS << Math.min(reconnectAttempt, 4), RECONNECT_MAX_MS);
            reconnectAttempt++;
            diagnostic("session_reconnect", "attempt", reconnectAttempt, "delayMs", delay);
            setState(STATE_DISCONNECTED);
            main.postDelayed(reconnectRunnable, delay);
            return;
        }
        diagnostic("reconnect_exhausted", "reason", "reconnect_window");
        // Window expired: an accepted match that never started was refunded
        // by the server; a running match was forfeited. Mirror only the former.
        if (this.matchId != null && this.currentRound < 0) {
            refundEntryFeeIfNeeded();
        }
        clearMatchSnapshot("reconnect_window");
        disconnectedAt = 0L;
        setState(STATE_DISCONNECTED);
    }

    // ------------------------------------------------------- matchmaking

    /** Watchdog: fired when the match_join got no match_queued receipt. */
    private Runnable queuedWatchdog;

    public void startMatchmaking() {
        if (PartySession.get().occupied()) return;
        if (!canAutoConnect() || AccountManager.get(appContext).isBusy()) return;
        if (this.appContext != null
                && !com.cube.memorygames.security.IntegritySentinel.isIntact(this.appContext)) {
            // Repackaged build (hardening design §5.1): ranked queue stays
            // inert — no state change, no error, nothing observable.
            return;
        }
        // Stack trace kept deliberately: an intermittent phantom match_join
        // (queue entry appearing around match-exit moments) is still open;
        // the next occurrence will identify the call site. Debug tag only.
        android.util.Log.d("OnlinePK", "startMatchmaking from state=" + state,
                new IllegalStateException("trace"));
        if (state == STATE_IDLE || state == STATE_QUEUE) {
            if (state == STATE_QUEUE) {
                return;
            }
            queueRequestPending=false;
            setState(STATE_QUEUE);
            send(OnlineMessages.T_MATCH_JOIN, new Object());
            armQueuedWatchdog();
        } else if (state == STATE_DISCONNECTED || state == STATE_CONNECTING || state == STATE_AUTHORIZING) {
            queueRequestPending=true;
            pendingQueueRejoin=true;
            if (state == STATE_DISCONNECTED) connect();
        }
    }

    /**
     * A zombie TCP connection passes isConnected() and swallows the join
     * silently (pong traffic fine, sends buffered) — the search board then
     * sits there forever because the server never sees the queue entry. The
     * server answers every join with an immediate match_queued receipt; if
     * it doesn't land in 5s, cut the socket: the P3 reconnect loop rebuilds
     * the connection and auth_ok re-joins the queue automatically.
     */
    private void armQueuedWatchdog() {
        cancelQueuedWatchdog();
        this.queuedWatchdog = new Runnable() {
            @Override
            public void run() {
                OnlineSession.this.queuedWatchdog = null;
                if (OnlineSession.this.state != STATE_QUEUE || OnlineSession.this.matchId != null) {
                    return;
                }
                diagnostic("queue_timeout", "reason", "queue_ack");
                android.util.Log.d("OnlinePK", "join unacknowledged — zombie socket, reconnecting");
                OnlineSession.this.pendingQueueRejoin = true;
                OnlineClient dead = OnlineSession.this.client;
                OnlineSession.this.client = null;
                if (dead != null) {
                    dead.close();
                }
                handleDisconnected(-1, "join unacknowledged");
            }
        };
        this.main.postDelayed(this.queuedWatchdog, 5000L);
    }

    private void cancelQueuedWatchdog() {
        if (this.queuedWatchdog != null) {
            this.main.removeCallbacks(this.queuedWatchdog);
            this.queuedWatchdog = null;
        }
    }

    /** Friend rooms (PRD F-12). */
    public void createFriendRoom() {
        if (!canChangeAccount() || !canAutoConnect() || AccountManager.get(appContext).isBusy()) return;
        friendRoomPending=true;
        sendRoomCommand(OnlineMessages.T_ROOM_CREATE, new Object());
    }

    /**
     * Explicit practice duel (docs/bot-battle-prd.md): the server pairs us
     * with a difficulty-tier bot instantly; match_found arrives with
     * mode=PRACTICE on the same socket.
     */
    public void sendBotMatchRequest(String difficulty) {
        if (!canChangeAccount() || !canAutoConnect() || AccountManager.get(appContext).isBusy()) return;
        OnlineMessages.BotMatchRequest request = new OnlineMessages.BotMatchRequest();
        request.difficulty = difficulty;
        practicePending = true;
        sendRoomCommand(OnlineMessages.T_BOT_MATCH_REQUEST, request);
    }

    public void joinFriendRoom(String code) {
        if (!canChangeAccount() || !canAutoConnect() || AccountManager.get(appContext).isBusy()) return;
        OnlineMessages.RoomJoin join = new OnlineMessages.RoomJoin();
        join.code = code;
        friendRoomPending = true;
        sendRoomCommand(OnlineMessages.T_ROOM_JOIN, join);
    }

    public void cancelFriendRoom() {
        friendRoomPending = false;
        sendRoomCommand(OnlineMessages.T_ROOM_CANCEL, new Object());
    }

    /**
     * Room commands come from the lobby, where the socket may have gone
     * stale after an earlier match. A zombie TCP connection can pass
     * isConnected() while send() silently buffers (observed on device
     * 2026-10-04: creates/joins vanished without any feedback), so every
     * acked command also arms a response timeout that surfaces an error.
     */
    private void sendRoomCommand(final String type, final Object data) {
        cancelRoomAck();
        final long command=++roomCommandEpoch;
        OnlineClient c = this.client;
        if (c != null && c.isConnected() && state == STATE_IDLE) {
            send(type, data);
            scheduleRoomAck(type);
            return;
        }
        if (state == STATE_DISCONNECTED) connect();
        final long attempt=connectionEpoch;
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (attempt != connectionEpoch || command != roomCommandEpoch || reconnectBlocked) return;
                OnlineClient retry = OnlineSession.this.client;
                if (retry != null && retry.isConnected() && state == STATE_IDLE) {
                    send(type, data);
                    scheduleRoomAck(type);
                } else {
                    friendRoomPending=false;
                    practicePending=false;
                    notifyError(-1, "connection lost");
                }
            }
        }, 2500L);
    }

    /** room_cancel is fire-and-forget; the server never acks it. */
    private void scheduleRoomAck(String type) {
        if (OnlineMessages.T_ROOM_CANCEL.equals(type)) {
            return;
        }
        cancelRoomAck();
        this.pendingRoomAck = new Runnable() {
            @Override
            public void run() {
                OnlineSession.this.pendingRoomAck = null;
                if (OnlineSession.this.matchId == null
                        && OnlineSession.this.state != STATE_MATCH
                        && OnlineSession.this.state != STATE_QUEUE) {
                    friendRoomPending=false;
                    practicePending=false;
                    diagnostic("room_timeout", "reason", "room_ack");
                    notifyError(-1, "no response");
                }
            }
        };
        main.postDelayed(this.pendingRoomAck, 3000L);
    }

    private void cancelRoomAck() {
        if (this.pendingRoomAck != null) {
            main.removeCallbacks(this.pendingRoomAck);
            this.pendingRoomAck = null;
        }
    }

    /** Match history (PRD F-11); response arrives via MatchListener.onHistory. */
    public void requestHistory() {
        send(OnlineMessages.T_HISTORY_REQUEST, new Object());
    }

    public boolean hasRequestedMatchStart() {
        return matchId != null && matchAcceptance.isStartRequested();
    }

    /** Origin's manual START: releases round 1 once both seats pressed. */
    public void sendMatchStart() {
        if (this.matchId == null || this.resigningMatchId != null) return;
        this.matchAcceptance.requestStart();
        sendMatchStartRequest();
    }

    private boolean preparesPracticeRounds() {
        return practiceMode && opponent != null && opponent.isBot;
    }

    private void sendMatchStartRequest() {
        send(OnlineMessages.T_MATCH_START,
                preparesPracticeRounds() ? new OnlineMessages.MatchStart() : new Object());
    }

    public RoundStart getRoundStartForRestore() {
        if (lastRoundStart == null) return null;
        RoundStart snapshot = new RoundStart();
        snapshot.matchId = lastRoundStart.matchId;
        snapshot.round = lastRoundStart.round;
        snapshot.gameId = lastRoundStart.gameId;
        snapshot.durationMs = lastRoundStart.durationMs;
        snapshot.serverNow = lastRoundStart.serverNow;
        snapshot.preparing = roundClock.isPreparing();
        snapshot.resuming = roundClock.hasStarted();
        return snapshot;
    }

    /** Callback is scoped to the owning match/round, and may safely repeat after reconnect. */
    public void sendRoundReady(RoundStart preparation) {
        if (!preparesPracticeRounds() || !roundClock.isPreparing() || matchEndSeen
                || resigningMatchId != null
                || !RoundIntermission.canLaunch(preparation, matchId, currentRound, getLastRoundEnd())
                || !preparation.preparing) return;
        OnlineMessages.RoundReady ready = new OnlineMessages.RoundReady();
        ready.matchId = matchId;
        ready.round = currentRound;
        send(OnlineMessages.T_ROUND_READY, ready);
    }

    /** TOP Online leaderboard (top 20 by ELO). */
    public void requestTop() {
        send(OnlineMessages.T_TOP_REQUEST, new Object());
    }

    public void cancelMatchmaking() {
        pendingQueueRejoin=false;
        queueRequestPending=false;
        cancelQueuedWatchdog();
        if (state == STATE_QUEUE) {
            send(OnlineMessages.T_MATCH_CANCEL, new Object());
            setState(STATE_IDLE);
        }
    }

    // -------------------------------------------------------- game bridge

    /** Every party callback keeps the originating Activity's immutable identity. */
    public void reportLevel(android.app.Activity owner, int level, int score, boolean success) {
        if (PartySession.isParty(owner.getIntent())) PartySession.get().level(owner.getIntent(), level, success);
        else if (success) reportLevelWin(level, score);
    }

    public void finishRound(android.app.Activity owner, int score) {
        if (PartySession.isParty(owner.getIntent())) PartySession.get().complete(owner.getIntent());
        else onRoundFinished(score);
    }

    public void resign(android.app.Activity owner) {
        if (PartySession.isParty(owner.getIntent())) PartySession.get().leave(owner.getIntent());
        else resign();
    }

    boolean isAuthenticatedSocket() {
        return client != null && client.isConnected() && (state == STATE_IDLE || state == STATE_MATCH);
    }

    void sendParty(String type, Object data, String rid) {
        com.google.gson.JsonObject root = new com.google.gson.JsonParser()
                .parse(OnlineMessages.envelope(type, data)).getAsJsonObject();
        root.addProperty("rid", rid);
        if (client != null) client.send(root.toString());
    }

    /** Called from Game1MemoryGridActivity.updateOnlineScore() in online mode. */
    public void reportLevelWin(int level, int totalScore) {
        android.util.Log.d("OnlinePK", "reportLevelWin round=" + currentRound
                + " level=" + level + " score=" + totalScore
                + " matchId=" + (matchId == null ? "null" : matchId));
        if (matchId == null || currentRound < 0 || roundReported || roundClock.isPreparing()) {
            return;
        }
        // Live HUD value: completed rounds total plus the running round score.
        this.myScore = this.myCompletedScore + totalScore;
        notifyScoreChanged();
        LevelWin msg = new LevelWin();
        msg.round = currentRound;
        msg.level = level;
        msg.score = totalScore;
        send(OnlineMessages.T_LEVEL_WIN, msg);
    }

    /**
     * Called from Game1MemoryGridActivity.showOnlineReplayDialog() when the
     * game's own round timer has expired; reports the final round score.
     */
    public void onRoundFinished(int score) {
        diagnostic("round_complete", "score", score);
        if (matchId == null || currentRound < 0 || roundReported || roundClock.isPreparing()) {
            return;
        }
        roundReported = true;
        RoundComplete msg = new RoundComplete();
        msg.round = currentRound;
        msg.score = score;
        send(OnlineMessages.T_ROUND_COMPLETE, msg);
    }

    /** Called when the player confirms leaving an online match (forfeit). */
    public void resign() {
        diagnostic("resign", "reason", "user_resign");
        if (this.matchId != null) {
            cancelAcceptFallback();
            this.resigningMatchId = this.matchId;
            this.resignRetries = RESIGN_RETRY_MAX;
            sendResign(this.resigningMatchId);
        }
    }

    /** Retry until the server confirms seat release or sends a legacy terminal frame. */
    private void sendResign(final String targetMatchId) {
        diagnostic("resign_send", "attempt", RESIGN_RETRY_MAX-resignRetries);
        OnlineClient c = client;
        android.util.Log.d("OnlinePK", "sendResign matchId=" + targetMatchId
                + " connected=" + (c != null && c.isConnected())
                + " retriesLeft=" + resignRetries);
        OnlineMessages.Resign request = new OnlineMessages.Resign();
        request.matchId = targetMatchId;
        send(OnlineMessages.T_RESIGN, request);
        this.main.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (targetMatchId.equals(OnlineSession.this.matchId)
                        && targetMatchId.equals(OnlineSession.this.resigningMatchId)
                        && !OnlineSession.this.matchEndSeen
                        && OnlineSession.this.resignRetries-- > 0) {
                    sendResign(targetMatchId);
                }
            }
        }, RESIGN_RETRY_MS);
    }

    // -------------------------------------------------------- dispatcher

    private void handleMessage(String json) {
        String type = OnlineMessages.typeOf(json);
        if (type == null) {
            diagnostic("frame_dropped", "reason", "bad_type");
            return;
        }
        if ((type.startsWith("party_") || OnlineMessages.T_ERROR.equals(type))
                && PartySession.get().receive(type, json)) return;
        if (DiagnosticSanitizer.businessType(type)) diagnosticFrame(json, type);
        // Match-scoped frames carry the owning matchId; anything addressed to
        // a match we are not in (e.g. an orphan room from a server-side
        // double-queue) must be dropped before it corrupts our state.
        if (isMatchScoped(type) && !ownedByCurrentMatch(json)) {
            diagnostic("frame_dropped", "reason", "wrong_match");
            return;
        }
        if (OnlineMessages.T_SESSION_REPLACED.equals(type)) {
            stopAuthorization("当前在线连接已被另一设备替换，请明确重新登录后再连接");
            return;
        }
        if (OnlineMessages.T_AUTH_OK.equals(type)) {
            AuthOk ok = OnlineMessages.payloadOf(json, AuthOk.class);
            if (ok != null) {
                this.lastProfile = ok;
                // The connection is healthy again; restart the reconnect
                // window clock for any future drop.
                this.disconnectedAt = 0L;
                this.reconnectAttempt = 0;
                this.serverEntryFee = nonNegativeFieldOf(json, "entryFee", this.serverEntryFee);
                this.serverWinCoins = nonNegativeFieldOf(json, "winCoins", this.serverWinCoins);
                this.serverDrawCoins = nonNegativeFieldOf(json, "drawCoins", this.serverDrawCoins);
                this.practiceStreak = ok.practiceStreak;
                Runnable callback = this.profileCallback;
                if (callback != null) {
                    try {
                        callback.run();
                    } catch (RuntimeException ignored) {
                    }
                }
                authRefreshAttempted=false;
                try {
                    if (!v2Connection) {
                        OnlineIdentity.saveToken(appContext,tokenOf(json),ok.userId);
                        OnlineIdentity.setNickname(appContext,ok.nickname);
                    } // V2 nicknames belong exclusively to the encrypted, per-account profile.
                } catch (RuntimeException e) { stopAuthorization(e.getMessage()); return; }
                boolean resumeMatch = this.matchId != null;
                setState(resumeMatch ? STATE_MATCH : STATE_IDLE);
                PartySession.get().authenticated();
                if (resumeMatch && this.resigningMatchId != null) {
                    this.resignRetries = RESIGN_RETRY_MAX;
                    sendResign(this.resigningMatchId);
                } else if (resumeMatch && this.currentRound < 0) {
                    if (this.matchAcceptance.isStartRequested()) {
                        sendMatchStartRequest();
                    }
                    long remaining = this.acceptDeadlineElapsed > 0L
                            ? this.acceptDeadlineElapsed - SystemClock.elapsedRealtime() : 0L;
                    armAcceptFallback(Math.max(POST_AUTH_MATCH_GRACE_MS, remaining));
                }
                if (listener != null) {
                    try {
                        listener.onAuthOk(ok);
                    } catch (RuntimeException ignored) {
                    }
                }
                if (this.pendingQueueRejoin && !resumeMatch) {
                    this.pendingQueueRejoin = false;
                    startMatchmaking();
                }
            }
            return;
        }
        if (OnlineMessages.T_ERROR.equals(type)) {
            OnlineMessages.ErrorMsg err = OnlineMessages.payloadOf(json, OnlineMessages.ErrorMsg.class);
            int code = err != null ? err.code : 0;
            String message = err != null && err.message != null ? err.message : "unknown";
            if (code == 401) {
                if (v2Connection && !authRefreshAttempted) {
                    authRefreshAttempted=true;
                    if (state == STATE_QUEUE) pendingQueueRejoin=true;
                    authorizeAccount(true,true);
                } else stopAuthorization(message);
                return;
            }
            // A rejected room command (404/409) ends the pending pairing;
            // leaving the flag set would wrongly admit a stale match_found.
            this.friendRoomPending = false;
            this.practicePending = false;
            cancelRoomAck();
            cancelQueuedWatchdog();
            pendingQueueRejoin=false;
            queueRequestPending=false;
            if (state == STATE_QUEUE) setState(STATE_IDLE);
            notifyError(code, message);
            return;
        }
        if (OnlineMessages.T_MATCH_QUEUED.equals(type)) {
            // The join landed; the search can now legitimately wait for a
            // human opponent or the bot fallback.
            cancelQueuedWatchdog();
            return;
        }
        if (OnlineMessages.T_MATCH_FOUND.equals(type)) {
            // Accept a pairing only while we are actually searching, a
            // friend-room pairing is pending, or a practice request is in
            // flight; a frame arriving in any other state belongs to a stale
            // server-side room and would hijack the session (seen in the
            // 2026-10-04 test where a leftover queue entry bot-matched an
            // idle lobby client).
            if (this.state != STATE_QUEUE && this.matchId == null
                    && !this.friendRoomPending && !this.practicePending) {
                diagnostic("frame_dropped", "reason", "unexpected_match");
                return;
            }
            android.util.Log.d("OnlinePK", "match_found handling, state=" + state);
            MatchFound found = OnlineMessages.payloadOf(json, MatchFound.class);
            if (found != null) {
                this.friendRoomPending = false;
                this.practicePending = false;
                cancelRoomAck();
                this.matchId = found.matchId;
                this.opponent = found.opponent;
                this.friendMode = OnlineSession.MODE_FRIEND.equals(found.mode);
                this.practiceMode = OnlineSession.MODE_PRACTICE.equals(found.mode);
                this.rounds = found.rounds;
                this.roundClock.reset(found.rounds != null && found.rounds.length > 0
                        ? found.rounds[0].durationMs : 0L);
                this.lastRoundStart = null;
                this.currentRound = -1;
                this.intermission.clear();
                this.myScore = 0;
                this.myCompletedScore = 0;
                this.oppScore = 0;
                this.roundReported = false;
                this.matchEndSeen = false;
                this.resigningMatchId = null;
                this.localEntryFeeCharged = 0;
                this.matchAcceptance.clear();
                this.disconnectedAt = 0L;
                if (!this.practiceMode) {
                    chargeEntryFee();
                }
                this.acceptDeadlineElapsed = SystemClock.elapsedRealtime()
                        + MATCH_ACCEPT_FALLBACK_MS;
                armAcceptFallback(MATCH_ACCEPT_FALLBACK_MS);
                setState(STATE_MATCH);
                if (listener != null) {
                    try {
                        listener.onMatchFound(found);
                    } catch (RuntimeException ignored) {
                    }
                }
            }
            return;
        }
        if (OnlineMessages.T_RESIGN_ACK.equals(type)) {
            OnlineMessages.ResignAck ack = OnlineMessages.payloadOf(json, OnlineMessages.ResignAck.class);
            if (ack != null && ack.confirms(this.matchId, this.resigningMatchId)) {
                clearMatchSnapshot("resign_ack");
                this.pendingQueueRejoin = false;
                this.queueRequestPending = false;
                this.friendRoomPending = false;
                this.practicePending = false;
                cancelRoomAck();
                cancelQueuedWatchdog();
                setState(STATE_IDLE);
            }
            return;
        }
        if (OnlineMessages.T_MATCH_CANCELLED.equals(type)) {
            handleMatchCancelled("match_cancelled");
            return;
        }
        if (OnlineMessages.T_ROUND_START.equals(type)) {
            RoundStart start = OnlineMessages.payloadOf(json, RoundStart.class);
            if (start != null) {
                // Bind legacy untagged frames now so delayed launches retain their owning match.
                if (start.matchId == null) {
                    start.matchId = this.matchId;
                }
                if (!RoundClock.belongsToMatch(start, this.matchId)) {
                    diagnostic("frame_dropped", "reason", "wrong_match");
                    return;
                }
                RoundEnd ended = intermission.getRoundEnd();
                if (start.round < currentRound || (ended != null && start.round <= ended.round)) {
                    diagnostic("frame_dropped", "reason", "stale_round_start");
                    return;
                }
                if (this.matchId == null || this.resigningMatchId != null) return;
                // Only explicit bot practice participates; ranked/friend retain their old launch flow.
                start.preparing = preparesPracticeRounds() && start.preparing;
                if (!roundClock.accept(start, preparesPracticeRounds(), SystemClock.elapsedRealtime())) return;
                intermission.clear();
                cancelAcceptFallback();
                this.currentRound = start.round;
                this.lastRoundStart = start;
                if (!start.resuming) this.roundReported = false;
                if (listener != null) {
                    try {
                        listener.onRoundStart(start);
                    } catch (RuntimeException ignored) {
                    }
                }
            }
            return;
        }
        if (OnlineMessages.T_OPPONENT_SCORE.equals(type)) {
            OpponentScore score = OnlineMessages.payloadOf(json, OpponentScore.class);
            if (score != null) {
                this.oppScore = score.score;
                notifyScoreChanged();
                if (listener != null) {
                    try {
                        listener.onOpponentScore(score);
                    } catch (RuntimeException ignored) {
                    }
                }
            }
            return;
        }
        if (OnlineMessages.T_ROUND_END.equals(type)) {
            RoundEnd end = OnlineMessages.payloadOf(json, RoundEnd.class);
            if (intermission.accept(end, currentRound, SystemClock.elapsedRealtime())) {
                cancelAcceptFallback();
                this.currentRound = end.round;
                this.roundReported = true;
                this.myScore = end.myScore;
                this.oppScore = end.oppScore;
                this.myCompletedScore = end.myScore;
                notifyScoreChanged();
                if (listener != null) {
                    try {
                        listener.onRoundEnd(end);
                    } catch (RuntimeException ignored) {
                    }
                }
            }
            return;
        }
        if (OnlineMessages.T_OPPONENT_START.equals(type)) {
            cancelAcceptFallback();
            if (listener != null) {
                try {
                    listener.onOpponentStart();
                } catch (RuntimeException ignored) {
                }
            }
            return;
        }
        if (OnlineMessages.T_MATCH_END.equals(type)) {
            MatchEnd end = OnlineMessages.payloadOf(json, MatchEnd.class);
            if (end != null) {
                diagnostic("match_end", "score", end.myScore);
                cancelAcceptFallback();
                applySettlement(end);
                this.matchEndSeen = true;
                this.resigningMatchId = null;
                refreshProfileRecord(end);
                this.matchId = null;
                this.friendMode = false;
                this.practiceMode = false;
                this.currentRound = -1;
                this.intermission.clear();
                this.roundClock.reset(0L);
                this.lastRoundStart = null;
                this.localEntryFeeCharged = 0;
                this.matchAcceptance.clear();
                this.disconnectedAt = 0L;
                setState(STATE_IDLE);
                if (listener != null) {
                    try {
                        listener.onMatchEnd(end);
                    } catch (RuntimeException ignored) {
                    }
                }
            }
            return;
        }
        if (OnlineMessages.T_ROOM_CREATED.equals(type)) {
            OnlineMessages.RoomCreated created = OnlineMessages.payloadOf(json, OnlineMessages.RoomCreated.class);
            if (created != null) {
                this.friendRoomPending = true;
                cancelRoomAck();
                if (listener != null) {
                    try {
                        listener.onRoomCreated(created.code);
                    } catch (RuntimeException ignored) {
                    }
                }
            }
            return;
        }
        if (OnlineMessages.T_TOP_RESPONSE.equals(type)) {
            OnlineMessages.TopResponse response =
                    OnlineMessages.payloadOf(json, OnlineMessages.TopResponse.class);
            if (listener != null) {
                try {
                    listener.onTop(response != null ? response.entries
                            : new OnlineMessages.TopEntry[0]);
                } catch (RuntimeException ignored) {
                }
            }
            return;
        }
        if (OnlineMessages.T_HISTORY_RESPONSE.equals(type)) {
            OnlineMessages.HistoryResponse response =
                    OnlineMessages.payloadOf(json, OnlineMessages.HistoryResponse.class);
            if (listener != null) {
                try {
                    listener.onHistory(response != null ? response.matches : new OnlineMessages.HistoryEntry[0]);
                } catch (RuntimeException ignored) {
                }
            }
            return;
        }
        if (OnlineMessages.T_OPPONENT_DISCONNECTED.equals(type)) {
            notifyOpponentDisconnected();
            return;
        }
        if (OnlineMessages.T_OPPONENT_RECONNECTED.equals(type)) {
            if (listener != null) {
                try {
                    listener.onOpponentReconnected();
                } catch (RuntimeException ignored) {
                }
            }
            return;
        }
    }

    private void diagnostic(String event, Object... extra) {
        try {
            String game=null;
            if (rounds!=null && currentRound>=1 && currentRound<=rounds.length && rounds[currentRound-1]!=null) game=rounds[currentRound-1].gameId;
            Object[] fields=new Object[10+extra.length];
            fields[0]="matchId";fields[1]=matchId;fields[2]="round";fields[3]=currentRound;
            fields[4]="gameId";fields[5]=game;fields[6]="state";fields[7]=state;
            fields[8]="mode";fields[9]=practiceMode ? MODE_PRACTICE : friendMode ? MODE_FRIEND : MODE_RANKED;
            System.arraycopy(extra,0,fields,10,extra.length);DiagnosticLog.event(event,fields);
        } catch(RuntimeException ignored) { }
    }

    private void diagnosticFrame(String json,String type) {
        try {
            com.google.gson.JsonObject root=new com.google.gson.JsonParser().parse(json).getAsJsonObject();
            com.google.gson.JsonObject d=root.has("d") && root.get("d").isJsonObject() ? root.get("d").getAsJsonObject() : new com.google.gson.JsonObject();
            String match=d.has("matchId") && d.get("matchId").isJsonPrimitive() ? d.get("matchId").getAsString() : matchId;
            String game=d.has("gameId") && d.get("gameId").isJsonPrimitive() ? d.get("gameId").getAsString() : null;
            int round=d.has("round") && d.get("round").isJsonPrimitive() ? d.get("round").getAsInt() : currentRound;
            DiagnosticLog.event("frame_received","reason",type,"matchId",match,"round",round,"gameId",game,"state",state);
        } catch(RuntimeException ignored) { }
    }

    private static boolean isMatchScoped(String type) {
        return OnlineMessages.T_ROUND_START.equals(type)
                || OnlineMessages.T_OPPONENT_SCORE.equals(type)
                || OnlineMessages.T_ROUND_END.equals(type)
                || OnlineMessages.T_MATCH_END.equals(type)
                || OnlineMessages.T_MATCH_CANCELLED.equals(type)
                || OnlineMessages.T_RESIGN_ACK.equals(type)
                || OnlineMessages.T_OPPONENT_START.equals(type);
    }

    private boolean ownedByCurrentMatch(String json) {
        return OnlineMessages.belongsToMatch(json, this.matchId);
    }

    private void setState(int newState) {
        if (state != newState) {
            diagnostic("session_state", "from", state, "to", newState);
            state = newState;
            PartySession.get().connectionChanged();
            if (listener != null) {
                try {
                    listener.onStateChanged(newState);
                } catch (RuntimeException ignored) {
                }
            }
        }
    }

    private void notifyOpponentDisconnected() {
        if (listener != null) {
            try {
                listener.onOpponentDisconnected();
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void notifyScoreChanged() {
        ScoreListener scores = this.scoreListener;
        if (scores != null) {
            try {
                scores.onScoreChanged(this.myScore, this.oppScore);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void handleMatchCancelled(String reason) {
        boolean hadMatch = this.matchId != null;
        String mode = this.practiceMode ? MODE_PRACTICE : this.friendMode ? MODE_FRIEND : MODE_RANKED;
        MatchAcceptance.Action action = this.matchAcceptance.cancel(hadMatch, mode,
                this.resigningMatchId != null);
        diagnostic("accept_gate".equals(reason) ? "accept_timeout" : "match_cancelled", "reason", reason);
        if (hadMatch) refundEntryFeeIfNeeded();
        clearMatchSnapshot(reason);
        this.pendingQueueRejoin = false;
        this.queueRequestPending = false;
        this.friendRoomPending = false;
        this.practicePending = false;
        cancelRoomAck();
        cancelQueuedWatchdog();
        setState(STATE_IDLE);
        MatchListener target = this.listener;
        if (target != null) {
            try {
                target.onMatchCancelled(action, mode);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void armAcceptFallback(long delayMs) {
        if (this.acceptFallback != null) {
            this.main.removeCallbacks(this.acceptFallback);
            this.acceptFallback = null;
        }
        final String targetMatchId = this.matchId;
        if (targetMatchId == null) {
            return;
        }
        this.acceptFallback = new Runnable() {
            @Override
            public void run() {
                OnlineSession.this.acceptFallback = null;
                if (!targetMatchId.equals(OnlineSession.this.matchId)
                        || OnlineSession.this.currentRound >= 0) {
                    return;
                }
                OnlineClient active = OnlineSession.this.client;
                if (active == null || !active.isConnected()) {
                    return;
                }
                handleMatchCancelled("accept_gate");
            }
        };
        this.main.postDelayed(this.acceptFallback, delayMs);
    }

    private void cancelAcceptFallback() {
        if (this.acceptFallback != null) {
            this.main.removeCallbacks(this.acceptFallback);
            this.acceptFallback = null;
        }
        this.acceptDeadlineElapsed = 0L;
    }

    private void clearMatchSnapshot(String reason) {
        diagnostic("snapshot_clear", "reason", reason);
        cancelAcceptFallback();
        this.matchId = null;
        this.opponent = null;
        this.rounds = null;
        this.friendMode = false;
        this.practiceMode = false;
        this.currentRound = -1;
        this.intermission.clear();
        this.roundClock.reset(0L);
        this.lastRoundStart = null;
        this.myScore = 0;
        this.myCompletedScore = 0;
        this.oppScore = 0;
        this.roundReported = false;
        this.localEntryFeeCharged = 0;
        this.matchAcceptance.clear();
        this.resigningMatchId = null;
        this.resignRetries = 0;
    }

    /** MatchEnd carries the fresh season record; fold it into the cached
     * profile so the lobby's stats row reflects the settled match without
     * waiting for the next auth_ok. */
    private void refreshProfileRecord(MatchEnd end) {
        AuthOk profile = this.lastProfile;
        if (profile != null) {
            profile.elo = end.elo;
            profile.balance = end.balance;
            profile.wins = end.myWins;
            profile.losses = end.myLosses;
            profile.draws = end.myDraws;
            OnlineModeStats.applySettlement(profile, end);
            profile.practiceStreak = end.practiceStreak;
        }
        this.practiceStreak = end.practiceStreak;
    }

    private void notifyError(int code, String message) {
        diagnostic("frame_received", "reason", "error", "code", code);
        if (listener != null) {
            try {
                listener.onError(code, message);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void send(String type, Object data) {
        OnlineClient c = client;
        if (c != null) {
            if (!c.send(OnlineMessages.envelope(type, data))) diagnostic("session_send_failed", "reason", DiagnosticSanitizer.businessType(type) ? type : "send");
        } else diagnostic("session_send_failed", "reason", "not_connected");
    }

    /** Entry debit/refund is server-owned: never infer deltas from an old snapshot after reconnect. */
    private void chargeEntryFee() {
        localEntryFeeCharged=0;
    }

    private void refundEntryFeeIfNeeded() {
        localEntryFeeCharged=0;
    }

    private void applySettlement(MatchEnd end) {
        localEntryFeeCharged=0;
        AuthOk profile=lastProfile;
        if (profile != null) profile.balance=Math.max(0,end.balance);
    }

    /** Reads a non-negative numeric payload field while tolerating older servers. */
    private int nonNegativeFieldOf(String json, String field, int fallback) {
        try {
            com.google.gson.JsonObject root =
                    new com.google.gson.JsonParser().parse(json).getAsJsonObject();
            if (root.has("d") && root.get("d").isJsonObject()) {
                com.google.gson.JsonObject d = root.get("d").getAsJsonObject();
                if (d.has(field) && !d.get(field).isJsonNull()) {
                    return Math.max(0, d.get(field).getAsInt());
                }
            }
        } catch (RuntimeException ignored) {
        }
        return fallback;
    }

    /** The register/auth_ok token is echoed by the server inside the raw frame. */
    private String tokenOf(String json) {
        try {
            com.google.gson.JsonObject root =
                    new com.google.gson.JsonParser().parse(json).getAsJsonObject();
            if (root.has("d") && root.get("d").isJsonObject()) {
                com.google.gson.JsonObject d = root.get("d").getAsJsonObject();
                if (d.has("token") && !d.get("token").isJsonNull()) {
                    return d.get("token").getAsString();
                }
            }
        } catch (RuntimeException ignored) {
        }
        return OnlineIdentity.getToken(appContext);
    }
}
