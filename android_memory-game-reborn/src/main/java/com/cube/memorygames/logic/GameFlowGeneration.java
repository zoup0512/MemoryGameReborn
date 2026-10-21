package com.cube.memorygames.logic;

/** Invalidates timer-to-UI work even if cancellation races with Handler.post(). */
public final class GameFlowGeneration {
    private volatile int generation;
    private volatile boolean cancelled;
    public int token() { return generation; }
    public boolean accepts(int token) { return !cancelled && token == generation; }
    public boolean isCancelled() { return cancelled; }
    public void pause() { ++generation; }
    public void cancel() { cancelled = true; ++generation; }
}
