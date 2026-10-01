package com.cube.memorygames.games;

import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Handler;
import android.support.v7.app.AlertDialog.Builder;
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
import com.memory.brain.training.games.R;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Game23TableActivity extends Game1MemoryGridActivity {
    private static final int GAME_TIME = 50000;
    private List<ElementGame14> elements;
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

    private void showOnlineBackPressDialog() {
        Builder builder = new Builder(this);
        builder.setMessage((int) R.string.dialog_lose_bet);
        builder.setNegativeButton((int) R.string.cancel, new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        builder.setPositiveButton((int) R.string.ok, new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                Game23TableActivity.this.confirmBackPress();
                dialog.dismiss();
            }
        });
        builder.show();
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
        Game23Grid game23Grid = new Game23Grid(this);
        game23Grid.setElements(this.elements, this.progression.getLevelNumber());
        game23Grid.setGridEventsListener(this);
        game23Grid.hideChallengeCells();
        LayoutParams params = new LayoutParams(-1, -1);
        params.addRule(13, -1);
        this.gridContainer.addView(game23Grid, 0, params);
        this.grid = game23Grid;
        if (this.startedTime <= 0) {
            this.progressBar.setVisibility(0);
            this.startedTime = System.currentTimeMillis();
            if (this.progression.getLevelNumber() == 1) {
                if (this.isOnlineGame) {
                    this.gameTime = 45000;
                    this.progressBar.setMax(4500);
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
        List<Integer> colors = getColorGroups();
        List<Integer> numbers = getNumbers(1, 48);
        List<ElementGame14> result = new ArrayList();
        for (i = 0; i < 48; i++) {
            ElementGame14 elementGame14 = new ElementGame14();
            elementGame14.number = ((Integer) numbers.get(i)).intValue();
            result.add(elementGame14);
        }
        GameRandom.shuffle(result);
        i = 0;
        int colorNumber = 0;
        while (i < result.size()) {
            if (colorNumber == colors.size()) {
                colorNumber = 0;
            }
            ((ElementGame14) result.get(i)).color = ((Integer) colors.get(colorNumber)).intValue();
            i++;
            colorNumber++;
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

    private List<Integer> getColorGroups() {
        int[] shapeColors = getResources().getIntArray(R.array.game23_colors);
        List<Integer> colors = new ArrayList();
        for (int shapeColor : shapeColors) {
            colors.add(Integer.valueOf(shapeColor));
        }
        GameRandom.shuffle(colors);
        return colors;
    }

    protected void createGameFlowStates() {
        List<GameFlowState> states = new ArrayList();
        states.add(new StatGameFlowState());
        this.stateTimer = new GameFlowStateTimer(states);
    }

    public void onSuccessCellClicked(int lastAddedCellClicked) {
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
