package com.cube.memorygames.logic;

import android.os.Handler;
import android.os.Looper;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class GameFlowStateTimer {
    private GameFlowState currentState;
    private List<GameFlowState> gameFlowStates;
    private Iterator<GameFlowState> iterator;
    private StateChangingUIThreadRunnable stateChangingUIThreadRunnable = new StateChangingUIThreadRunnable();
    private Timer timer = new Timer();
    private final Handler uiHandler;
    private final GameFlowGeneration generation = new GameFlowGeneration();
    private boolean held;
    private boolean startPending;
    private final java.util.List<Runnable> heldTasks = new java.util.ArrayList<>();

    /** A party grid is built while its animations/input await the server barrier. */
    public void setHeld(boolean value) {
        held = value;
        if (!held) {
            if (startPending) { startPending = false; start(); }
            for (Runnable task : new java.util.ArrayList<>(heldTasks)) task.run();
            heldTasks.clear();
        }
    }

    private class StateChangingTimerTask extends TimerTask {
        private StateChangingTimerTask() {
        }

        private final int token = generation.token();
        public void run() {
            GameFlowStateTimer.this.uiHandler.post(() -> {
                if (generation.accepts(token)) stateChangingUIThreadRunnable.run();
            });
        }
    }

    private class StateChangingUIThreadRunnable implements Runnable {
        private StateChangingUIThreadRunnable() {
        }

        public void run() {
            if (!generation.isCancelled() && GameFlowStateTimer.this.iterator != null && GameFlowStateTimer.this.iterator.hasNext()) {
                GameFlowStateTimer.this.startNextState();
            }
        }
    }

    public GameFlowStateTimer(List<GameFlowState> gameFlowStates) {
        this.gameFlowStates = gameFlowStates;
        this.uiHandler = new Handler(Looper.getMainLooper());
    }

    public void start() {
        if (generation.isCancelled()) return;
        if (held) { startPending = true; return; }
        this.timer.purge();
        this.iterator = this.gameFlowStates.iterator();
        if (this.iterator.hasNext()) {
            startNextState();
        }
    }

    private void startNextState() {
        if (generation.isCancelled()) return;
        this.currentState = (GameFlowState) this.iterator.next();
        this.currentState.applyState();
        if (!generation.isCancelled() && currentState != null) this.timer.schedule(new StateChangingTimerTask(), (long) this.currentState.getDuration());
    }

    public GameFlowState getCurrentState() {
        return this.currentState;
    }

    public void pause() {
        if (generation.isCancelled()) return;
        generation.pause();
        this.uiHandler.removeCallbacksAndMessages(null);
        this.timer.cancel();
        this.timer = new Timer();
    }

    public void resume() {
        if (generation.isCancelled()) return;
        this.timer.purge();
        this.iterator = this.gameFlowStates.iterator();
        if (this.currentState != null) {
            while (this.iterator.hasNext()) {
                if (this.iterator.next() == this.currentState) {
                    this.currentState.applyState();
                    this.timer.schedule(new StateChangingTimerTask(), (long) this.currentState.getDuration());
                    return;
                }
            }
        }
    }

    public void cancel() {
        generation.cancel();
        this.currentState = null;
        this.timer.cancel();
        this.uiHandler.removeCallbacksAndMessages(null);
        this.heldTasks.clear();
        this.startPending = false;
    }

    public void scheduleAndRunTask(TimerTask task, long delay) {
        if (generation.isCancelled()) return;
        if (held) { heldTasks.add(() -> timer.schedule(task, delay)); return; }
        this.timer.schedule(task, delay);
    }
}
