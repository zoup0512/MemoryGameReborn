package com.cube.memorygames.api.online;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.cube.memorygames.MemoryApplicationModel;
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

    private static final long RESIGN_RETRY_MS = 3000L;
    private static final int RESIGN_RETRY_MAX = 6;
    private static final long RECONNECT_BASE_MS = 1000L;
    private static final long RECONNECT_MAX_MS = 15000L;
    private static final long RECONNECT_WINDOW_MS = 90000L;

    /** UI callback invoked on the main thread. */
    public interface MatchListener {
        void onStateChanged(int state);

        void onAuthOk(AuthOk profile);

        void onMatchFound(MatchFound match);

        void onMatchCancelled();

        void onRoundStart(RoundStart round);

        void onOpponentScore(OpponentScore score);

        void onRoundEnd(RoundEnd round);

        void onMatchEnd(MatchEnd end);

        void onRoomCreated(String code);

        void onHistory(OnlineMessages.HistoryEntry[] entries);

        void onTop(OnlineMessages.TopEntry[] entries);

        void onOpponentDisconnected();

        void onOpponentReconnected();

        void onError(int code, String message);
    }

    /** Lightweight hook used by the in-game HUD (single consumer). */
    public interface ScoreListener {
        void onOpponentScore(int score);
    }

    private static OnlineSession instance;

    private Context appContext;
    private OnlineClient client;
    private final Handler main = new Handler(Looper.getMainLooper());
    private MatchListener listener;
    private ScoreListener scoreListener;

    private volatile int state = STATE_DISCONNECTED;
    private volatile AuthOk lastProfile;
    /** Set by match_end, gates the resign retry loop. */
    private volatile boolean matchEndSeen;
    private int resignRetries;
    /** Notified on the main thread whenever a fresh profile arrives. */
    private volatile Runnable profileCallback;
    /** Round duration delivered by the server (round_start/match_found). */
    private volatile long serverRoundDurationMs;
    /** Monotonic receive time of the last round_start (0 = no round). */
    private volatile long roundStartedElapsed;
    /**
     * True while a friend-room pairing is pending (host is waiting with a
     * code, or a join code has been sent). Participants sit in STATE_IDLE —
     * never STATE_QUEUE — so match_found must be accepted for them too.
     */
    private volatile boolean friendRoomPending;
    /** Fired when a room command gets no server answer in time (zombie socket). */
    private Runnable pendingRoomAck;
    /** Live economy values from the last auth_ok (server runtime config). */
    private volatile int serverEntryFee = LocalDataManager.ONLINE_ENTRY_FEE;
    private volatile int serverWinCoins = LocalDataManager.ONLINE_WIN_COINS;
    private volatile int serverDrawCoins = LocalDataManager.ONLINE_DRAW_COINS;
    private String matchId;
    private OnlineMessages.OpponentInfo opponent;
    private OnlineMessages.RoundDesc[] rounds;
    private volatile int currentRound = -1;
    private int myScore;
    private int myCompletedScore;
    private int oppScore;
    private boolean roundReported;
    private long disconnectedAt;
    private int reconnectAttempt;
    private final Runnable reconnectRunnable = new Runnable() {
        @Override
        public void run() {
            if (state == STATE_DISCONNECTED && matchId != null) {
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
    }

    public int getState() {
        return state;
    }

    public boolean isInMatch() {
        return matchId != null && (state == STATE_MATCH || state == STATE_DISCONNECTED);
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

    /**
     * Authoritative round duration for the running round: the server
     * configures round length at runtime (admin API) and pushes it with
     * round_start, so both sides always agree. Falls back to the debug pref
     * / production defaults when no server value is known.
     */
    public long roundDurationMs(Context context, long defaultMs) {
        if (serverRoundDurationMs > 0L) {
            return serverRoundDurationMs;
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
        long duration = serverRoundDurationMs;
        if (duration <= 0L || roundStartedElapsed <= 0L) {
            return 0L;
        }
        return Math.max(0L, duration - (SystemClock.elapsedRealtime() - roundStartedElapsed));
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

    public void setProfileCallback(Runnable callback) {
        this.profileCallback = callback;
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
        if (appContext == null) {
            throw new IllegalStateException("OnlineSession.init() not called");
        }
        if (client != null) {
            client.close();
            client = null;
        }
        setState(state == STATE_MATCH ? STATE_MATCH : STATE_CONNECTING);
        client = new OnlineClient(new OnlineClient.Listener() {
            @Override
            public void onConnected() {
                authorize();
            }

            @Override
            public void onDisconnected(int code, String reason) {
                handleDisconnected(code, reason);
            }

            @Override
            public void onMessage(String json) {
                handleMessage(json);
            }
        });
        client.connect(OnlineConfig.getEndpoint(appContext));
    }

    public synchronized void disconnect() {
        matchId = null;
        currentRound = -1;
        roundStartedElapsed = 0L;
        friendRoomPending = false;
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
        String token = OnlineIdentity.getToken(appContext);
        if (token != null && token.length() > 0) {
            OnlineMessages.Auth auth = new OnlineMessages.Auth();
            auth.token = token;
            send(OnlineMessages.T_AUTH, auth);
        } else {
            OnlineMessages.Register register = new OnlineMessages.Register();
            register.uuid = OnlineIdentity.getOrCreateUuid(appContext);
            register.nickname = OnlineIdentity.getNickname(appContext);
            register.balance = getLocalCoins();
            send(OnlineMessages.T_REGISTER, register);
        }
    }

    private void handleDisconnected(int code, String reason) {
        client = null;
        if (matchId != null) {
            // Reconnect window while a match is running (PRD 4.6).
            if (disconnectedAt == 0L) {
                disconnectedAt = System.currentTimeMillis();
                reconnectAttempt = 0;
                notifyOpponentDisconnected();
            }
            long elapsed = System.currentTimeMillis() - disconnectedAt;
            if (elapsed < RECONNECT_WINDOW_MS) {
                long delay = Math.min(RECONNECT_BASE_MS << Math.min(reconnectAttempt, 4), RECONNECT_MAX_MS);
                reconnectAttempt++;
                setState(STATE_DISCONNECTED);
                main.postDelayed(reconnectRunnable, delay);
                return;
            }
            // Window expired: drop the match locally; server will have
            // forfeited us after its own deadline.
            matchId = null;
            currentRound = -1;
            roundStartedElapsed = 0L;
            disconnectedAt = 0L;
        }
        setState(STATE_DISCONNECTED);
    }

    // ------------------------------------------------------- matchmaking

    public void startMatchmaking() {
        // Stack trace kept deliberately: an intermittent phantom match_join
        // (queue entry appearing around match-exit moments) is still open;
        // the next occurrence will identify the call site. Debug tag only.
        android.util.Log.d("OnlinePK", "startMatchmaking from state=" + state,
                new IllegalStateException("trace"));
        if (state == STATE_IDLE || state == STATE_QUEUE) {
            if (state == STATE_QUEUE) {
                return;
            }
            setState(STATE_QUEUE);
            send(OnlineMessages.T_MATCH_JOIN, new Object());
        } else if (state == STATE_DISCONNECTED || state == STATE_CONNECTING || state == STATE_AUTHORIZING) {
            connect();
            main.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (state == STATE_IDLE) {
                        startMatchmaking();
                    }
                }
            }, 4000L);
        }
    }

    /** Friend rooms (PRD F-12). */
    public void createFriendRoom() {
        sendRoomCommand(OnlineMessages.T_ROOM_CREATE, new Object());
    }

    public void joinFriendRoom(String code) {
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
        OnlineClient c = this.client;
        if (c != null && c.isConnected()) {
            send(type, data);
            scheduleRoomAck(type);
            return;
        }
        connect();
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                OnlineClient retry = OnlineSession.this.client;
                if (retry != null && retry.isConnected()) {
                    send(type, data);
                    scheduleRoomAck(type);
                } else {
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
                if (!OnlineSession.this.friendRoomPending
                        && OnlineSession.this.matchId == null
                        && OnlineSession.this.state != STATE_MATCH) {
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

    /** Origin's manual START: releases round 1 once both seats pressed. */
    public void sendMatchStart() {
        send(OnlineMessages.T_MATCH_START, new Object());
    }

    /** TOP Online leaderboard (top 20 by ELO). */
    public void requestTop() {
        send(OnlineMessages.T_TOP_REQUEST, new Object());
    }

    public void cancelMatchmaking() {
        if (state == STATE_QUEUE) {
            send(OnlineMessages.T_MATCH_CANCEL, new Object());
            setState(STATE_IDLE);
        }
    }

    // -------------------------------------------------------- game bridge

    /** Called from Game1MemoryGridActivity.updateOnlineScore() in online mode. */
    public void reportLevelWin(int level, int totalScore) {
        android.util.Log.d("OnlinePK", "reportLevelWin round=" + currentRound
                + " level=" + level + " score=" + totalScore
                + " matchId=" + (matchId == null ? "null" : matchId));
        if (matchId == null || currentRound < 0) {
            return;
        }
        // Live HUD value: completed rounds total plus the running round score.
        this.myScore = this.myCompletedScore + totalScore;
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
        if (matchId == null || currentRound < 0 || roundReported) {
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
        if (matchId != null) {
            this.resignRetries = RESIGN_RETRY_MAX;
            sendResign();
        }
    }

    /**
     * A resigned match must settle. A half-dead TCP connection swallows the
     * frame without any signal (the 2026-10-04 two-device finding: the
     * server kept the duel running while the player sat on the scoreboard),
     * so the resign is re-sent until match_end confirms the settlement.
     */
    private void sendResign() {
        OnlineClient c = client;
        android.util.Log.d("OnlinePK", "sendResign matchId=" + matchId
                + " connected=" + (c != null && c.isConnected())
                + " retriesLeft=" + resignRetries);
        send(OnlineMessages.T_RESIGN, new Object());
        this.main.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (OnlineSession.this.matchId != null && !OnlineSession.this.matchEndSeen
                        && OnlineSession.this.resignRetries-- > 0) {
                    sendResign();
                }
            }
        }, RESIGN_RETRY_MS);
    }

    // -------------------------------------------------------- dispatcher

    private void handleMessage(String json) {
        String type = OnlineMessages.typeOf(json);
        if (type == null) {
            return;
        }
        // Match-scoped frames carry the owning matchId; anything addressed to
        // a match we are not in (e.g. an orphan room from a server-side
        // double-queue) must be dropped before it corrupts our state.
        if (isMatchScoped(type) && !ownedByCurrentMatch(json)) {
            return;
        }
        if (OnlineMessages.T_AUTH_OK.equals(type)) {
            AuthOk ok = OnlineMessages.payloadOf(json, AuthOk.class);
            if (ok != null) {
                this.lastProfile = ok;
                if (ok.entryFee > 0) {
                    this.serverEntryFee = ok.entryFee;
                }
                if (ok.winCoins > 0) {
                    this.serverWinCoins = ok.winCoins;
                }
                if (ok.drawCoins > 0) {
                    this.serverDrawCoins = ok.drawCoins;
                }
                Runnable callback = this.profileCallback;
                if (callback != null) {
                    try {
                        callback.run();
                    } catch (RuntimeException ignored) {
                    }
                }
                OnlineIdentity.saveToken(appContext, tokenOf(json), ok.userId);
                OnlineIdentity.setNickname(appContext, ok.nickname);
                setState(STATE_IDLE);
                if (listener != null) {
                    try {
                        listener.onAuthOk(ok);
                    } catch (RuntimeException ignored) {
                    }
                }
            }
            return;
        }
        if (OnlineMessages.T_ERROR.equals(type)) {
            OnlineMessages.ErrorMsg err = OnlineMessages.payloadOf(json, OnlineMessages.ErrorMsg.class);
            int code = err != null ? err.code : 0;
            String message = err != null && err.message != null ? err.message : "unknown";
            if (code == 401) {
                // Unknown/expired token: drop identity and re-register.
                OnlineIdentity.clearToken(appContext);
                authorize();
                return;
            }
            // A rejected room command (404/409) ends the pending pairing;
            // leaving the flag set would wrongly admit a stale match_found.
            this.friendRoomPending = false;
            cancelRoomAck();
            notifyError(code, message);
            return;
        }
        if (OnlineMessages.T_MATCH_FOUND.equals(type)) {
            // Accept a pairing only while we are actually searching (or a
            // friend-room pairing is pending); a frame arriving in any other
            // state belongs to a stale server-side room and would hijack the
            // session (seen in the 2026-10-04 test where a leftover queue
            // entry bot-matched an idle lobby client).
            if (this.state != STATE_QUEUE && this.matchId == null && !this.friendRoomPending) {
                return;
            }
            MatchFound found = OnlineMessages.payloadOf(json, MatchFound.class);
            if (found != null) {
                this.friendRoomPending = false;
                cancelRoomAck();
                this.matchId = found.matchId;
                this.opponent = found.opponent;
                this.rounds = found.rounds;
                if (found.rounds != null && found.rounds.length > 0) {
                    this.serverRoundDurationMs = found.rounds[0].durationMs;
                }
                this.currentRound = -1;
                this.roundStartedElapsed = 0L;
                this.myScore = 0;
                this.myCompletedScore = 0;
                this.oppScore = 0;
                this.roundReported = false;
                this.disconnectedAt = 0L;
                chargeEntryFee();
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
        if (OnlineMessages.T_MATCH_CANCELLED.equals(type)) {
            this.friendRoomPending = false;
            setState(STATE_IDLE);
            if (listener != null) {
                try {
                    listener.onMatchCancelled();
                } catch (RuntimeException ignored) {
                }
            }
            return;
        }
        if (OnlineMessages.T_ROUND_START.equals(type)) {
            RoundStart start = OnlineMessages.payloadOf(json, RoundStart.class);
            if (start != null) {
                this.currentRound = start.round;
                this.roundStartedElapsed = SystemClock.elapsedRealtime();
                if (start.durationMs > 0) {
                    this.serverRoundDurationMs = start.durationMs;
                }
                this.roundReported = false;
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
                if (scoreListener != null) {
                    try {
                        scoreListener.onOpponentScore(score.score);
                    } catch (RuntimeException ignored) {
                    }
                }
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
            if (end != null) {
                this.myScore = end.myScore;
                this.oppScore = end.oppScore;
                this.myCompletedScore = end.myScore;
                if (listener != null) {
                    try {
                        listener.onRoundEnd(end);
                    } catch (RuntimeException ignored) {
                    }
                }
            }
            return;
        }
        if (OnlineMessages.T_MATCH_END.equals(type)) {
            MatchEnd end = OnlineMessages.payloadOf(json, MatchEnd.class);
            if (end != null) {
                applySettlement(end);
                this.matchEndSeen = true;
                refreshProfileRecord(end);
                this.matchId = null;
                this.currentRound = -1;
                this.roundStartedElapsed = 0L;
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

    private static boolean isMatchScoped(String type) {
        return OnlineMessages.T_ROUND_START.equals(type)
                || OnlineMessages.T_OPPONENT_SCORE.equals(type)
                || OnlineMessages.T_ROUND_END.equals(type)
                || OnlineMessages.T_MATCH_END.equals(type);
    }

    private boolean ownedByCurrentMatch(String json) {
        try {
            com.google.gson.JsonObject root =
                    new com.google.gson.JsonParser().parse(json).getAsJsonObject();
            if (!root.has("d") || !root.get("d").isJsonObject()) {
                return true; // malformed: let the existing handlers ignore it
            }
            com.google.gson.JsonObject d = root.get("d").getAsJsonObject();
            if (!d.has("matchId") || d.get("matchId").isJsonNull()) {
                return true; // legacy server frame without tagging
            }
            String frameMatch = d.get("matchId").getAsString();
            return frameMatch.equals(this.matchId);
        } catch (RuntimeException e) {
            return true;
        }
    }

    private void setState(int newState) {
        if (state != newState) {
            state = newState;
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
        }
    }

    private void notifyError(int code, String message) {
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
            c.send(OnlineMessages.envelope(type, data));
        }
    }

    private int getLocalCoins() {
        try {
            return MemoryApplicationModel.getInstance().getLocalDataManager().getLocalUser().money;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** Mirrors the server-side entry fee (-30) into the local coin ledger. */
    private void chargeEntryFee() {
        if (com.cube.memorygames.api.online.OnlineDebug.freeEconomy(this.appContext)) {
            return; // debug free economy: no local mirroring, server books only
        }
        try {
            MemoryApplicationModel.getInstance().getLocalDataManager().chargeForOnline();
        } catch (RuntimeException ignored) {
        }
    }

    /** Mirrors the server-side settlement payout into the local coin ledger. */
    private void applySettlement(MatchEnd end) {
        if (com.cube.memorygames.api.online.OnlineDebug.freeEconomy(this.appContext)) {
            return; // debug free economy: no local mirroring, server books only
        }
        try {
            LocalDataManager dm = MemoryApplicationModel.getInstance().getLocalDataManager();
            if (OnlineSession.RESULT_WIN.equals(end.result)) {
                dm.addCoinsTransaction(LocalDataManager.TYPE_WIN_ONLINE, this.serverWinCoins);
            } else if (OnlineSession.RESULT_DRAW.equals(end.result)) {
                dm.addCoinsTransaction(LocalDataManager.TYPE_DRAW_LEVEL, this.serverDrawCoins);
            } else {
                dm.addCoinsTransaction(LocalDataManager.TYPE_LOSE_ONLINE, 0);
            }
        } catch (RuntimeException ignored) {
        }
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
