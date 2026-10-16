package com.cube.memorygames.api.online;

import java.util.LinkedHashMap;
import java.util.Map;

final class OnlineModeStats {
    final long wins;
    final long losses;
    final long draws;

    private OnlineModeStats(OnlineMessages.MatchStats stats) {
        wins = Math.max(0L, stats.wins);
        losses = Math.max(0L, stats.losses);
        draws = Math.max(0L, stats.draws);
    }

    static OnlineModeStats forMode(OnlineMessages.AuthOk live,
                                   AccountManager.UserProfile cached, String mode) {
        if (!knownMode(mode)) {
            return null;
        }
        if (live != null && live.userId > 0
                && (cached == null || cached.userId <= 0 || cached.userId == live.userId)) {
            return from(live.modeStats, mode);
        }
        return cached != null && cached.userId > 0 ? from(cached.modeStats, mode) : null;
    }

    static void applySettlement(OnlineMessages.AuthOk profile, OnlineMessages.MatchEnd end) {
        // A settlement is a full snapshot, not a delta: replay must never add another result.
        if (profile != null && end != null) {
            profile.modeStats = copySnapshot(end.modeStats);
        }
    }

    static Map<String, OnlineMessages.MatchStats> copySnapshot(
            Map<String, OnlineMessages.MatchStats> snapshot) {
        if (snapshot == null) {
            return null;
        }
        Map<String, OnlineMessages.MatchStats> copy = new LinkedHashMap<>();
        String[] modes = {OnlineSession.MODE_RANKED, OnlineSession.MODE_PRACTICE,
                OnlineSession.MODE_FRIEND};
        for (String mode : modes) {
            OnlineModeStats value = from(snapshot, mode);
            if (value != null) {
                OnlineMessages.MatchStats stats = new OnlineMessages.MatchStats();
                stats.wins = value.wins;
                stats.losses = value.losses;
                stats.draws = value.draws;
                copy.put(mode, stats);
            }
        }
        return copy;
    }

    static int statColumns(int widthPx, int baseColumnPx, float fontScale) {
        return OnlineRankingSummary.statColumns(widthPx, baseColumnPx, fontScale);
    }

    int winRate() {
        double total = (double) wins + losses + draws;
        return total == 0.0 ? 0 : (int) Math.round(wins * 100.0 / total);
    }

    private static OnlineModeStats from(Map<String, OnlineMessages.MatchStats> snapshot,
                                        String mode) {
        OnlineMessages.MatchStats stats = snapshot == null ? null : snapshot.get(mode);
        // Old servers only have mixed totals; they cannot be attributed to any one mode.
        return stats == null ? null : new OnlineModeStats(stats);
    }

    private static boolean knownMode(String mode) {
        return OnlineSession.MODE_RANKED.equals(mode) || OnlineSession.MODE_PRACTICE.equals(mode)
                || OnlineSession.MODE_FRIEND.equals(mode);
    }
}
