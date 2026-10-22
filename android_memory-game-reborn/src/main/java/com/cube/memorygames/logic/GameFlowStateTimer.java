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
    /** Optional diagnostics only. Observer failures never participate in game control flow. */
    public interface Observer {
        void flow(String event, String phase, long run, int step, int generation);
        Object scheduled(String reason, long delay, long run, int step, int generation);
        void executed(Object captured, boolean accepted);
    }
    private Observer observer;
    private long flowRun;
    private int flowStep;
    public void setObserver(Observer observer) { this.observer = observer; }
    private void observe(String event, String phase) {
        try { if (observer != null) observer.flow(event, phase, flowRun, flowStep, generation.token()); }
        catch (RuntimeException ignored) { }
    }
    private Object scheduled(String reason, long delay) {
        try { return observer == null ? null : observer.scheduled(reason, delay, flowRun, flowStep, generation.token()); }
        catch (RuntimeException ignored) { return null; }
    }
    private void executed(Object captured, boolean accepted) {
        try { if (observer != null) observer.executed(captured, accepted); }
        catch (RuntimeException ignored) { }
    }
    private void scheduleState() {
        long delay = (long) currentState.getDuration();
        this.timer.schedule(new StateChangingTimerTask(scheduled("flow_advance", delay)), delay);
    }
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
        private final Object captured;
        private StateChangingTimerTask(Object captured) {
            this.captured = captured;
        }

        private final int token = generation.token();
        public void run() {
            GameFlowStateTimer.this.uiHandler.post(() -> {
                executed(captured, generation.accepts(token));
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
        observe("flow_start", "request");
        if (generation.isCancelled()) return;
        if (held) { observe("flow_start", "held"); startPending = true; return; }
        ++flowRun;
        flowStep = 0;
        this.timer.purge();
        this.iterator = this.gameFlowStates.iterator();
        if (this.iterator.hasNext()) {
            startNextState();
        }
    }

    private void startNextState() {
        if (generation.isCancelled()) return;
        this.currentState = (GameFlowState) this.iterator.next();
        ++flowStep;
        observe("flow_state", "apply");
        this.currentState.applyState();
        if (!generation.isCancelled() && currentState != null) scheduleState();
    }

    public GameFlowState getCurrentState() {
        return this.currentState;
    }

    public void pause() {
        if (generation.isCancelled()) return;
        generation.pause();
        observe("flow_pause", "pause");
        this.uiHandler.removeCallbacksAndMessages(null);
        this.timer.cancel();
        this.timer = new Timer();
    }

    public void resume() {
        observe("flow_resume", "resume");
        if (generation.isCancelled()) return;
        this.timer.purge();
        this.iterator = this.gameFlowStates.iterator();
        if (this.currentState != null) {
            while (this.iterator.hasNext()) {
                if (this.iterator.next() == this.currentState) {
                    observe("flow_state", "apply");
                    this.currentState.applyState();
                    scheduleState();
                    return;
                }
            }
        }
    }

    public void cancel() {
        generation.cancel();
        observe("flow_cancel", "destroy");
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
