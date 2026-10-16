package com.cube.memorygames.api.online;

/** Pure-Java round clock. Replays can shorten, but never extend, a running round. */
final class RoundClock {
    private int round = -1;
    private boolean preparing;
    private boolean started;
    private long startedElapsed;
    private long durationMs;

    static boolean belongsToMatch(OnlineMessages.RoundStart frame, String matchId) {
        return frame != null && matchId != null && matchId.equals(frame.matchId);
    }

    boolean accept(OnlineMessages.RoundStart frame, boolean prepareRounds, long nowElapsed) {
        if (frame == null || frame.round < 1 || frame.round < round) return false;
        boolean preparation = prepareRounds && frame.preparing;
        boolean sameRound = frame.round == round;
        // A delayed preparation replay must not put a running round back behind the ready gate.
        if (sameRound && started && preparation) return false;
        if (!sameRound) {
            started = false;
            startedElapsed = 0L;
        }
        round = frame.round;
        preparing = preparation;
        if (preparation) {
            if (frame.durationMs > 0) durationMs = frame.durationMs;
            return true;
        }
        frame.resuming = sameRound && started;
        long remaining = started ? remainingMs(nowElapsed) : durationMs;
        if (frame.durationMs > 0) {
            remaining = started ? Math.min(remaining, frame.durationMs) : frame.durationMs;
        }
        // Re-anchor the remaining interval: retaining the old origin double-subtracts elapsed time.
        durationMs = remaining;
        startedElapsed = nowElapsed;
        started = true;
        return true;
    }

    long durationMs() { return durationMs; }
    boolean isPreparing() { return preparing; }
    boolean hasStarted() { return started; }

    long remainingMs(long nowElapsed) {
        return started ? Math.max(0L, durationMs - Math.max(0L, nowElapsed - startedElapsed)) : 0L;
    }

    void reset(long defaultDurationMs) {
        round = -1;
        preparing = false;
        started = false;
        startedElapsed = 0L;
        durationMs = Math.max(0L, defaultDurationMs);
    }
}
