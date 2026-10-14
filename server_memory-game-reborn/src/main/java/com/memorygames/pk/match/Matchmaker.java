package com.memorygames.pk.match;

import com.memorygames.pk.admin.AdminMetrics;
import com.memorygames.pk.admin.EventRing;
import com.memorygames.pk.config.PkProperties;
import com.memorygames.pk.protocol.ProtocolCodec;
import com.memorygames.pk.user.UserAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Matchmaking queue with ELO bucketing and bot fallback (PRD 3.4): pairs
 * within +-bucket while young, widens the bucket while waiting, and
 * substitutes a bot after {@code botWaitMs}.
 *
 * Every entry path funnels through {@link PlayerLifecycle}: a user is
 * QUEUED, FRIEND_WAITING, MATCHING or IN_MATCH — never two of them — so a
 * stale queue entry can no longer fork a second, orphan match (P1-2).
 */
@Component
public class Matchmaker {

    private static final Logger log = LoggerFactory.getLogger(Matchmaker.class);
    private static final String[] BOT_NAMES = {
            "QuickMind", "Brainy Owl", "FastFingers", "SharpEye", "CleverFox",
            "NeuroNinja", "PuzzlePanda", "ThinkTank", "SmartShark", "LogicLion"
    };

    /** Gateway-visible outcomes of match_join. */
    public enum EnqueueResult {
        QUEUED, ALREADY_QUEUED, BUSY, INSUFFICIENT_COINS
    }

    /** Gateway-visible outcomes of room_join. */
    public enum JoinResult {
        LAUNCHED, NO_SUCH_ROOM, BUSY
    }

    /** A queued player together with its delivery endpoint. */
    private static class Entry {

        final UserAccount user;
        final PlayerEndpoint endpoint;
        final long enqueuedAt = System.currentTimeMillis();

        Entry(UserAccount user, PlayerEndpoint endpoint) {
            this.user = user;
            this.endpoint = endpoint;
        }
    }

    private final List<Entry> queue = new ArrayList<>();
    private final Object lock = new Object();

    /** Friend rooms awaiting a guest (PRD F-12). */
    private static final long FRIEND_ROOM_TTL_MS = 10 * 60 * 1000L;

    private static class FriendRoom {

        final String code;
        final UserAccount host;
        final PlayerEndpoint hostEndpoint;
        final long createdAt = System.currentTimeMillis();

        FriendRoom(String code, UserAccount host, PlayerEndpoint hostEndpoint) {
            this.code = code;
            this.host = host;
            this.hostEndpoint = hostEndpoint;
        }
    }

    private final List<FriendRoom> friendRooms = new ArrayList<>();
    private final java.util.Random codeRandom = new java.util.Random();

    private final MatchManager manager;
    private final PlayerLifecycle lifecycle;
    private final PkProperties properties;
    private final AdminMetrics metrics;
    private final EventRing events;
    private final Random random = new Random();

    public Matchmaker(MatchManager manager, PlayerLifecycle lifecycle, PkProperties properties,
                      AdminMetrics metrics, EventRing events) {
        this.manager = manager;
        this.lifecycle = lifecycle;
        this.properties = properties;
        this.metrics = metrics;
        this.events = events;
    }

    /** Live queue view for the admin dashboard. */
    public java.util.List<String> queueSnapshot() {
        java.util.List<String> out = new java.util.ArrayList<>();
        long now = System.currentTimeMillis();
        synchronized (this.lock) {
            for (Entry entry : this.queue) {
                out.add(String.format("user=%d(%s) waiting=%dms",
                        entry.user.id, entry.user.nickname, now - entry.enqueuedAt));
            }
        }
        return out;
    }

    /** Called by the WS gateway on match_join. */
    public EnqueueResult enqueue(UserAccount user, PlayerEndpoint endpoint) {
        if (user.balance < this.properties.getEconomy().getEntryFee()) {
            return EnqueueResult.INSUFFICIENT_COINS;
        }
        synchronized (this.lock) {
            String state = this.lifecycle.stateOf(user.id);
            if (PlayerLifecycle.QUEUED.equals(state)) {
                return EnqueueResult.ALREADY_QUEUED; // idempotent re-join
            }
            if (!this.lifecycle.claim(user.id, PlayerLifecycle.QUEUED)) {
                // In another match, hosting/joining a friend room, or mid
                // room creation: the queue must never fork a second match.
                return EnqueueResult.BUSY;
            }
            this.queue.add(new Entry(user, endpoint));
        }
        this.metrics.queueJoins.incrementAndGet();
        scan();
        return EnqueueResult.QUEUED;
    }

