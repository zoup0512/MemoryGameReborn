package com.memorygames.pk.season;

import com.memorygames.pk.config.PkProperties;
import com.memorygames.pk.user.UserAccount;
import com.memorygames.pk.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Season lifecycle (PRD F-13): fixed-length ranked windows. When a season
 * expires, the next settle/boot call finalizes it and soft-resets every
 * account's ELO halfway toward the initial value, then opens the next
 * season. Cheap and safe on a single instance; no cron needed.
 */
@Service
public class SeasonService {

    private static final Logger log = LoggerFactory.getLogger(SeasonService.class);

    private final SeasonRepository seasons;
    private final UserRepository users;
    private final PkProperties properties;

    public SeasonService(SeasonRepository seasons, UserRepository users, PkProperties properties) {
        this.seasons = seasons;
        this.users = users;
        this.properties = properties;
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
    @Transactional
    public Snapshot activeSeason() {
        Season current = this.seasons.findFirstByFinalizedFalseOrderByStartAtDesc().orElse(null);
        long now = System.currentTimeMillis();
        long duration = this.properties.getSeason().getDurationMs();
        if (current == null) {
            int nextIndex = this.seasons.findFirstByFinalizedTrueOrderByIndexDesc()
                    .map(s -> s.index + 1).orElse(1);
            current = new Season();
            current.index = nextIndex;
            current.startAt = now;
            current.endAt = now + duration;
            current.finalized = false;
            this.seasons.save(current);
            log.info("season {} opened ({} ms)", nextIndex, duration);
        } else if (now >= current.endAt) {
            current.finalized = true;
            this.seasons.save(current);
            softResetAll();
            Season next = new Season();
            next.index = current.index + 1;
            next.startAt = now;
            next.endAt = now + duration;
            next.finalized = false;
            this.seasons.save(next);
            log.info("season {} finalized; season {} opened with ELO soft reset",
                    current.index, next.index);
            current = next;
        }
        return new Snapshot(current.index, current.endAt);
    }

    /** ELO halves its distance to the initial rating; floors at 100. */
    private void softResetAll() {
        int initial = this.properties.getRating().getInitialElo();
        List<UserAccount> all = this.users.findAll();
        for (UserAccount account : all) {
            int reset = initial + (account.elo - initial) / 2;
            account.elo = Math.max(100, reset);
            account.updatedAt = System.currentTimeMillis();
        }
        this.users.saveAll(all);
        log.info("ELO soft reset applied to {} accounts", all.size());
    }
}
