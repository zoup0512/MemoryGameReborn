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
        synchronized (this.ring) {
            this.ring.addLast(new Event(System.currentTimeMillis(), type, matchId, detail));
            while (this.ring.size() > CAPACITY) {
                this.ring.removeFirst();
            }
        }
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
