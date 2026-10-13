package com.memorygames.pk.season;

import com.memorygames.pk.config.PkProperties;
import com.memorygames.pk.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Season lifecycle (PRD F-13): fixed-length ranked windows. When a season
 * expires, the next settle/auth call finalizes it and soft-resets every
 * account's ELO halfway toward the initial value, then opens the next
 * season. Cheap and safe on a single instance; no cron needed.
 *
 * Rollover is race-proof (P2-3): the method serializes in-process, the
 * finalize is a conditional UPDATE (the loser sees row count 0 and re-reads
 * instead of resetting ELO twice), and the unique(activeFlag) constraint
 * keeps exactly one active season even across instances.
 */
@Service
public class SeasonService {

    private static final Logger log = LoggerFactory.getLogger(SeasonService.class);

    private final SeasonRepository seasons;
    private final UserRepository users;
    private final PkProperties properties;
    private final TransactionTemplate tx;

    public SeasonService(SeasonRepository seasons, UserRepository users,
                         PkProperties properties, PlatformTransactionManager transactionManager) {
        this.seasons = seasons;
        this.users = users;
        this.properties = properties;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public static class Snapshot {

        public final int index;
        public final long endAt;

        Snapshot(int index, long endAt) {
            this.index = index;
            this.endAt = endAt;
        }

        public long daysLeft() {
            return Math.max(0L, (this.endAt - System.currentTimeMillis()) / 86400000L);
        }
    }

    /**
     * Cached snapshot for read paths (auth). Every WebSocket auth used to
     * hit the database for the season row; concurrent settles holding row
     * locks could starve the connection pool and hang auths (LAN incident
     * 2026-10-04). Cache for a minute; writes still go through
     * {@link #activeSeason()}.
     */
    private volatile Snapshot cached;
    private volatile long cachedAt;

    public Snapshot snapshot() {
        Snapshot value = this.cached;
        long at = this.cachedAt;
        if (value != null && System.currentTimeMillis() - at < 60000L) {
            return value;
        }
        try {
            this.cached = value = activeSeason();
            this.cachedAt = System.currentTimeMillis();
        } catch (RuntimeException e) {
            if (value == null) {
                value = new Snapshot(1, System.currentTimeMillis() + 86400000L);
            }
        }
        return value;
    }

    /** Finalizes an expired season if needed and returns the active one. */
    public synchronized Snapshot activeSeason() {
        try {
            Snapshot snapshot = this.tx.execute(status -> rolloverIfNeeded());
            if (snapshot != null) {
                return snapshot;
            }
        } catch (DataIntegrityViolationException raced) {
            // Lost the unique(activeFlag) race against another instance; the
            // winner's season (and reset, if any) stands.
            log.info("season open lost a race; re-reading the winner");
        }
        Season winner = this.seasons.findFirstByActiveFlagOrderByIdDesc(1).orElse(null);
        if (winner != null) {
            return new Snapshot(winner.index, winner.endAt);
        }
        // Degenerate empty table (should not happen): open one more attempt
        // inside a fresh transaction.
        return this.tx.execute(status -> rolloverIfNeeded());
    }

    private Snapshot rolloverIfNeeded() {
        Season current = this.seasons.findFirstByActiveFlagOrderByIdDesc(1).orElse(null);
        long now = System.currentTimeMillis();
        long duration = this.properties.getSeason().getDurationMs();
        if (current == null) {
            int nextIndex = this.seasons.findFirstByFinalizedTrueOrderByIndexDesc()
                    .map(s -> s.index + 1).orElse(1);
            current = open(nextIndex, now, duration);
            log.info("season {} opened ({} ms)", nextIndex, duration);
        } else if (now >= current.endAt) {
            int finalized = this.seasons.finalizeIfActive(current.id);
            if (finalized == 0) {
                // Another thread/instance already rolled this season over.
                Season winner = this.seasons.findFirstByActiveFlagOrderByIdDesc(1).orElse(current);
                return new Snapshot(winner.index, winner.endAt);
            }
            int reset = softResetAll(now);
            Season next = open(current.index + 1, now, duration);
            log.info("season {} finalized; season {} opened with ELO soft reset for {} accounts",
                    current.index, next.index, reset);
            current = next;
        }
        return new Snapshot(current.index, current.endAt);
    }

    private Season open(int index, long now, long duration) {
        Season season = new Season();
        season.index = index;
        season.startAt = now;
        season.endAt = now + duration;
        season.finalized = false;
        season.activeFlag = 1;
        return this.seasons.saveAndFlush(season);
    }

    /** ELO halves its distance to the initial rating; floors at 100. */
    private int softResetAll(long now) {
        int initial = this.properties.getRating().getInitialElo();
        return this.users.softResetElo(initial, now);
    }
}
