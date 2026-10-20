package com.memorygames.pk.match;

import com.memorygames.pk.admin.AdminMetrics;
import com.memorygames.pk.admin.EventRing;
import com.memorygames.pk.config.PkProperties;
import com.memorygames.pk.game.GameCatalog;
import com.memorygames.pk.rating.Rating;
import com.memorygames.pk.season.SeasonService;
import com.memorygames.pk.user.UserAccount;
import com.memorygames.pk.user.UserService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Owns the live match rooms, the shared timer pool and the bounded DB pool
 * (P2-4): timer threads only fire domain events; every room creation (entry
 * fee) and settlement transaction runs on {@code dbExecutor}, so a slow
 * database can no longer stall every room's round clock at once. Live state
 * is deliberately in-memory only — a server restart voids in-flight matches
 * (refund + idempotent record) instead of silently keeping the coins.
 */
@Component
public class MatchManager {

    private static final Logger log = LoggerFactory.getLogger(MatchManager.class);

    private final Map<String, MatchRoom> rooms = new ConcurrentHashMap<>();
    private final Map<Long, String> roomByUser = new ConcurrentHashMap<>();

    private final GameCatalog catalog;
    private final UserService users;
    private final Rating rating;
    private final PkProperties properties;
    private final SettlementService settlement;
    private final PlayerLifecycle lifecycle;
    private final ScheduledThreadPoolExecutor scheduler;
    private final ThreadPoolExecutor dbExecutor;
    private final SeasonService seasons;
    private final EventRing events;
    private final AdminMetrics metrics;

    /** Flips to true during graceful shutdown; new matches are refused. */
    private volatile boolean shuttingDown;

