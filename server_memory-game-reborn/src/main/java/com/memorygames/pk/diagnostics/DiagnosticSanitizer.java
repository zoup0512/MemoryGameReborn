package com.memorygames.pk.diagnostics;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.memorygames.pk.user.AccountException;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/** Reconstruct events, never retain raw frames, arbitrary text, credentials or exception messages. */
@Component
public class DiagnosticSanitizer {
    public static final int MAX_CONTENT_BYTES=192*1024;
    private static final Set<String> EVENTS=Set.of(
            "application_start","fatal","ws_connect","ws_connected","ws_close","ws_error","ws_ping_timeout","ws_send_failed",
            "session_state","session_connect","session_disconnect","session_reconnect","reconnect_exhausted",
            "frame_received","frame_dropped","snapshot_clear","queue_timeout","room_timeout","accept_timeout",
            "resign","resign_send","round_complete","match_end","match_cancelled","match_create","match_destroy","match_finish",
            "exit_dialog","exit_confirm","exit_cancel","game_launch","tutorial_launch","unknown_game","game_return",
            "game_back_dialog","game_back_confirm","game_back_cancel","game_round_finished","session_send_failed","log_dropped",
            "question_generated","question_visible","question_hidden","question_options","question_answer",
            "question_transition","question_callback","flow_start","flow_state","flow_pause","flow_resume","flow_cancel","game_activity");
    private static final Set<String> NUMBERS=Set.of("ts","seq","round","gameId","epoch","attempt","delayMs","score","durationMs",
            "question","level","targetQuestion","flowRun","flowStep","generation","elapsedMs","callbackId");
    private static final Set<String> PHASES=Set.of("create","recreate","resume","pause","destroy","request","held","apply",
            "schedule","execute","skipped","visible","hidden","options","correct","wrong","intent","generated");
    private static final Set<String> CODES=Set.of("state","from","to","mode","reason","code","remote");
    private static final Pattern SECRET=Pattern.compile("(?i)(token|bearer|password|secret|authorization|cookie|credential|installation|refresh|access[_-]?key)|[A-Za-z0-9_-]{28,}");
    private static final Pattern FRAME=Pattern.compile("[A-Za-z_$][A-Za-z0-9_$.]{0,180}\\.(?:[A-Za-z_$][A-Za-z0-9_$]{0,80}|<init>|<clinit>):(?:[0-9]{1,7}|-[12])");
    private final ObjectMapper json;
    public DiagnosticSanitizer(ObjectMapper mapper) {
        json=mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }
    public static AccountException invalid() { return new AccountException(400,"INVALID_INPUT","Invalid diagnostic report"); }
    public static boolean identifier(String value) { return value!=null && value.matches("(?:[a-fA-F0-9]{12}|[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12})"); }
    public static boolean uuid(String value) { return value!=null && value.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"); }
    private String safeCode(String value) {
        return value.length()<=96 && value.matches("[A-Za-z0-9_.:-]{1,96}") && !SECRET.matcher(value).find()?value:"[redacted]";
    }
    public String metadata(String value) {
        if(value==null || value.length()>80 || value.codePoints().anyMatch(Character::isISOControl)) throw invalid();
        return SECRET.matcher(value).find() || value.contains("/") || value.contains("@")?"[redacted]":value;
    }
    private String stack(JsonNode value) {
        List<String> lines=new ArrayList<>();
        if(value.isTextual()) lines.addAll(Arrays.asList(value.textValue().split("\\R")));
        else if(value.isArray()) value.forEach(v->{ if(v.isTextual()) lines.add(v.textValue()); });
        return lines.stream().filter(s->FRAME.matcher(s).matches() && !s.toLowerCase(Locale.ROOT).contains("token"))
                .limit(8).collect(java.util.stream.Collectors.joining("\n"));
    }
    public String sanitize(String content) {
        if(content==null || content.isBlank()) throw invalid();
        if(content.getBytes(StandardCharsets.UTF_8).length>MAX_CONTENT_BYTES)
            throw new AccountException(413,"PAYLOAD_TOO_LARGE","Diagnostic content exceeds 192 KiB UTF-8");
        String[] lines=content.split("\n",-1);
        if(lines.length>2048) throw invalid();
        StringBuilder result=new StringBuilder();
        try {
            for(String line:lines) {
                if(line.isBlank()) continue;
                JsonNode source=json.readTree(line);
                if(!source.isObject()) throw invalid();
                if(!source.path("event").isTextual() || !EVENTS.contains(source.path("event").textValue())) continue;
                ObjectNode safe=json.createObjectNode();
                safe.put("event",source.path("event").textValue());
                source.fields().forEachRemaining(entry->{
                    String key=entry.getKey(); JsonNode value=entry.getValue();
                    if("gameId".equals(key) && value.isTextual() && value.textValue().matches("[0-9]{1,3}")) safe.put(key,value.textValue());
                    else if(NUMBERS.contains(key) && value.isIntegralNumber() && value.canConvertToLong()) safe.put(key,value.longValue());
                    else if(("activityId".equals(key) || "fingerprint".equals(key)) && value.isTextual()
                            && value.textValue().matches("[a-f0-9]{16}")) safe.put(key,value.textValue());
                    else if("phase".equals(key) && value.isTextual() && PHASES.contains(value.textValue())) safe.put(key,value.textValue());
                    else if(CODES.contains(key)) {
                        if(value.isTextual()) safe.put(key,safeCode(value.textValue()));
                        else if(value.isBoolean() || (value.isIntegralNumber() && value.canConvertToLong())) safe.set(key,value);
                    } else if("matchId".equals(key) && value.isTextual() && identifier(value.textValue())) safe.put(key,value.textValue());
                    else if("run".equals(key) && value.isTextual() && uuid(value.textValue())) safe.put(key,value.textValue());
                    else if("stack".equals(key)) { String frames=stack(value); if(!frames.isEmpty()) safe.put(key,frames); }
                });
                result.append(json.writeValueAsString(safe)).append('\n');
            }
        } catch(com.fasterxml.jackson.core.JsonProcessingException ex) { throw invalid(); }
        if(result.isEmpty()) throw invalid();
        return result.toString();
    }
}
