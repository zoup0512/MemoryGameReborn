package com.memorygames.pk.game;

import com.memorygames.pk.config.PkProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Launch-pool game directory with the per-game round durations (matching
 * what the client game activities actually run in online mode) and the
 * online scoring table, ported verbatim from the client's
 * Game1MemoryGridActivity.updateOnlineScore() so the server can recompute
 * the authoritative score from the level-win stream.
 */
@Component
public class GameCatalog {

    public static class GameSpec {
        public final String gameId;
        /** Round duration in online mode (what the client game itself runs). */
        public final long durationMs;
        /** Per-level base score (updateOnlineScore switch). */
        public final int baseScore;
        /** Games "14"/"15"/"19" add no level bonus on the client. */
        public final boolean flatScore;
        /** Minimum plausible interval between level wins (anti-cheat floor). */
        public final long minLevelIntervalMs;
        /** Nominal interval a bot needs per level. */
        public final long botLevelIntervalMs;

        GameSpec(String gameId, long durationMs, int baseScore, boolean flatScore,
                 long minLevelIntervalMs, long botLevelIntervalMs) {
            this.gameId = gameId;
            this.durationMs = durationMs;
            this.baseScore = baseScore;
            this.flatScore = flatScore;
            this.minLevelIntervalMs = minLevelIntervalMs;
            this.botLevelIntervalMs = botLevelIntervalMs;
        }

        /** Score awarded for winning the given level (client parity). */
        public int levelScore(int level) {
            return flatScore ? baseScore : baseScore + level * 2;
        }
    }

    private final PkProperties properties;
    private final Map<String, GameSpec> games = new LinkedHashMap<>();

    public GameCatalog(PkProperties properties) {
        this.properties = properties;
        // gameId -> (duration, base, flat?, human floor, bot interval)
        // Durations mirror the client: Game1 80s base online timer, Game5/11
        // 60s, Game16/20 their own 30s timers, Game23 its 45s online variant.
        // minLevelIntervalMs = anti-cheat floor below the fastest plausible
        // human pace per game (e.g. Stroop answers ~0.7s are legitimate).
        add(new GameSpec("0", 80000, 10, false, 1800, 4000));  // Memory Grid
        add(new GameSpec("4", 60000, 10, false, 2500, 5000));  // Count'em All
        add(new GameSpec("10", 60000, 10, false, 1000, 3000)); // Correctly?
        add(new GameSpec("15", 30000, 8, true, 700, 2000));    // Like Previous?
        add(new GameSpec("19", 30000, 8, true, 600, 1500));    // Colors
        add(new GameSpec("22", 45000, 0, false, 400, 1000));   // Schultz Tables
    }

    private void add(GameSpec spec) {
        this.games.put(spec.gameId, spec);
    }

    public GameSpec byId(String gameId) {
        return this.games.get(gameId);
    }

    public long durationOf(GameSpec spec) {
        long override = this.properties.getRoundDurationOverrideMs();
        return override > 0 ? override : spec.durationMs;
    }

    /** Anti-cheat floor between level wins; overridable for accelerated tests. */
    public long minLevelIntervalOf(GameSpec spec) {
        long override = this.properties.getMinLevelIntervalOverrideMs();
        return override > 0 ? override : spec.minLevelIntervalMs;
    }

    /** Bot pacing per level; overridable for accelerated tests. */
    public long botLevelIntervalOf(GameSpec spec) {
        long override = this.properties.getBotLevelIntervalOverrideMs();
        return override > 0 ? override : spec.botLevelIntervalMs;
    }

    /**
     * Picks the round plan for a match: {@code count} distinct games from the
     * pool (or repeated, should the pool ever be smaller) with one seed per
     * round. Derived from the match seed, so both sides replay the same plan.
     */
    public List<ProtocolRound> planRounds(long matchSeed, int count) {
        List<GameSpec> pool = new ArrayList<>(this.games.values());
        Collections.shuffle(pool, new Random(matchSeed));
        Random random = new Random(matchSeed ^ 0x5DEECE66DL);
        List<ProtocolRound> rounds = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            GameSpec spec = pool.get(i % pool.size());
            rounds.add(new ProtocolRound(spec.gameId, durationOf(spec), random.nextLong()));
        }
        return rounds;
    }

    /** Round description as it appears on the wire. */
    public static class ProtocolRound {
        public final String gameId;
        public final int durationMs;
        public final long seed;

        public ProtocolRound(String gameId, long durationMs, long seed) {
            this.gameId = gameId;
            this.durationMs = (int) durationMs;
            this.seed = seed;
        }
    }
}
