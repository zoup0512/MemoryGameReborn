package com.cube.memorygames.api.online;

final class OnlineRankingSummary {
    final String nickname;
    final int elo;
    final int tier;
    final int wins;
    final int losses;
    final int draws;
    final int seasonIndex;
    final long seasonEndsAt;

    private OnlineRankingSummary(String nickname, int elo, int tier, int wins, int losses,
                                 int draws, int seasonIndex, long seasonEndsAt) {
        this.nickname = nickname;
        this.elo = elo;
        this.tier = tier;
        this.wins = Math.max(0, wins);
        this.losses = Math.max(0, losses);
        this.draws = Math.max(0, draws);
        this.seasonIndex = seasonIndex;
        this.seasonEndsAt = seasonEndsAt;
    }

    static OnlineRankingSummary from(OnlineMessages.AuthOk live, AccountManager.UserProfile cached) {
        if (live != null && live.userId > 0
                && (cached == null || cached.userId <= 0 || cached.userId == live.userId)) {
            return new OnlineRankingSummary(live.nickname, live.elo, live.rank, live.wins,
                    live.losses, live.draws, live.seasonIndex, live.seasonEndsAt);
        }
        if (cached != null && cached.userId > 0) {
            // REST profiles have no authoritative tier or season information.
            return new OnlineRankingSummary(cached.nickname, cached.elo, -1, cached.wins,
                    cached.losses, cached.draws, 0, 0L);
        }
        return null;
    }

    static int statColumns(int widthPx, int baseColumnPx, float fontScale) {
        float minimum = baseColumnPx * Math.max(1.0f, fontScale);
        if (widthPx >= minimum * 4) {
            return 4;
        }
        return widthPx >= minimum * 2 ? 2 : 1;
    }

    int winRate() {
        long total = (long) wins + losses + draws;
        return total == 0 ? 0 : (int) Math.round(wins * 100.0 / total);
    }

    boolean hasSeason() {
        return seasonIndex > 0 && seasonEndsAt > 0L;
    }

    long daysLeft(long now) {
        return Math.max(0L, (seasonEndsAt - now) / 86400000L);
    }
}
