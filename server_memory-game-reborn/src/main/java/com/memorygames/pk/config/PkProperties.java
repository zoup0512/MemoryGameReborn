package com.memorygames.pk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

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
    private final Season season = new Season();
    private final AntiCheat antiCheat = new AntiCheat();
    private final Practice practice = new Practice();

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
        private int welcomeCoins = 60;

        public int getEntryFee() { return entryFee; }
        public void setEntryFee(int entryFee) { this.entryFee = entryFee; }
        public int getWinCoins() { return winCoins; }
        public void setWinCoins(int winCoins) { this.winCoins = winCoins; }
        public int getDrawCoins() { return drawCoins; }
        public void setDrawCoins(int drawCoins) { this.drawCoins = drawCoins; }
        public int getWelcomeCoins() { return welcomeCoins; }
        public void setWelcomeCoins(int welcomeCoins) { this.welcomeCoins = welcomeCoins; }
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
        /** Practice-vs-bot only; ranked fallback bots retain the normal gap. */
        private long practiceRoundIntermissionMs = 2000;
        /** Extra time after a round's duration before the server force-ends it. */
        private long roundGraceMs = 10000;
        /**
         * Network tolerance after the scoring deadline: level_wins arriving
         * later than duration + this are rejected. Distinct from roundGraceMs,
         * which only absorbs transport for in-flight events and never
         * re-opens scoring.
         */
        private long scoreAcceptGraceMs = 2000;
        /** Queue wait before a bot opponent is substituted. */
        private long botWaitMs = 15000;
        /** Window after match_found for both START presses before refund-cancel. */
        private long acceptTimeoutMs = 30000;
        /** Bounded practice tutorial/countdown window, before gameplay clocks start. */
        private long practicePreparationTimeoutMs = 120000;
        /** Disconnect window before a player is forfeited. */
        private long reconnectWindowMs = 90000;

        public long getMatchFoundDelayMs() { return matchFoundDelayMs; }
        public void setMatchFoundDelayMs(long v) { this.matchFoundDelayMs = v; }
        public long getRoundIntermissionMs() { return roundIntermissionMs; }
        public void setRoundIntermissionMs(long v) { this.roundIntermissionMs = v; }
        public long getPracticeRoundIntermissionMs() { return practiceRoundIntermissionMs; }
        public void setPracticeRoundIntermissionMs(long v) { this.practiceRoundIntermissionMs = v; }
        public long getAcceptTimeoutMs() { return acceptTimeoutMs; }
        public void setAcceptTimeoutMs(long v) { this.acceptTimeoutMs = v; }
        public long getRoundGraceMs() { return roundGraceMs; }
        public void setRoundGraceMs(long v) { this.roundGraceMs = v; }
        public long getScoreAcceptGraceMs() { return scoreAcceptGraceMs; }
        public void setScoreAcceptGraceMs(long v) { this.scoreAcceptGraceMs = v; }
        public long getBotWaitMs() { return botWaitMs; }
        public void setBotWaitMs(long v) { this.botWaitMs = v; }
        public long getPracticePreparationTimeoutMs() { return practicePreparationTimeoutMs; }
        public void setPracticePreparationTimeoutMs(long v) { this.practicePreparationTimeoutMs = v; }
        public long getReconnectWindowMs() { return reconnectWindowMs; }
        public void setReconnectWindowMs(long v) { this.reconnectWindowMs = v; }
    }

    public static class Season {
        /** Season length in ms (30 days; tests use milliseconds). */
        private long durationMs = 30L * 86400000L;

        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long v) { this.durationMs = v; }
    }

    public static class AntiCheat {
        /** Answers needed before distribution checks apply. */
        private int minAnswersForCheck = 10;
        /** suspicion++ when avg interval < floor * this factor. */
        private double speedFactor = 0.7d;
        /** Bot-only matches after this many suspicion strikes. */
        private int suspicionThreshold = 2;
        private int restrictionMatches = 5;

        public int getMinAnswersForCheck() { return minAnswersForCheck; }
        public void setMinAnswersForCheck(int v) { this.minAnswersForCheck = v; }
        public double getSpeedFactor() { return speedFactor; }
        public void setSpeedFactor(double v) { this.speedFactor = v; }
        public int getSuspicionThreshold() { return suspicionThreshold; }
        public void setSuspicionThreshold(int v) { this.suspicionThreshold = v; }
        public int getRestrictionMatches() { return restrictionMatches; }
        public void setRestrictionMatches(int v) { this.restrictionMatches = v; }
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

    /**
     * Practice (play-vs-bot) tuning (docs/bot-battle-prd.md): the five
     * difficulty tiers and the daily paid-win cap. All read on every use,
     * so admin config puts apply without a restart.
     */
    public static class Practice {

        /** Paid practice wins per calendar day; later wins still settle, with 0 coins. */
        private int dailyRewardCap = 5;
        /** Difficulty tiers, bronze first; lookups fall back to the first entry. */
        private List<BotDifficulty> difficulties = new ArrayList<>();

        public Practice() {
            this.difficulties.add(new BotDifficulty("BRONZE", 800, 0.60d, 1.5d, 8));
            this.difficulties.add(new BotDifficulty("SILVER", 1000, 0.68d, 1.25d, 10));
            this.difficulties.add(new BotDifficulty("GOLD", 1250, 0.78d, 1.0d, 14));
            this.difficulties.add(new BotDifficulty("DIAMOND", 1500, 0.86d, 0.85d, 20));
            this.difficulties.add(new BotDifficulty("MASTER", 1800, 0.92d, 0.72d, 30));
        }

        public int getDailyRewardCap() { return dailyRewardCap; }
        public void setDailyRewardCap(int v) { this.dailyRewardCap = v; }
        public List<BotDifficulty> getDifficulties() { return difficulties; }
        public void setDifficulties(List<BotDifficulty> v) { this.difficulties = v; }
    }

    /** One practice bot tier (docs/bot-battle-prd.md section 4). */
    public static class BotDifficulty {

        private String id;
        /** Bot rating; drives the VS header badge and the stats-board W/L/D derivation. */
        private int elo;
        /** Base hit rate per answered level (rubber-banding drifts around it). */
        private double accuracy;
        /** Multiplier over GameCatalog.botLevelIntervalMs. */
        private double pace;
        /** Coins for a won practice match (draws pay half). */
        private int winCoins;

        public BotDifficulty() {
        }

        public BotDifficulty(String id, int elo, double accuracy, double pace, int winCoins) {
            this.id = id;
            this.elo = elo;
            this.accuracy = accuracy;
            this.pace = pace;
            this.winCoins = winCoins;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public int getElo() { return elo; }
        public void setElo(int elo) { this.elo = elo; }
        public double getAccuracy() { return accuracy; }
        public void setAccuracy(double accuracy) { this.accuracy = accuracy; }
        public double getPace() { return pace; }
        public void setPace(double pace) { this.pace = pace; }
        public int getWinCoins() { return winCoins; }
        public void setWinCoins(int winCoins) { this.winCoins = winCoins; }
    }

    public Economy getEconomy() { return economy; }
    public Rating getRating() { return rating; }
    public Timing getTiming() { return timing; }
    public Match getMatch() { return match; }
    public Season getSeason() { return season; }
    public AntiCheat getAntiCheat() { return antiCheat; }
    public Practice getPractice() { return practice; }

    /** Difficulty lookup by id (case-insensitive); unknown ids fall back to the first tier. */
    public BotDifficulty practiceDifficulty(String id) {
        List<BotDifficulty> tiers = this.practice.getDifficulties();
        if (tiers.isEmpty()) {
            return new BotDifficulty("GOLD", 1250, 0.78d, 1.0d, 14);
        }
        for (BotDifficulty tier : tiers) {
            if (tier.getId() != null && tier.getId().equalsIgnoreCase(id)) {
                return tier;
            }
        }
        return tiers.get(0);
    }
    public long getRoundDurationOverrideMs() { return roundDurationOverrideMs; }
    public void setRoundDurationOverrideMs(long v) { this.roundDurationOverrideMs = v; }
    public long getMinLevelIntervalOverrideMs() { return minLevelIntervalOverrideMs; }
    public void setMinLevelIntervalOverrideMs(long v) { this.minLevelIntervalOverrideMs = v; }
    public long getBotLevelIntervalOverrideMs() { return botLevelIntervalOverrideMs; }
    public void setBotLevelIntervalOverrideMs(long v) { this.botLevelIntervalOverrideMs = v; }
}
