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

    private class StateChangingTimerTask extends TimerTask {
        private StateChangingTimerTask() {
        }

        public void run() {
            GameFlowStateTimer.this.uiHandler.post(GameFlowStateTimer.this.stateChangingUIThreadRunnable);
        }
    }

    private class StateChangingUIThreadRunnable implements Runnable {
        private StateChangingUIThreadRunnable() {
        }

        public void run() {
            if (GameFlowStateTimer.this.iterator.hasNext()) {
                GameFlowStateTimer.this.startNextState();
            }
        }
    }

    public GameFlowStateTimer(List<GameFlowState> gameFlowStates) {
        this.gameFlowStates = gameFlowStates;
        this.uiHandler = new Handler(Looper.getMainLooper());
    }

    public void start() {
        this.timer.purge();
        this.iterator = this.gameFlowStates.iterator();
        if (this.iterator.hasNext()) {
            startNextState();
        }
    }

    private void startNextState() {
        this.currentState = (GameFlowState) this.iterator.next();
        this.currentState.applyState();
        this.timer.schedule(new StateChangingTimerTask(), (long) this.currentState.getDuration());
    }

    public GameFlowState getCurrentState() {
        return this.currentState;
    }

    public void pause() {
        this.timer.cancel();
        this.timer = new Timer();
    }

    public void resume() {
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
        this.currentState = null;
        this.timer.cancel();
    }

    public void scheduleAndRunTask(TimerTask task, long delay) {
        this.timer.schedule(task, delay);
    }
}
