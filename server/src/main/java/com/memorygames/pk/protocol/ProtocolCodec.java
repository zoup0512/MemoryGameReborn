package com.memorygames.pk.protocol;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Wire codec for the envelope {"v":1,"t":"<type>","d":{...}}.
 *
 * Payload classes use public fields only; the client (gson, field-based)
 * and this mapper therefore agree on the wire format without annotations.
 */
public final class ProtocolCodec {

    public static final int VERSION = 1;

    public static final String T_REGISTER = "register";
    public static final String T_AUTH = "auth";
    public static final String T_RESUME = "resume";
    public static final String T_MATCH_JOIN = "match_join";
    public static final String T_MATCH_CANCEL = "match_cancel";
    public static final String T_LEVEL_WIN = "level_win";
    public static final String T_ROUND_COMPLETE = "round_complete";
    public static final String T_RESIGN = "resign";
    public static final String T_ROOM_CREATE = "room_create";
    public static final String T_ROOM_JOIN = "room_join";
    public static final String T_ROOM_CANCEL = "room_cancel";
    public static final String T_MATCH_START = "match_start";
    public static final String T_TOP_REQUEST = "top_request";
    public static final String T_ROOM_CREATED = "room_created";
    public static final String T_HISTORY_REQUEST = "history_request";
    public static final String T_HISTORY_RESPONSE = "history_response";
    public static final String T_PING = "ping";

    public static final String T_AUTH_OK = "auth_ok";
    public static final String T_MATCH_FOUND = "match_found";
    public static final String T_MATCH_CANCELLED = "match_cancelled";
    public static final String T_ROUND_START = "round_start";
    public static final String T_OPPONENT_SCORE = "opponent_score";
    public static final String T_ROUND_END = "round_end";
    public static final String T_MATCH_END = "match_end";
    public static final String T_OPPONENT_DISCONNECTED = "opponent_disconnected";
    public static final String T_OPPONENT_RECONNECTED = "opponent_reconnected";
    public static final String T_TOP_RESPONSE = "top_response";
    public static final String T_PONG = "pong";
    public static final String T_ERROR = "error";

    /** Match settlement results. */
    public static final String RESULT_WIN = "W";
    public static final String RESULT_LOSE = "L";
    public static final String RESULT_DRAW = "D";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
            .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

    private ProtocolCodec() {
    }

    // -------------------------------------------------------------- DTOs

    public static class Envelope {
        public int v;
        public String t;
        public Object d;
    }

    public static class Register {
        public String uuid;
        public String nickname;
        public int balance;
    }

    public static class Auth {
        public String token;
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

    public static class OpponentInfo {
        public long userId;
        public String nickname;
        public int elo;
        public int rank;
        public boolean isBot;
        /** Season record for the pre-match statistics board. */
        public int wins;
        public int losses;
        public int draws;
    }

    public static class RoundDesc {
        public String gameId;
        public int durationMs;
        public long seed;
    }

    public static class MatchFound {
        public String matchId;
        /** RANKED (random matchmaking) or FRIEND (room code). */
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
    }

    /** result: "W" win, "L" loss, "D" draw. */
    public static class MatchEnd {
        public String matchId;
        public String result;
        public int myScore;
        public int oppScore;
        public int eloDelta;
        public int elo;
        public int coinDelta;
        public int balance;
        /** Per-round scores feeding the results list board. */
        public RoundScore[] rounds;
    }

    public static class RoundScore {
        public int me;
        public int opp;
    }

    public static class TopEntry {
        public int rank;
        public String nickname;
        public int elo;
        public boolean online;
    }

    public static class TopResponse {
        public TopEntry[] entries;
    }

    public static class AuthOk {
        public String token;
        /** Runtime economy snapshot so clients gate/mirror with live values. */
        public int entryFee;
        public int winCoins;
        public int drawCoins;
        public long userId;
        public String nickname;
        public int elo;
        public int rank;
        public int wins;
        public int losses;
        public int draws;
        public int balance;
        public int seasonIndex;
        public long seasonEndsAt;
    }

    public static class RoomJoin {
        public String code;
    }

    public static class RoomCreated {
        public String code;
    }

    public static class HistoryEntry {
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

    public static class ErrorMsg {
        public int code;
        public String message;
    }

    // ------------------------------------------------------------ helpers

    public static Envelope parse(String json) {
        try {
            Envelope envelope = MAPPER.readValue(json, Envelope.class);
            return envelope.t == null ? null : envelope;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public static <T> T convert(Object raw, Class<T> type) {
        return MAPPER.convertValue(raw, type);
    }

    public static String encode(String type, Object data) {
        if (data == null || data.getClass() == Object.class) {
            // Empty payloads (match_join, resign, ...) carry no fields; a bare
            // java.lang.Object has nothing for Jackson to reflect on.
            return "{\"v\":" + VERSION + ",\"t\":\"" + type + "\",\"d\":{}}";
        }
        Envelope envelope = new Envelope();
        envelope.v = VERSION;
        envelope.t = type;
        envelope.d = data;
        try {
            return MAPPER.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("unencodable frame", e);
        }
    }
}
