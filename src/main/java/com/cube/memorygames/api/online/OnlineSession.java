package com.cube.memorygames.api.online;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

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
    /** Notified on the main thread whenever a fresh profile arrives. */
    private volatile Runnable profileCallback;
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
            disconnectedAt = 0L;
        }
        setState(STATE_DISCONNECTED);
    }

    // ------------------------------------------------------- matchmaking

    public void startMatchmaking() {
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
            send(OnlineMessages.T_RESIGN, new Object());
        }
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
            notifyError(code, message);
            return;
        }
        if (OnlineMessages.T_MATCH_FOUND.equals(type)) {
            // Accept a pairing only while we are actually searching; a frame
            // arriving in any other state belongs to a stale server-side
            // room and would hijack the session (seen in the 2026-10-04 test
            // where a leftover queue entry bot-matched an idle lobby client).
            if (this.state != STATE_QUEUE && this.matchId == null) {
                return;
            }
            MatchFound found = OnlineMessages.payloadOf(json, MatchFound.class);
            if (found != null) {
                this.matchId = found.matchId;
                this.opponent = found.opponent;
                this.rounds = found.rounds;
                this.currentRound = -1;
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
                this.matchId = null;
                this.currentRound = -1;
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
                dm.addCoinsTransaction(LocalDataManager.TYPE_WIN_ONLINE, LocalDataManager.ONLINE_WIN_COINS);
            } else if (OnlineSession.RESULT_DRAW.equals(end.result)) {
                dm.addCoinsTransaction(LocalDataManager.TYPE_DRAW_LEVEL, LocalDataManager.ONLINE_DRAW_COINS);
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
