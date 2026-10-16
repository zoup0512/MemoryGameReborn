package com.cube.memorygames.games;

/** Shared success/skip progression boundary for the finite online Schulte board. */
final class Game23RoundProgress {
    static final int TARGET_COUNT = 48;
    private boolean waitingForDeadline;

    void advance(boolean online, int resolvedTarget, Runnable nextTarget, Runnable waitForDeadline) {
        if (waitingForDeadline) return;
        if (online && resolvedTarget >= TARGET_COUNT) {
            waitingForDeadline = true;
            waitForDeadline.run();
        } else {
            nextTarget.run();
        }
    }

    boolean isWaitingForDeadline() { return waitingForDeadline; }
    void reset() { waitingForDeadline = false; }
}
