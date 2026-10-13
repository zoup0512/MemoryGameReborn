package com.memorygames.pk.match;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified per-user lifecycle gate (P1-2): every matchmaking entry path —
 * quick queue, friend room host/guest, practice duel — must CAS the user
 * from unclaimed into a claimed state before a room is created, so one
 * account can never hold a stale queue entry plus a live seat, or two
 * seats in two rooms. Claims are released by cancellation, disconnect
 * cleanup and settlement, all through the same registry.
 */
@Component
public class PlayerLifecycle {

    /** Queued in the matchmaker, awaiting a pairing or bot fallback. */
    public static final String QUEUED = "QUEUED";
    /** Hosting a friend room, awaiting a guest. */
    public static final String FRIEND_WAITING = "FRIEND_WAITING";
    /** Seat reserved; the room object is being created and registered. */
    public static final String MATCHING = "MATCHING";
    /** Seated in a live registered room. */
    public static final String IN_MATCH = "IN_MATCH";

    private final ConcurrentHashMap<Long, String> states = new ConcurrentHashMap<>();

    /**
     * Atomically claims the user for {@code to}.
     *
     * @return false when the user is already claimed in any state (busy)
     */
    public boolean claim(long userId, String to) {
        return this.states.putIfAbsent(userId, to) == null;
    }

    /**
     * Atomic transition between two claimed states (QUEUED -> MATCHING,
     * MATCHING -> IN_MATCH, ...). Fails when the current state differs,
     * which means somebody else moved or released the claim first.
     */
    public boolean transition(long userId, String from, String to) {
        return this.states.replace(userId, from, to);
    }

    /** Releases the claim only if it currently is {@code expected}. */
    public void releaseIf(long userId, String expected) {
        this.states.remove(userId, expected);
    }

    /** Unconditional release (settlement / terminal cleanup). */
    public void release(long userId) {
        this.states.remove(userId);
    }

    public boolean isClaimed(long userId) {
        return this.states.containsKey(userId);
    }

    public String stateOf(long userId) {
        return this.states.get(userId);
    }
}
