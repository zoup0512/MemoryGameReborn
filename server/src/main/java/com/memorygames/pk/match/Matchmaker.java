package com.memorygames.pk.match;

import com.memorygames.pk.config.PkProperties;
import com.memorygames.pk.protocol.ProtocolCodec;
import com.memorygames.pk.user.UserAccount;
import com.memorygames.pk.user.UserService;
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
 */
@Component
public class Matchmaker {

    private static final Logger log = LoggerFactory.getLogger(Matchmaker.class);
    private static final String[] BOT_NAMES = {
            "QuickMind", "Brainy Owl", "FastFingers", "SharpEye", "CleverFox",
            "NeuroNinja", "PuzzlePanda", "ThinkTank", "SmartShark", "LogicLion"
    };

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

    private final MatchManager manager;
    private final UserService users;
    private final PkProperties properties;
    private final Random random = new Random();

    public Matchmaker(MatchManager manager, UserService users, PkProperties properties) {
        this.manager = manager;
        this.users = users;
        this.properties = properties;
    }

    /** Called by the WS gateway on match_join. */
    public boolean enqueue(UserAccount user, PlayerEndpoint endpoint) {
        if (user.balance < this.properties.getEconomy().getEntryFee()) {
            return false;
        }
        // A user already seated in a live match must never (re)queue: their
        // frames would fork a second, orphan match pushing events into the
        // same socket (observed in the 2026-10-04 device test).
        if (this.manager.roomFor(user.id) != null) {
            return true; // already playing; treat as accepted no-op
        }
        synchronized (this.lock) {
            for (Entry entry : this.queue) {
                if (entry.user.id.equals(user.id)) {
                    return true; // already queued
                }
            }
            this.queue.add(new Entry(user, endpoint));
        }
        scan();
        return true;
    }

    /** Called by the WS gateway on match_cancel. */
    public boolean cancel(long userId) {
        synchronized (this.lock) {
            Iterator<Entry> it = this.queue.iterator();
            while (it.hasNext()) {
                Entry entry = it.next();
                if (entry.user.id == userId) {
                    it.remove();
                    entry.endpoint.send(ProtocolCodec.encode(ProtocolCodec.T_MATCH_CANCELLED, new Object()));
                    return true;
                }
            }
        }
        return false;
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
                if (now - a.enqueuedAt >= botWait) {
                    botFallbacks.add(a);
                    this.queue.set(i, null);
                    continue;
                }
                long waited = now - a.enqueuedAt;
                int width = step * bucketMultiplier(waited);
                for (int j = i + 1; j < this.queue.size(); j++) {
                    Entry b = this.queue.get(j);
                    if (b == null) {
                        continue;
                    }
                    if (Math.abs(a.user.elo - b.user.elo) <= width) {
                        pairsToStart.add(new Entry[]{a, b});
                        this.queue.set(i, null);
                        this.queue.set(j, null);
                        break;
                    }
                }
            }
            this.queue.removeIf(e -> e == null);
        }
        for (Entry[] pair : pairsToStart) {
            this.manager.createRoom(pair[0].user, pair[0].endpoint, pair[1].user, pair[1].endpoint, null);
        }
        for (Entry alone : botFallbacks) {
            if (this.manager.roomFor(alone.user.id) != null) {
                // User reached a live match through another path while the
                // stale entry waited out the bot timer — drop the entry.
                continue;
            }
            String botName = BOT_NAMES[this.random.nextInt(BOT_NAMES.length)];
            log.info("bot fallback for user {} after {}ms wait", alone.user.id,
                    System.currentTimeMillis() - alone.enqueuedAt);
            this.manager.createRoom(alone.user, alone.endpoint, null, null, botName);
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