    /** Called by the WS gateway on match_cancel. */
    public boolean cancel(long userId) {
        synchronized (this.lock) {
            Iterator<Entry> it = this.queue.iterator();
            while (it.hasNext()) {
                Entry entry = it.next();
                if (entry.user.id == userId) {
                    it.remove();
                    this.lifecycle.releaseIf(userId, PlayerLifecycle.QUEUED);
                    entry.endpoint.send(ProtocolCodec.encode(ProtocolCodec.T_MATCH_CANCELLED, new Object()));
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Creates a friend room; returns its 4-digit code (PRD F-12), or null
     * when the host is busy in the queue or another match.
     */
    public String createFriendRoom(UserAccount host, PlayerEndpoint endpoint) {
        String code;
        synchronized (this.lock) {
            dropExpiredFriendRooms();
            String state = this.lifecycle.stateOf(host.id);
            if (!PlayerLifecycle.FRIEND_WAITING.equals(state)
                    && !this.lifecycle.claim(host.id, PlayerLifecycle.FRIEND_WAITING)) {
                return null;
            }
            // also drop any previous room hosted by this user
            this.friendRooms.removeIf(r -> r.host.id.equals(host.id));
            do {
                code = String.format("%04d", this.codeRandom.nextInt(10000));
            } while (roomCodeTaken(code));
            this.friendRooms.add(new FriendRoom(code, host, endpoint));
        }
        this.events.emit("ROOM_CREATED", null, "host=" + host.id + " code=" + code);
        return code;
    }

    /** Guest joins by code; the pairing is launched under the lifecycle gate. */
    public JoinResult joinFriendRoom(String code, UserAccount guest, PlayerEndpoint endpoint) {
        FriendRoom room = null;
        synchronized (this.lock) {
            dropExpiredFriendRooms();
            for (FriendRoom candidate : this.friendRooms) {
                if (candidate.code.equals(code)) {
                    room = candidate;
                    break;
                }
            }
            if (room == null) {
                return JoinResult.NO_SUCH_ROOM;
            }
            if (!this.lifecycle.claim(guest.id, PlayerLifecycle.MATCHING)) {
                return JoinResult.BUSY;
            }
            if (!this.lifecycle.transition(room.host.id, PlayerLifecycle.FRIEND_WAITING,
                    PlayerLifecycle.MATCHING)) {
                // Host left/cancelled between our lookup and the claim.
                this.lifecycle.release(guest.id);
                return JoinResult.NO_SUCH_ROOM;
            }
            this.friendRooms.remove(room);
        }
        try {
            MatchRoom match = this.manager.createRoom(room.host, room.hostEndpoint, guest, endpoint, null, true);
            // Log only after creation succeeds; use the stored server code, not the join input.
            this.events.emit("ROOM_JOINED", match.matchId,
                    "code=" + room.code + " host=" + room.host.id + " guest=" + guest.id);
        } catch (RuntimeException e) {
            this.events.emit("ROOM_LAUNCH_FAILED", null,
                    "host=" + room.host.id + " guest=" + guest.id + " class=" + e.getClass().getName());
            this.lifecycle.release(room.host.id);
            this.lifecycle.release(guest.id);
            return JoinResult.NO_SUCH_ROOM;
        }
        return JoinResult.LAUNCHED;
    }

    /** Host cancelled the room (or disconnected). */
    public void cancelFriendRoom(long hostUserId) {
        synchronized (this.lock) {
            this.friendRooms.removeIf(r -> r.host.id == hostUserId);
        }
        this.lifecycle.releaseIf(hostUserId, PlayerLifecycle.FRIEND_WAITING);
    }

    private boolean roomCodeTaken(String code) {
        for (FriendRoom room : this.friendRooms) {
            if (room.code.equals(code)) {
                return true;
            }
        }
        return false;
    }

    private void dropExpiredFriendRooms() {
        long now = System.currentTimeMillis();
        this.friendRooms.removeIf(r -> {
            if (now - r.createdAt >= FRIEND_ROOM_TTL_MS) {
                this.lifecycle.releaseIf(r.host.id, PlayerLifecycle.FRIEND_WAITING);
                return true;
            }
            return false;
        });
    }

    /** Periodic scan; also triggered opportunistically after each enqueue. */
    public void scan() {
        List<Entry[]> pairsToStart = new ArrayList<>();
        List<Entry> botFallbacks = new ArrayList<>();
        synchronized (this.lock) {
            long now = System.currentTimeMillis();
            int step = this.properties.getMatch().getEloBucketStep();
            long botWait = this.properties.getTiming().getBotWaitMs();
            // Oldest-first greedy pairing inside the widened bucket.
            this.queue.sort((a, b) -> Long.compare(a.enqueuedAt, b.enqueuedAt));
            for (int i = 0; i < this.queue.size(); i++) {
                Entry a = this.queue.get(i);
                if (a == null) {
                    continue;
                }
                if (now - a.enqueuedAt >= botWait || a.user.restrictedMatches > 0) {
                    if (this.lifecycle.transition(a.user.id, PlayerLifecycle.QUEUED,
                            PlayerLifecycle.MATCHING)) {
                        botFallbacks.add(a);
                        this.queue.set(i, null);
                    } else {
                        // Left the queue through another path; drop the entry.
                        this.queue.set(i, null);
                    }
                    continue;
                }
                long waited = now - a.enqueuedAt;
                int width = step * bucketMultiplier(waited);
                for (int j = i + 1; j < this.queue.size(); j++) {
                    Entry b = this.queue.get(j);
                    if (b == null) {
                        continue;
                    }
                    if (Math.abs(a.user.elo - b.user.elo) > width) {
                        continue;
                    }
                    if (!this.lifecycle.transition(a.user.id, PlayerLifecycle.QUEUED,
                            PlayerLifecycle.MATCHING)) {
                        this.queue.set(i, null);
                        break;
                    }
                    if (!this.lifecycle.transition(b.user.id, PlayerLifecycle.QUEUED,
                            PlayerLifecycle.MATCHING)) {
                        this.lifecycle.transition(a.user.id, PlayerLifecycle.MATCHING,
                                PlayerLifecycle.QUEUED);
                        this.queue.set(j, null);
                        continue;
                    }
                    pairsToStart.add(new Entry[]{a, b});
                    this.queue.set(i, null);
                    this.queue.set(j, null);
                    break;
                }
            }
            this.queue.removeIf(e -> e == null);
        }
        for (final Entry[] pair : pairsToStart) {
            long wait = System.currentTimeMillis() - pair[0].enqueuedAt;
            this.metrics.pairings.incrementAndGet();
            this.metrics.queueWaitTotalMs.addAndGet(wait);
            this.events.emit("PAIRED", null, "a=" + pair[0].user.id + " b=" + pair[1].user.id
                    + " waited=" + wait);
            this.manager.launchRoom(new Runnable() {
                @Override
                public void run() {
                    try {
                        Matchmaker.this.manager.createRoom(pair[0].user, pair[0].endpoint,
                                pair[1].user, pair[1].endpoint, null);
                    } catch (RuntimeException e) {
                        Matchmaker.this.events.emit("PAIRED_LAUNCH_FAILED", null,
                                "a=" + pair[0].user.id + " b=" + pair[1].user.id
                                        + " class=" + e.getClass().getName());
                        Matchmaker.this.lifecycle.release(pair[0].user.id);
                        Matchmaker.this.lifecycle.release(pair[1].user.id);
                    }
                }
            });
        }
        for (final Entry alone : botFallbacks) {
            long wait = System.currentTimeMillis() - alone.enqueuedAt;
            this.metrics.botFallbacks.incrementAndGet();
            this.metrics.queueWaitTotalMs.addAndGet(wait);
            this.events.emit("BOT_FALLBACK", null,
                    "user=" + alone.user.id + " waited=" + wait);
            if (!this.manager.acceptingMatches()) {
                this.lifecycle.release(alone.user.id);
                continue;
            }
            final String botName = BOT_NAMES[this.random.nextInt(BOT_NAMES.length)];
            log.info("bot fallback for user {} after {}ms wait", alone.user.id,
                    System.currentTimeMillis() - alone.enqueuedAt);
            this.manager.launchRoom(new Runnable() {
                @Override
                public void run() {
                    try {
                        Matchmaker.this.manager.createRoom(alone.user, alone.endpoint, null, null, botName);
                    } catch (RuntimeException e) {
                        Matchmaker.this.events.emit("BOT_LAUNCH_FAILED", null,
                                "user=" + alone.user.id + " class=" + e.getClass().getName());
                        Matchmaker.this.lifecycle.release(alone.user.id);
                    }
                }
            });
        }
    }

    private int bucketMultiplier(long waitedMs) {
        int[] steps = this.properties.getMatch().getBucketWidenSteps();
        int stage = (int) Math.min(waitedMs / 8000L, steps.length - 1);
        return steps[stage];
    }

    public int queueSize() {
        synchronized (this.lock) {
            return this.queue.size();
        }
    }
}
