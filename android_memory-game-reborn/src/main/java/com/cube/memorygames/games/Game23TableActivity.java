package com.cube.memorygames.games;

import android.os.Handler;
import android.view.View;
import android.widget.RelativeLayout.LayoutParams;
import com.cube.memorygames.MemoryApplicationModel;
import com.cube.memorygames.api.local.LocalDataManager;
import com.cube.memorygames.api.local.model.LocalGameSession;
import com.cube.memorygames.logic.GameFlowState;
import com.cube.memorygames.logic.GameFlowStateTimer;
import com.cube.memorygames.logic.GameProgression3;
import com.cube.memorygames.logic.GameRandom;
import com.cube.memorygames.ui.ElementGame14;
import com.cube.memorygames.ui.Game23Grid;
import com.cube.memorygames.ui.Game23Colors;
import com.memory.brain.training.games.R;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Game23TableActivity extends Game1MemoryGridActivity {
    private static final int GAME_TIME = 50000;
    private List<ElementGame14> elements;
    private final Game23RoundProgress roundProgress = new Game23RoundProgress();
    private long gameTime;
    private long startedTime;
    private Handler timerHandler = new Handler();
    private Runnable timerRunnable = new Runnable() {
        public void run() {
            long currentPausedDuration = 0;
            if (Game23TableActivity.this.pausedTime != 0) {
                currentPausedDuration = System.currentTimeMillis() - Game23TableActivity.this.pausedTime;
            }
            final long time = ((Game23TableActivity.this.gameTime - (System.currentTimeMillis() - Game23TableActivity.this.startedTime)) + Game23TableActivity.this.pausedDuration) + currentPausedDuration;
            if (time > 0) {
                Game23TableActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        Game23TableActivity.this.updateTimerText(time);
                    }
                });
                Game23TableActivity.this.timerHandler.postDelayed(this, 50);
                return;
            }
            Game23TableActivity.this.startedTime = 0;
            Game23TableActivity.this.progressBar.setVisibility(4);
            if (!Game23TableActivity.this.isFinishing()) {
                Game23TableActivity.this.showTimeout(true);
            }
        }
    };

    protected class StatGameFlowState implements GameFlowState {
        protected StatGameFlowState() {
        }

        public void applyState() {
            Game23TableActivity.this.grid.hideChallengeCells();
            Game23TableActivity.this.grid.enableAllCells();
            Game23TableActivity.this.timerContainer.setVisibility(8);
        }

        public int getDuration() {
            return 0;
        }
    }

    protected boolean isEnableLevelTimer() {
        return false;
    }

    protected void createProgression() {
        this.progression = new GameProgression3();
    }

    private void updateTimerText(long time) {
        this.progressBar.setProgress((int) (time / 10));
    }

    private void killTimer() {
        this.timerHandler.removeCallbacks(this.timerRunnable);
    }

    public void onBackPressed() {
        if (this.isOnlineGame) {
            showOnlineBackPressDialog();
        } else {
            confirmBackPress();
        }
    }

    private void confirmBackPress() {
        killTimer();
        int currentLevel = this.progression.getLevelNumber();
        if (currentLevel > this.gameSession.endLevel) {
            this.gameSession.endLevel = currentLevel;
        }
        saveGameSession();
        super.onBackPressed();
    }



    protected void onDestroy() {
        super.onDestroy();
        killTimer();
    }

    protected void startLevel() {
        displayLevelNumber();
        if (this.elements == null) {
            this.elements = generateElements();
        }
        final Game23Grid game23Grid = new Game23Grid(this);
        game23Grid.setElements(this.elements, this.progression.getLevelNumber());
        game23Grid.setGridEventsListener(this);
        game23Grid.hideChallengeCells();
        this.grid = game23Grid;
        // Pin the grid to the container's measured size. MATCH_PARENT plus
        // CENTER_IN_PARENT let this canvas view lay out taller than the
        // visible area on 18:9 screens, pushing the last rows under the
        // navigation bar (device finding 2026-10-11).
        this.gridContainer.post(new Runnable() {
            public void run() {
                if (Game23TableActivity.this.grid != game23Grid || game23Grid.getParent() != null) {
                    return;
                }
                int w = Game23TableActivity.this.gridContainer.getWidth();
                int h = Game23TableActivity.this.gridContainer.getHeight();
                LayoutParams params = new LayoutParams(w > 0 ? w : -1, h > 0 ? h : -1);
                params.addRule(13, -1);
                Game23TableActivity.this.gridContainer.addView(game23Grid, 0, params);
            }
        });
        if (this.startedTime <= 0) {
            this.progressBar.setVisibility(0);
            this.startedTime = System.currentTimeMillis();
            if (this.progression.getLevelNumber() == 1) {
                if (this.isOnlineGame) {
                    this.gameTime = com.cube.memorygames.api.online.OnlineSession.get().roundDurationMs(this, 45000L);
                    this.progressBar.setMax((int) (this.gameTime / 10));
                } else {
                    this.gameTime = 50000;
                    this.progressBar.setMax(5000);
                }
            }
            this.timerHandler.postDelayed(this.timerRunnable, 0);
        }
        this.stateTimer.start();
    }

    protected void exitFromPauseClicked() {
        retryFromPauseClicked();
    }

    protected void retryFromPauseClicked() {
        this.progressBar.setVisibility(4);
        this.startedTime = 0;
        this.timerHandler.removeCallbacks(this.timerRunnable);
    }

    private List<ElementGame14> generateElements() {
        int i;
        List<Integer> numbers = getNumbers(1, 48);
        List<ElementGame14> result = new ArrayList();
        for (i = 0; i < 48; i++) {
            ElementGame14 elementGame14 = new ElementGame14();
            elementGame14.number = ((Integer) numbers.get(i)).intValue();
            result.add(elementGame14);
        }
        GameRandom.shuffle(result);
        int[] palette = Game23Colors.shuffledPalette();
        i = 0;
        while (i < result.size()) {
            ElementGame14 elementGame14 = (ElementGame14) result.get(i);
            elementGame14.color = Game23Colors.colorForNumber(elementGame14.number, palette);
            i++;
        }
        Collections.sort(result, new Comparator<ElementGame14>() {
            public int compare(ElementGame14 t1, ElementGame14 t2) {
                return t1.number - t2.number;
            }
        });
        GameRandom.shuffle(result);
        return result;
    }

    private List<Integer> getNumbers(int minNumber, int maxNumber) {
        List<Integer> numbers = new ArrayList();
        for (int i = minNumber; i <= maxNumber; i++) {
            numbers.add(Integer.valueOf(i));
        }
        GameRandom.shuffle(numbers);
        return numbers;
    }

    protected void createGameFlowStates() {
        List<GameFlowState> states = new ArrayList();
        states.add(new StatGameFlowState());
        this.stateTimer = new GameFlowStateTimer(states);
    }

    @Override
    protected void startNextLevel() {
        // Success and the base failure/skip callback both arrive here. Keep both
        // round timers alive: an early round_complete would shorten the AI window.
        roundProgress.advance(this.isOnlineGame, this.progression.getLevelNumber(),
                () -> super.startNextLevel(), () -> this.grid.disableAllCells());
    }

    public void onSuccessCellClicked(int lastAddedCellClicked) {
        if (roundProgress.isWaitingForDeadline()) return;
        giveStars();
        this.userScore += getLevelScore(this.progression.getLevelNumber());
        updateOnlineScore();
        this.textLevelReady.setText("");
        startNextLevel();
    }

    protected int giveStars() {
        int newStars;
        if (this.isChallenge || this.isOnlineGame || this.isWorkout) {
            newStars = 0;
        } else if (this.progression.getLevelNumber() % 3 == 0) {
            newStars = 1;
        } else {
            newStars = 0;
        }
        if (newStars > 0) {
            this.gameSession.moneyEarned += newStars;
            this.localDataManager.addCoinsTransaction(LocalDataManager.TYPE_GAME_EARNED, newStars);
        }
        return newStars;
    }

    public void onFailCellClicked() {
        if (roundProgress.isWaitingForDeadline()) return;
        if (!this.isOnlineGame) {
            this.progressBar.setVisibility(4);
            this.gameTime = (this.gameTime - (System.currentTimeMillis() - this.startedTime)) + this.pausedDuration;
            this.startedTime = 0;
            this.pausedDuration = 0;
            this.timerHandler.removeCallbacks(this.timerRunnable);
        }
        super.onFailCellClicked();
    }

    public void retryGame() {
        this.roundProgress.reset();
        this.elements = generateElements();
        this.buyStarsClickedCountCount = 0;
        this.videoWatchedCount = 0;
        this.pausedDuration = 0;
        this.pausedTime = 0;
        this.pausedCount = 0;
        enablePausePanel();
        this.life1.setVisibility(0);
        this.life2.setVisibility(0);
        int currentLevel = this.progression.getLevelNumber();
        if (currentLevel > this.gameSession.endLevel) {
            this.gameSession.endLevel = currentLevel;
        }
        saveGameSession();
        String game = this.gameSession.game;
        this.gameSession = new LocalGameSession();
        this.gameSession.moneyEarned = 0;
        this.gameSession.moneyPaid = 0;
        this.gameSession.startLevel = 1;
        this.gameSession.endLevel = 1;
        this.gameSession.game = game;
        this.progression.startGame();
        if (this.grid != null) {
            this.gridContainer.removeView((View) this.grid);
        }
        this.timerContainer.setVisibility(8);
        startLevel();
    }
}
