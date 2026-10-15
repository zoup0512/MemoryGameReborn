package com.cube.memorygames.api.online;

final class RoundIntermission {
    private OnlineMessages.RoundEnd roundEnd;
    private long receivedElapsed;

    boolean accept(OnlineMessages.RoundEnd end, int currentRound, long nowElapsed) {
        if (end == null || end.round < 1 || end.round < currentRound
                || (roundEnd != null && end.round < roundEnd.round)) {
            return false;
        }
        if (roundEnd != null && end.round == roundEnd.round
                && end.serverNow <= roundEnd.serverNow) {
            return false;
        }
        roundEnd = end;
        receivedElapsed = nowElapsed;
        return true;
    }

    OnlineMessages.RoundEnd getRoundEnd() {
        return roundEnd;
    }

    long remainingMs(long nowElapsed) {
        if (roundEnd == null || roundEnd.serverNow <= 0L
                || roundEnd.nextRoundStartsAt <= roundEnd.serverNow) {
            return 0L;
        }
        // Anchor the server's interval to a monotonic clock, not the phone's wall clock.
        long interval = roundEnd.nextRoundStartsAt - roundEnd.serverNow;
        return Math.max(0L, interval - Math.max(0L, nowElapsed - receivedElapsed));
    }

    boolean hasCountdown() {
        return roundEnd != null && roundEnd.serverNow > 0L
                && roundEnd.nextRoundStartsAt > 0L;
    }

    static boolean canLaunch(OnlineMessages.RoundStart start, String matchId,
            int currentRound, OnlineMessages.RoundEnd ended) {
        return start != null && matchId != null && matchId.equals(start.matchId)
                && start.round == currentRound
                && (ended == null || start.round > ended.round);
    }

    static long secondsRemaining(long remainingMs) {
        return remainingMs <= 0L ? 0L : 1L + (remainingMs - 1L) / 1000L;
    }

    void clear() {
        roundEnd = null;
        receivedElapsed = 0L;
    }
}
