package com.memorygames.pk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunable PK parameters. Production values follow docs/online-pk-prd.md;
 * the integration test overrides them with accelerated timings.
 */
@ConfigurationProperties(prefix = "pk")
public class PkProperties {

    private final Economy economy = new Economy();
    private final Rating rating = new Rating();
    private final Timing timing = new Timing();
    private final Match match = new Match();

    /** When > 0, replaces every game's round duration (tests only). */
    private long roundDurationOverrideMs = 0;
    /** When > 0, replaces the anti-cheat min interval between level wins (tests only). */
    private long minLevelIntervalOverrideMs = 0;
    /** When > 0, replaces the bot per-level interval (tests only). */
    private long botLevelIntervalOverrideMs = 0;

    public static class Economy {
        private int entryFee = 30;
        private int winCoins = 50;
        private int drawCoins = 15;

        public int getEntryFee() { return entryFee; }
        public void setEntryFee(int entryFee) { this.entryFee = entryFee; }
        public int getWinCoins() { return winCoins; }
        public void setWinCoins(int winCoins) { this.winCoins = winCoins; }
        public int getDrawCoins() { return drawCoins; }
        public void setDrawCoins(int drawCoins) { this.drawCoins = drawCoins; }
    }

    public static class Rating {
        private int initialElo = 1000;
        private int kFactor = 32;

        public int getInitialElo() { return initialElo; }
        public void setInitialElo(int initialElo) { this.initialElo = initialElo; }
        public int getKFactor() { return kFactor; }
        public void setKFactor(int kFactor) { this.kFactor = kFactor; }
    }

    public static class Timing {
        /** Delay between match_found and the first round_start. */
        private long matchFoundDelayMs = 3000;
        /** Gap between round_end and the next round_start. */
        private long roundIntermissionMs = 6000;
        /** Extra time after a round's duration before the server force-ends it. */
        private long roundGraceMs = 10000;
        /** Queue wait before a bot opponent is substituted. */
        private long botWaitMs = 15000;
        /** Disconnect window before a player is forfeited. */
        private long reconnectWindowMs = 90000;

        public long getMatchFoundDelayMs() { return matchFoundDelayMs; }
        public void setMatchFoundDelayMs(long v) { this.matchFoundDelayMs = v; }
        public long getRoundIntermissionMs() { return roundIntermissionMs; }
        public void setRoundIntermissionMs(long v) { this.roundIntermissionMs = v; }
        public long getRoundGraceMs() { return roundGraceMs; }
        public void setRoundGraceMs(long v) { this.roundGraceMs = v; }
        public long getBotWaitMs() { return botWaitMs; }
        public void setBotWaitMs(long v) { this.botWaitMs = v; }
        public long getReconnectWindowMs() { return reconnectWindowMs; }
        public void setReconnectWindowMs(long v) { this.reconnectWindowMs = v; }
    }

    public static class Match {
        private int roundsPerMatch = 5;
        /** ELO bucket widths for matchmaking ([0,w,2w,...]). */
        private int eloBucketStep = 100;
        /** Bucket widening steps while waiting (multiplied with eloBucketStep). */
        private int[] bucketWidenSteps = {1, 2, 4};

        public int getRoundsPerMatch() { return roundsPerMatch; }
        public void setRoundsPerMatch(int v) { this.roundsPerMatch = v; }
        public int getEloBucketStep() { return eloBucketStep; }
        public void setEloBucketStep(int v) { this.eloBucketStep = v; }
        public int[] getBucketWidenSteps() { return bucketWidenSteps; }
        public void setBucketWidenSteps(int[] v) { this.bucketWidenSteps = v; }
    }

    public Economy getEconomy() { return economy; }
    public Rating getRating() { return rating; }
    public Timing getTiming() { return timing; }
    public Match getMatch() { return match; }
    public long getRoundDurationOverrideMs() { return roundDurationOverrideMs; }
    public void setRoundDurationOverrideMs(long v) { this.roundDurationOverrideMs = v; }
    public long getMinLevelIntervalOverrideMs() { return minLevelIntervalOverrideMs; }
    public void setMinLevelIntervalOverrideMs(long v) { this.minLevelIntervalOverrideMs = v; }
    public long getBotLevelIntervalOverrideMs() { return botLevelIntervalOverrideMs; }
    public void setBotLevelIntervalOverrideMs(long v) { this.botLevelIntervalOverrideMs = v; }
}
