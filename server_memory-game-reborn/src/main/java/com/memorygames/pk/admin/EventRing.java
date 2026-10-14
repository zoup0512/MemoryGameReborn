package com.memorygames.pk.admin;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * In-process ring buffer of recent business events (admin design 4.8):
 * the online tail for the dashboard and the primary tool for live
 * debugging of issues like the open phantom match_join — every match_join
 * is recorded with its originating WS session id.
 */
@Component
public class EventRing {

    /** Capacity covers a busy debugging session without memory concerns. */
    private static final int CAPACITY = 1000;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EventRing.class);
    private static final int MAX_DETAIL_LENGTH = 2048;
    private static final java.util.Set<String> FRAME_TYPES = new java.util.HashSet<>();

    static {
        // Protocol constants, not remote strings, define the loggable frame vocabulary.
        for (java.lang.reflect.Field field : com.memorygames.pk.protocol.ProtocolCodec.class.getFields()) {
            if (field.getName().startsWith("T_") && field.getType() == String.class) {
                try {
                    FRAME_TYPES.add((String) field.get(null));
                } catch (IllegalAccessException ignored) {
                    // Unknown types remain redacted.
                }
            }
        }
    }

    public static String safeFrameType(String value) {
        return FRAME_TYPES.contains(value) ? value : "unknown";
    }

    /** Only server identifiers, never remote free text, belong in identifier fields. */
    public static String safeIdentifier(String value) {
        return value != null && value.matches("[A-Za-z0-9_.-]{1,64}") ? value : "unknown";
    }

    /** Structured diagnostic scalars only; payloads and arbitrary text are discarded. */
    private static String safeDetail(String detail) {
        if (detail == null) {
            return null;
        }
        if (detail.length() > MAX_DETAIL_LENGTH) {
            return "detail=redacted";
        }
        StringBuilder out = new StringBuilder();
        for (String field : detail.split(" ")) {
            int equals = field.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String key = field.substring(0, equals);
            String value = field.substring(equals + 1);
            boolean safe;
            switch (key) {
                case "user": case "a": case "b": case "host": case "guest":
                case "round": case "current": case "level": case "score":
                case "game": case "dur": case "seed": case "rounds": case "waited":
                case "windowMs": case "code": case "closeCode": case "attempt": case "attempts":
                case "avg": case "floor": case "suspicion": case "winner": case "seat":
                    safe = value.matches("-?[0-9]{1,20}");
                    break;
                case "accepted": case "forced": case "forfeit": case "voided":
                case "refund": case "bot": case "doneA": case "doneB":
                case "acceptedA": case "acceptedB": case "replayed": case "disconnected":
                    safe = value.matches("true|false");
                    break;
                case "session": case "match":
                    safe = value.equals(safeIdentifier(value));
                    break;
                case "mode":
                    safe = value.matches("RANKED|FRIEND|PRACTICE");
                    break;
                case "frame":
                    safe = value.equals(safeFrameType(value));
                    break;
                case "class":
                    safe = value.matches("[A-Za-z_$][A-Za-z0-9_.$]{0,127}");
                    break;
                case "reason":
                    safe = value.matches("ok|unknown_user|settled|round_mismatch|duplicate|both_complete|deadline"
                            + "|rounds_complete|resign|accept_timeout|disconnect_timeout|admin_settle|admin_terminate"
                            + "|session_replaced|session_revoked|legacy_auth_disabled|policy_violation"
                            + "|oversized_frame|message_rate_limit|db_pool|transaction|scheduler_rejected|send_failure"
                            + "|late|interval|sequence");
                    break;
                case "scores":
                    safe = value.matches("[0-9-]{1,12}/[0-9-]{1,12}");
                    break;
                case "plan":
                    safe = value.matches("[0-9:,/-]{1,1024}");
                    break;
                default:
                    safe = false;
            }
            if (safe) {
                if (out.length() > 0) {
                    out.append(' ');
                }
                out.append(field);
            }
        }
        return out.toString();
    }

    public static class Event {

        public final long ts;
        public final String type;
        public final String matchId;
        public final String detail;

        Event(long ts, String type, String matchId, String detail) {
            this.ts = ts;
            this.type = type;
            this.matchId = matchId;
            this.detail = detail;
        }

        public String format() {
            java.text.SimpleDateFormat format =
                    new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US);
            StringBuilder sb = new StringBuilder(format.format(new java.util.Date(this.ts)));
            sb.append(' ').append(this.type);
            if (this.matchId != null) {
                sb.append('[').append(this.matchId).append(']');
            }
            if (this.detail != null && !this.detail.isEmpty()) {
                sb.append(' ').append(this.detail);
            }
            return sb.toString();
        }
    }

    private final Deque<Event> ring = new ArrayDeque<>();

    public void emit(String type, String matchId, String detail) {
        String safeType = type != null && type.matches("[A-Z][A-Z0-9_]{0,47}")
                ? type : "UNKNOWN";
        Event event = new Event(System.currentTimeMillis(), safeType,
                matchId == null ? null : safeIdentifier(matchId), safeDetail(detail));
        synchronized (this.ring) {
            this.ring.addLast(event);
            while (this.ring.size() > CAPACITY) {
                this.ring.removeFirst();
            }
        }
        // Synchronous INFO output survives eviction and uses the configured SLF4J appenders.
        log.info("event={} match={} {}", event.type,
                event.matchId == null ? "none" : event.matchId,
                event.detail == null ? "" : event.detail);
    }

    /** Newest-first snapshot of at most {@code n} events. */
    public List<Event> recent(int n) {
        synchronized (this.ring) {
            List<Event> out = new ArrayList<>(Math.min(n, this.ring.size()));
            java.util.Iterator<Event> it = this.ring.descendingIterator();
            while (out.size() < n && it.hasNext()) {
                out.add(it.next());
            }
            return out;
        }
    }

    public int size() {
        synchronized (this.ring) {
            return this.ring.size();
        }
    }
}
