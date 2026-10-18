package com.cube.memorygames.api.online;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.util.Map;

/**
 * Wire protocol between the client and the PK server (see
 * docs/online-pk-prd.md section 4.3). Every frame is a JSON envelope
 * {"v":1,"t":"<type>","d":{...}}. Payloads use plain public fields and are
 * serialized field-only by gson on the client and by a field-visible
 * ObjectMapper on the server, so both sides agree on the wire format without
 * annotations.
 */
public final class OnlineMessages {

    public static final int VERSION = 1;

    /** Client -> server message types. */
    public static final String T_REGISTER = "register";
    public static final String T_AUTH = "auth";
    public static final String T_RESUME = "resume";
    public static final String T_MATCH_JOIN = "match_join";
    public static final String T_BOT_MATCH_REQUEST = "bot_match_request";
    public static final String T_MATCH_CANCEL = "match_cancel";
    public static final String T_LEVEL_WIN = "level_win";
    public static final String T_ROUND_COMPLETE = "round_complete";
    public static final String T_RESIGN = "resign";
    public static final String T_MATCH_START = "match_start";
    public static final String T_ROUND_READY = "round_ready";
    public static final String T_OPPONENT_START = "opponent_start";
    public static final String T_TOP_REQUEST = "top_request";
    public static final String T_TOP_RESPONSE = "top_response";
    public static final String T_ROOM_CREATE = "room_create";
    public static final String T_ROOM_JOIN = "room_join";
    public static final String T_ROOM_CANCEL = "room_cancel";
    public static final String T_ROOM_CREATED = "room_created";
    public static final String T_HISTORY_REQUEST = "history_request";
    public static final String T_HISTORY_RESPONSE = "history_response";
    public static final String T_PING = "ping";

    /** Server -> client message types. */
    public static final String T_AUTH_OK = "auth_ok";
    public static final String T_SESSION_REPLACED = "session_replaced";
    /** Immediate receipt for a match_join (search watchdog uses it). */
    public static final String T_MATCH_QUEUED = "match_queued";
    public static final String T_MATCH_FOUND = "match_found";
    public static final String T_MATCH_CANCELLED = "match_cancelled";
    public static final String T_ROUND_START = "round_start";
    public static final String T_OPPONENT_SCORE = "opponent_score";
    public static final String T_ROUND_END = "round_end";
    public static final String T_MATCH_END = "match_end";
    public static final String T_OPPONENT_DISCONNECTED = "opponent_disconnected";
    public static final String T_OPPONENT_RECONNECTED = "opponent_reconnected";
    public static final String T_PONG = "pong";
    public static final String T_ERROR = "error";

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private OnlineMessages() {
    }

    // ---------------------------------------------------------------- DTOs

    public static class MatchStats {
        public long wins;
        public long losses;
        public long draws;
    }

    /** Server-side opponent / profile snapshot. */
    public static class OpponentInfo {
        public long userId;
        public String nickname;
        public int elo;
        public int rank;
        public boolean isBot;
        /** Practice bots only: the difficulty tier id (e.g. "BRONZE"). */
        public String tier;
        /** Season record for the pre-match statistics board. */
        public int wins;
        public int losses;
        public int draws;
        public Map<String, MatchStats> modeStats;
    }

    public static class Register {
        public String uuid;
        public String nickname;
        public int balance;
    }

    public static class Auth {
        public String token;
    }

    public static class Resume {
        public String matchId;
    }

    public static class LevelWin {
        public int round;
        public int level;
        public int score;
    }

    public static class RoundComplete {
        public int round;
        public int score;
    }

    public static class MatchStart {
        public boolean prepareRounds = true;
    }

    public static class RoundReady {
        public String matchId;
        public int round;
    }

