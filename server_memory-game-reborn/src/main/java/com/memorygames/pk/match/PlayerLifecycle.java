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

    /** Persistent multiplayer lobby ownership; never released by legacy duel callbacks. */
    public static final String PARTY_LOBBY = "PARTY_LOBBY";

    private record Claim(String state, String owner) { }
    private final ConcurrentHashMap<Long, Claim> states = new ConcurrentHashMap<>();

    public boolean claimOwned(long userId, String state, String owner) {
        return this.states.putIfAbsent(userId, new Claim(state, owner)) == null;
    }

    public void releaseOwned(long userId, String state, String owner) {
        this.states.remove(userId, new Claim(state, owner));
    }

    /**
     * Atomically claims the user for {@code to}.
     *
     * @return false when the user is already claimed in any state (busy)
     */
    public boolean claim(long userId, String to) {
        return claimOwned(userId, to, null);
    }

    /**
     * Atomic transition between two claimed states (QUEUED -> MATCHING,
     * MATCHING -> IN_MATCH, ...). Fails when the current state differs,
     * which means somebody else moved or released the claim first.
     */
    public boolean transition(long userId, String from, String to) {
        return this.states.replace(userId, new Claim(from, null), new Claim(to, null));
    }

    /** Releases the claim only if it currently is {@code expected}. */
    public void releaseIf(long userId, String expected) {
        this.states.remove(userId, new Claim(expected, null));
    }

    /** Unconditional release (settlement / terminal cleanup). */
    public void release(long userId) {
        this.states.computeIfPresent(userId, (id, claim) -> claim.owner() == null ? null : claim);
    }

    public boolean isClaimed(long userId) {
        return this.states.containsKey(userId);
    }

    public String stateOf(long userId) {
        Claim claim = this.states.get(userId);
        return claim == null ? null : claim.state();
    }
}
