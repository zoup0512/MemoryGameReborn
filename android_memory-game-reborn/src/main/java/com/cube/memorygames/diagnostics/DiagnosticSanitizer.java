package com.cube.memorygames.diagnostics;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Closed schema: never serialize a protocol object, exception message, URL or user input. */
public final class DiagnosticSanitizer {
    private static final Set<String> EVENTS = set("log_dropped application_start fatal ws_connect ws_connected ws_close ws_error ws_ping_timeout ws_send_failed session_state session_connect session_disconnect session_reconnect reconnect_exhausted frame_received frame_dropped snapshot_clear queue_timeout room_timeout accept_timeout resign resign_send round_complete match_end match_cancelled match_create match_destroy match_finish exit_dialog exit_confirm exit_cancel game_launch tutorial_launch unknown_game game_return game_back_dialog game_back_confirm game_back_cancel game_round_finished session_send_failed");
    private static final Set<String> CODES = set("RANKED FRIEND PRACTICE create recreate destroy configuration finish framework back_no_match user_resign search_cancel unknown_game match_cancelled bad_type wrong_match unexpected_match stale_epoch reconnect_window queue_ack room_ack accept_gate not_connected ping_timeout runtime tls send class_error round_complete exit_confirm exit_cancel launch_cancelled");
    private static final Set<String> NUMBER = set("round state from to epoch attempt delayMs code score durationMs");
    private static final Set<String> FRAME_TYPES = set("resume match_join bot_match_request match_cancel level_win round_complete resign match_start opponent_start top_request top_response room_create room_join room_cancel room_created history_request history_response session_replaced match_queued match_found match_cancelled round_start opponent_score round_end match_end opponent_disconnected opponent_reconnected error");
    public static boolean businessType(String type) { return FRAME_TYPES.contains(type); }
    private DiagnosticSanitizer() { }
    private static Set<String> set(String text) { return new HashSet<>(Arrays.asList(text.split(" "))); }
    public static String record(long ts, long seq, String run, String event, Object... fields) {
        if (!EVENTS.contains(event) || !run.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) return null;
        StringBuilder out=new StringBuilder("{\"ts\":").append(ts).append(",\"seq\":").append(seq)
                .append(",\"run\":").append(quote(run)).append(",\"event\":").append(quote(event));
        Set<String> seen=new HashSet<>();
        for (int i=0; fields != null && i+1<fields.length && i<40; i+=2) {
            if (!(fields[i] instanceof String)) continue;
            String key=(String)fields[i]; Object value=fields[i+1];
            if (value == null || !seen.add(key)) continue;
            String safe=null;
            if (NUMBER.contains(key) && (value instanceof Integer || value instanceof Long || value instanceof Short)) safe=value.toString();
            else if ("remote".equals(key) && value instanceof Boolean) safe=value.toString();
            else if (value instanceof String) {
                String s=(String)value;
                if ("matchId".equals(key) && s.matches("[A-Za-z0-9]{12}")) safe=quote(s);
                else if ("gameId".equals(key) && s.matches("[0-9]{1,3}")) safe=quote(s);
                else if (("reason".equals(key) || "mode".equals(key)) && (CODES.contains(s) || ("reason".equals(key) && FRAME_TYPES.contains(s)))) safe=quote(s);
                else if ("reason".equals(key) && s.matches("(?:java|javax|android|org\\.java_websocket|com\\.google\\.gson)\\.[a-zA-Z0-9_.$]*(?:Exception|Error)")) safe=quote(s.substring(0,Math.min(s.length(),160)));
                else if ("stack".equals(key) && validStack(s)) safe=quote(s);
            }
            if (safe != null) out.append(',').append(quote(key)).append(':').append(safe);
        }
        out.append("}\n");
        String result=out.toString();
        return result.getBytes(StandardCharsets.UTF_8).length<=2048 ? result : null;
    }
    private static boolean validStack(String s) {
        if(s.isEmpty()) return true;
        if(s.length()>960) return false;
        String[] frames=s.split("\\n",-1);if(frames.length>8) return false;
        for(String frame:frames) if(!frame.matches("(?:[a-zA-Z_$][a-zA-Z0-9_$]*\\.)+[a-zA-Z0-9_$<>]+:-?[0-9]{1,9}")) return false;
        return true;
    }
    public static String stack(StackTraceElement[] frames) {
        StringBuilder out=new StringBuilder();
        for (int i=0; frames != null && i<frames.length && i<8; i++) {
            StackTraceElement f=frames[i];
            String frame=f.getClassName()+"."+f.getMethodName()+":"+f.getLineNumber();
            if (!frame.matches("[a-zA-Z0-9_.$:<>-]+")) continue;
            if (out.length()+frame.length()+1>960) break;
            if (out.length()>0) out.append('\n');
            out.append(frame);
        }
        return out.toString();
    }
    public static String quote(String s) {
        StringBuilder out=new StringBuilder("\"");
        for (int i=0;i<s.length();i++) {
            char c=s.charAt(i);
            if (c=='"' || c=='\\') out.append('\\').append(c);
            else if (c=='\n') out.append("\\n");
            else if (c=='\r') out.append("\\r");
            else if (c=='\t') out.append("\\t");
            else if (c<32 || c=='<' || c=='>' || c=='&' || c=='\u2028' || c=='\u2029') out.append(String.format(java.util.Locale.US,"\\u%04x",(int)c));
            else out.append(c);
        }
        return out.append('"').toString();
    }
}