    public static class AuthOk {
        /** Live economy snapshot from the server's runtime config. */
        public int entryFee;
        public int winCoins;
        public int drawCoins;
        public long userId;
        public String accountType;
        public String avatarValue;
        public String nickname;
        public int elo;
        public int rank;
        public int wins;
        public int losses;
        public int draws;
        public Map<String, MatchStats> modeStats;
        public int balance;
        public int seasonIndex;
        public long seasonEndsAt;
        /** Consecutive practice (vs-bot) wins, for the difficulty card. */
        public int practiceStreak;
        /** Live difficulty tiers so the card mirrors admin hot config. */
        public PracticeTier[] practiceTiers;
    }

    /** One practice difficulty tier as seen by the picker card. */
    public static class PracticeTier {
        public String id;
        public int elo;
        public int winCoins;
    }

    public static class RoomJoin {
        public String code;
    }

    /** BotMatchRequest: the difficulty tier id picked in the lobby card. */
    public static class BotMatchRequest {
        public String difficulty;
    }

    public static class RoomCreated {
        public String code;
    }

    public static class HistoryEntry {
        public String matchId;
        public long opponentUserId;
        public long ts;
        public String opponent;
        public String mode;
        public String result;
        public int myScore;
        public int oppScore;
        public boolean vsBot;
    }

    public static class HistoryResponse {
        public HistoryEntry[] matches;
    }

    public static class RoundDesc {
        public String gameId;
        public int durationMs;
        public long seed;
    }

    public static class MatchFound {
        public String matchId;
        /** RANKED, FRIEND or PRACTICE (play vs bot). */
        public String mode;
        public OpponentInfo opponent;
        public RoundDesc[] rounds;
    }

    public static class RoundStart {
        public String matchId;
        public int round;
        public String gameId;
        public int durationMs;
        public long serverNow;
        public boolean preparing;
        /** Local-only: this is a replay of an already running round. */
        public transient boolean resuming;
    }

    public static class OpponentScore {
        public String matchId;
        public int round;
        public int score;
    }

    public static class RoundEnd {
        public String matchId;
        public int round;
        public int myScore;
        public int oppScore;
        /** Optional on older servers; zero means no announced next round. */
        public long nextRoundStartsAt;
        public long serverNow;
        public String nextGameId;
    }

    /** result: "W" win, "L" loss, "D" draw. */
    public static class MatchEnd {
        public String matchId;
        public String mode;
        public Map<String, MatchStats> modeStats;
        public String result;
        public int myScore;
        public int oppScore;
        public int eloDelta;
        public int elo;
        public int coinDelta;
        public int balance;
        /** Practice only: consecutive vs-bot wins after this settlement. */
        public int practiceStreak;
        /** Season record after settlement for instant profile refresh. */
        public int myWins;
        public int myLosses;
        public int myDraws;
        /** Per-round scores feeding the results list board. */
        public RoundScore[] rounds;
    }

    /** Origin's UserConfirmedGameEvent: both START presses are in. */
    public static class OpponentStart {
        public String matchId;
    }

    public static class RoundScore {
        public int me;
        public int opp;
    }

    public static class TopEntry {
        public long userId;
        public int rank;
        public String nickname;
        public int elo;
        public boolean online;
    }

    public static class TopResponse {
        public TopEntry[] entries;
    }

    public static class ErrorMsg {
        public int code;
        public String message;
    }

    // ------------------------------------------------------------- helpers

    public static String envelope(String type, Object data) {
        JsonObject root = new JsonObject();
        root.addProperty("v", VERSION);
        root.addProperty("t", type);
        root.add("d", GSON.toJsonTree(data));
        return GSON.toJson(root);
    }

    public static String typeOf(String json) {
        try {
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            return root == null ? null : (root.has("t") ? root.get("t").getAsString() : null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static <T> T payloadOf(String json, Class<T> clazz) {
        try {
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null || !root.has("d") || root.get("d").isJsonNull()) {
                return GSON.fromJson("{}", clazz);
            }
            return GSON.fromJson(root.get("d"), clazz);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
