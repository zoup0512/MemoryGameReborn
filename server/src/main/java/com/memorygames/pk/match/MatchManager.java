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
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Owns the live match rooms and the shared scheduler. Live state is
 * deliberately in-memory only (M1; Redis-backed registry per PRD is an M2
 * step) — a server restart forfeits in-flight matches, which the client
 * tolerates through its reconnect window.
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
    private final ScheduledThreadPoolExecutor scheduler;
    private final MatchRecordRepository records;
    private final SeasonService seasons;
    private final EventRing events;
    private final AdminMetrics metrics;

    public MatchManager(GameCatalog catalog, UserService users, Rating rating,
                        PkProperties properties, MatchRecordRepository records, SeasonService seasons,
                        EventRing events, AdminMetrics metrics) {
        this.catalog = catalog;
        this.users = users;
        this.rating = rating;
        this.properties = properties;
        this.records = records;
        this.seasons = seasons;
        this.events = events;
        this.metrics = metrics;
        this.scheduler = new ScheduledThreadPoolExecutor(2, r -> {
            Thread thread = new Thread(r, "pk-room");
            thread.setDaemon(true);
            return thread;
        });
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

    public MatchRoom createRoom(UserAccount userA, PlayerEndpoint endpointA,
                                UserAccount userB, PlayerEndpoint endpointB,
                                String botNickname) {
        return createRoom(userA, endpointA, userB, endpointB, botNickname, false);
    }

    public MatchRoom createRoom(UserAccount userA, PlayerEndpoint endpointA,
                                UserAccount userB, PlayerEndpoint endpointB,
                                String botNickname, boolean friendMode) {
        MatchRoom room = new MatchRoom(userA, endpointA, userB, endpointB, botNickname, friendMode,
                this.catalog, this.users, this.rating, this.properties, this.scheduler,
                settled -> onSettled(settled), this.records, this.seasons, this.events, this.metrics);
        this.rooms.put(room.matchId, room);
        if (userA != null) {
            this.roomByUser.put(userA.id, room.matchId);
        }
        if (userB != null) {
            this.roomByUser.put(userB.id, room.matchId);
        }
        room.start();
        return room;
    }

    /** Routes a userId to its live room, if any. */
    public MatchRoom roomFor(long userId) {
        String matchId = this.roomByUser.get(userId);
        return matchId != null ? this.rooms.get(matchId) : null;
    }

    private void onSettled(MatchRoom room) {
        this.rooms.remove(room.matchId);
        for (long userId : room.userIds()) {
            if (userId >= 0) {
                this.roomByUser.remove(userId, room.matchId);
            }
        }
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

    @PreDestroy
    public void shutdown() {
        this.scheduler.shutdownNow();
    }
}
