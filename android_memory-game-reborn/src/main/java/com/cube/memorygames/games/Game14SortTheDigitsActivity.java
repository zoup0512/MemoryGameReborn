package com.cube.memorygames.games;

import android.os.Handler;
import android.view.View;
import android.widget.RelativeLayout.LayoutParams;
import com.cube.memorygames.MemoryApplicationModel;
import com.cube.memorygames.SoundUtils;
import com.cube.memorygames.SoundUtils.SOUND;
import com.cube.memorygames.api.local.model.LocalGameSession;
import com.cube.memorygames.logic.GameFlowState;
import com.cube.memorygames.logic.GameFlowStateTimer;
import com.cube.memorygames.logic.GameProgression3;
import com.cube.memorygames.logic.GameRandom;
import com.cube.memorygames.ui.ElementGame14;
import com.cube.memorygames.ui.Game14Grid;
import com.memory.brain.training.games.R;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TimerTask;

public class Game14SortTheDigitsActivity extends Game1MemoryGridActivity {
    private static final int GAME_TIME = 60000;
    protected static int miniLevelTime = 3400;
    private long gameTime;
    private long startedTime;
    private Handler timerHandler = new Handler();
    private Runnable timerRunnable = new Runnable() {
        public void run() {
            long currentPausedDuration = 0;
            if (Game14SortTheDigitsActivity.this.pausedTime != 0) {
                currentPausedDuration = System.currentTimeMillis() - Game14SortTheDigitsActivity.this.pausedTime;
            }
            final long time = ((Game14SortTheDigitsActivity.this.gameTime - (System.currentTimeMillis() - Game14SortTheDigitsActivity.this.startedTime)) + Game14SortTheDigitsActivity.this.pausedDuration) + currentPausedDuration;
            if (time > 0) {
                Game14SortTheDigitsActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        Game14SortTheDigitsActivity.this.updateTimerText(time);
                    }
                });
                Game14SortTheDigitsActivity.this.timerHandler.postDelayed(this, 50);
                return;
            }
            Game14SortTheDigitsActivity.this.startedTime = 0;
            Game14SortTheDigitsActivity.this.progressBar.setVisibility(4);
            if (!Game14SortTheDigitsActivity.this.isFinishing()) {
                Game14SortTheDigitsActivity.this.showTimeout(true);
            }
        }
    };

    protected class StatGameFlowState implements GameFlowState {
        protected StatGameFlowState() {
        }

        public void applyState() {
            Game14SortTheDigitsActivity.this.grid.hideChallengeCells();
            Game14SortTheDigitsActivity.this.grid.enableAllCells();
            Game14SortTheDigitsActivity.this.timerContainer.setVisibility(8);
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
        Game14Grid game14Grid = new Game14Grid(this);
        game14Grid.setElements(getGroups(this.progression.getLevelNumber()));
        game14Grid.setGridEventsListener(this);
        game14Grid.hideChallengeCells();
        LayoutParams params = new LayoutParams(-1, -1);
        params.addRule(13, -1);
        this.gridContainer.addView(game14Grid, 0, params);
        this.grid = game14Grid;
        if (this.startedTime <= 0) {
            this.progressBar.setVisibility(0);
            this.startedTime = System.currentTimeMillis();
            if (this.progression.getLevelNumber() == 1) {
                if (this.isOnlineGame) {
                    this.gameTime = 45000;
                    this.progressBar.setMax(4500);
                }
                if (this.isChallenge) {
                    this.gameTime = (long) (this.challengeJsonGame.getLevel() * miniLevelTime);
                    this.progressBar.setMax((int) (this.gameTime / 10));
                } else {
                    this.gameTime = 60000;
                    this.progressBar.setMax(6000);
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

    private List<ElementGame14> getGroups(int levelNumber) {
        int numbersCount;
        int colorsCount;
        int minNumber;
        int maxNumber;
        int i;
        if (levelNumber <= 3) {
            numbersCount = 3;
            colorsCount = 1;
        } else if (levelNumber <= 10) {
            numbersCount = 4;
            colorsCount = 2;
        } else if (levelNumber <= 15) {
            numbersCount = 5;
            colorsCount = 3;
        } else {
            numbersCount = 6;
            colorsCount = 4;
        }
        if (levelNumber <= 3) {
            minNumber = 1;
            maxNumber = 9;
        } else if (levelNumber <= 7) {
            minNumber = 1;
            maxNumber = 15;
        } else if (levelNumber <= 10) {
            minNumber = -5;
            maxNumber = 15;
        } else if (levelNumber <= 13) {
            minNumber = -5;
            maxNumber = 20;
        } else if (levelNumber <= 16) {
            minNumber = -10;
            maxNumber = 25;
        } else {
            minNumber = -10;
            maxNumber = 30;
        }
        List<Integer> colors = getColorGroups(colorsCount);
        List<Integer> numbers = getNumbers(numbersCount, minNumber, maxNumber);
        List<ElementGame14> result = new ArrayList();
        for (i = 0; i < numbersCount; i++) {
            ElementGame14 elementGame14 = new ElementGame14();
            elementGame14.number = ((Integer) numbers.get(i)).intValue();
            result.add(elementGame14);
        }
        GameRandom.shuffle(result);
        i = 0;
        int colorNumber = 0;
        ElementGame14 elementGame14;
        while (i < result.size()) {
            if (colorNumber == colors.size()) {
                colorNumber = 0;
            }
            elementGame14 = (ElementGame14) result.get(i);
            elementGame14.color = ((Integer) colors.get(colorNumber)).intValue();
            elementGame14.figure = 0;
            i++;
            colorNumber++;
        }
        Collections.sort(result, new Comparator<ElementGame14>() {
            public int compare(ElementGame14 t1, ElementGame14 t2) {
                return t1.number - t2.number;
            }
        });
        for (i = 0; i < result.size(); i++) {
            ((ElementGame14) result.get(i)).order = i;
        }
        GameRandom.shuffle(result);
        return result;
    }

    private List<Integer> getNumbers(int numbersCount, int minNumber, int maxNumber) {
        List<Integer> numbers = new ArrayList();
        for (int i = minNumber; i <= maxNumber; i++) {
            numbers.add(Integer.valueOf(i));
        }
        GameRandom.shuffle(numbers);
        return numbers.subList(0, numbersCount);
    }

    private List<Integer> getColorGroups(int colorsCount) {
        int[] shapeColors = getResources().getIntArray(R.array.game14_colors);
        List<Integer> colors = new ArrayList();
        for (int shapeColor : shapeColors) {
            colors.add(Integer.valueOf(shapeColor));
        }
        GameRandom.shuffle(colors);
        return colors.subList(0, colorsCount);
    }

    private List<Integer> getFiguresGroups(int figuresCount) {
        List<Integer> figures = new ArrayList();
        for (int i = 0; i < 4; i++) {
            figures.add(Integer.valueOf(i));
        }
        GameRandom.shuffle(figures);
        return figures.subList(0, figuresCount);
    }

    protected void createGameFlowStates() {
        List<GameFlowState> states = new ArrayList();
        states.add(new StatGameFlowState());
        this.stateTimer = new GameFlowStateTimer(states);
    }

    public void onSuccessCellClicked(int lastAddedCellClicked) {
        this.userScore += getLevelScore(this.progression.getLevelNumber());
        giveStars();
        updateOnlineScore();
        this.textLevelReady.setText("");
        this.levelHint.setVisibility(0);
        this.timerContainer.setVisibility(0);
        this.stateTimer.scheduleAndRunTask(new TimerTask() {
            public void run() {
                Game14SortTheDigitsActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        if ((Game14SortTheDigitsActivity.this.gameTime - (System.currentTimeMillis() - Game14SortTheDigitsActivity.this.startedTime)) + Game14SortTheDigitsActivity.this.pausedDuration > 0) {
                            Game14SortTheDigitsActivity.this.startNextLevel();
                        }
                    }
                });
            }
        }, 800);
        SoundUtils.playSound(this, SOUND.WIN);
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