    public MatchManager(GameCatalog catalog, UserService users, Rating rating,
                        PkProperties properties, SettlementService settlement,
                        PlayerLifecycle lifecycle, SeasonService seasons,
                        EventRing events, AdminMetrics metrics) {
        this.catalog = catalog;
        this.users = users;
        this.rating = rating;
        this.properties = properties;
        this.settlement = settlement;
        this.lifecycle = lifecycle;
        this.seasons = seasons;
        this.events = events;
        this.metrics = metrics;
        this.scheduler = new ScheduledThreadPoolExecutor(2, r -> {
            Thread thread = new Thread(r, "pk-room");
            thread.setDaemon(true);
            return thread;
        });
        this.dbExecutor = new ThreadPoolExecutor(
                1, 2, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(200), r -> {
            Thread thread = new Thread(r, "pk-db");
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
        // Opportunistic queue scans on top of the enqueue-triggered scans.
        this.scheduler.scheduleWithFixedDelay(() -> {
            try {
                this.scanHook.run();
            } catch (RuntimeException e) {
                log.warn("matchmaker scan failed: {}", e.getMessage());
            }
        }, 1000, 1000, TimeUnit.MILLISECONDS);
    }

    /** Late-bound to break the Matchmaker <-> MatchManager cycle. */
    private Runnable scanHook = () -> {
    };

    public void bindMatchmaker(Matchmaker matchmaker) {
        this.scanHook = matchmaker::scan;
    }

    /** False once graceful shutdown started (readiness gate). */
    public boolean acceptingMatches() {
        return !this.shuttingDown;
    }

    /**
     * Room creations (pairings, bot fallbacks) run on the DB pool: their
     * start() charges entry fees, which must stay off timer threads. A
     * saturated pool degrades to the caller thread rather than dropping
     * the match.
     */
    public void launchRoom(Runnable creation) {
        try {
            this.dbExecutor.execute(creation);
        } catch (RejectedExecutionException saturated) {
            creation.run();
        }
    }

    public MatchRoom createRoom(UserAccount userA, PlayerEndpoint endpointA,
                                UserAccount userB, PlayerEndpoint endpointB,
                                String botNickname) {
        return createRoom(userA, endpointA, userB, endpointB, botNickname, false);
    }

    public MatchRoom createRoom(UserAccount userA, PlayerEndpoint endpointA,
                                UserAccount userB, PlayerEndpoint endpointB,
                                String botNickname, boolean friendMode) {
        // Ranked fallback bots shadow their human opponent's rating; friend
        // rooms always seat two humans, so the bot elo never applies there.
        int botElo = userB == null && userA != null ? userA.elo : 1000;
        MatchRoom room = new MatchRoom(userA, endpointA, userB, endpointB, botNickname,
                friendMode ? MatchRoom.MODE_FRIEND : MatchRoom.MODE_RANKED, botElo,
                null, 1.0d,
                this.catalog, this.users, this.rating, this.properties,
                MatchTerms.forVersus(this.properties),
                this.scheduler, this.dbExecutor,
                this.roomCallbacks, this.settlement, this.seasons, this.events, this.metrics);
        return register(room, userA, userB);
    }

    /**
     * Explicit play-vs-bot duel (docs/bot-battle-prd.md): the human is seat 0,
     * the difficulty tier defines the bot's rating, accuracy and pacing. No
     * queueing — the match_found frame leaves immediately.
     */
    public MatchRoom createPracticeRoom(UserAccount user, PlayerEndpoint endpoint,
                                        PkProperties.BotDifficulty difficulty) {
        MatchRoom room = new MatchRoom(user, endpoint, null, null,
                difficulty.getId() == null ? "Practice Bot"
                        : difficulty.getId().charAt(0) + difficulty.getId().substring(1).toLowerCase(java.util.Locale.US) + " Bot",
                MatchRoom.MODE_PRACTICE, difficulty.getElo(),
                difficulty.getAccuracy(), difficulty.getPace(),
                this.catalog, this.users, this.rating, this.properties,
                MatchTerms.forPractice(this.properties, difficulty),
                this.scheduler, this.dbExecutor,
                this.roomCallbacks, this.settlement, this.seasons, this.events, this.metrics);
        return register(room, user, null);
    }

    /**
     * Seat reservation is a putIfAbsent per user (P1-2): callers hold the
     * MATCHING lifecycle claim, and this is the final atomic gate — if a
     * second room ever races in for the same user, that room is refused
     * instead of silently overwriting roomByUser and orphaning the first.
     */
    private MatchRoom register(MatchRoom room, UserAccount userA, UserAccount userB) {
        UserAccount[] seated = {userA, userB};
        for (UserAccount user : seated) {
            if (user != null && this.roomByUser.putIfAbsent(user.id, room.matchId) != null) {
                // Roll back only the seats this registration actually took;
                // the colliding user's claim belongs to their live room and
                // must stay untouched. Callers release their own MATCHING
                // claims when this throws.
                for (UserAccount taken : seated) {
                    if (taken != null && taken.id != user.id) {
                        releasePlayer(room, taken.id);
                    }
                }
                throw new IllegalStateException("user " + user.id + " already seated in a match");
            }
        }
        this.rooms.put(room.matchId, room);
        for (UserAccount user : seated) {
            if (user != null) {
                this.lifecycle.transition(user.id, PlayerLifecycle.MATCHING, PlayerLifecycle.IN_MATCH);
            }
        }
        try {
            room.start();
        } catch (RuntimeException e) {
            // Escrow/charge failure: the room must not linger as a live-but-
            // started-nothing ghost; unwind the registration and rethrow so
            // the caller releases its lifecycle claims.
            this.rooms.remove(room.matchId);
            for (UserAccount user : seated) {
                if (user != null) releasePlayer(room, user.id);
            }
            throw e;
        }
        return room;
    }

    /** Routes a userId to its live room, if any. */
    public MatchRoom roomFor(long userId) {
        String matchId = this.roomByUser.get(userId);
        return matchId != null ? this.rooms.get(matchId) : null;
    }

    private final MatchRoom.SettlementCallback roomCallbacks = new MatchRoom.SettlementCallback() {
        @Override public void onSettled(MatchRoom room) { MatchManager.this.onSettled(room); }
        @Override public void onPlayerReleased(MatchRoom room, long userId) {
            releasePlayer(room, userId);
        }
    };

    /**
     * Release only the named room's seat. Keep its mapping present until the
     * lifecycle release completes, so no successor room can register between
     * ownership verification and release. Stale callbacks cannot free a new
     * queue/friend-room/match claim after a player has actively left.
     */
    public void releasePlayer(MatchRoom room, long userId) {
        this.roomByUser.computeIfPresent(userId, (id, owner) -> {
            if (!room.matchId.equals(owner)) return owner;
            this.lifecycle.release(id);
            return null;
        });
    }

    private void onSettled(MatchRoom room) {
        for (long userId : room.userIds()) {
            if (userId >= 0) releasePlayer(room, userId);
        }
        // A missing room must imply all its seats have already detached:
        // tagged resign retries can then be acknowledged without a room lookup.
        this.rooms.remove(room.matchId, room);
    }

    public int activeRooms() {
        return this.rooms.size();
    }

    /** Live match snapshots for the admin surface (newest first). */
    public java.util.List<MatchRoom.Snapshot> activeSnapshots() {
        java.util.List<MatchRoom.Snapshot> out = new java.util.ArrayList<>(this.rooms.size());
        for (MatchRoom room : this.rooms.values()) {
            out.add(room.adminSnapshot());
        }
        out.sort((a, b) -> Long.compare(b.roundEndsInMs, a.roundEndsInMs));
        return out;
    }

    public MatchRoom roomById(String matchId) {
        return this.rooms.get(matchId);
    }

    public EventRing events() {
        return this.events;
    }

    /** Queue depth of the DB pool (saturation signal for metrics). */
    public int dbQueueDepth() {
        return this.dbExecutor.getQueue().size();
    }

    /**
     * Graceful shutdown (P1-6): stop the scan loop, void every unsettled
     * room (refund + idempotent void record through the settlement
     * transaction on the DB pool), then drain the DB pool before the
     * timers so in-flight settlement transactions can commit. Entry fees
     * already charged are therefore never silently lost to a restart.
     */
    @PreDestroy
    public void shutdown() {
        this.shuttingDown = true;
        this.scanHook = () -> {
        };
        MatchRoom[] live = this.rooms.values().toArray(new MatchRoom[0]);
        for (MatchRoom room : live) {
            try {
                room.adminTerminate();
            } catch (RuntimeException e) {
                log.error("shutdown void of match {} failed: {}", room.matchId, e.getMessage());
            }
        }
        this.dbExecutor.shutdown();
        try {
            if (!this.dbExecutor.awaitTermination(15, TimeUnit.SECONDS)) {
                this.dbExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            this.dbExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        this.scheduler.shutdown();
        try {
            if (!this.scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                this.scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            this.scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("match manager shut down; {} live rooms were voided", live.length);
    }
}
