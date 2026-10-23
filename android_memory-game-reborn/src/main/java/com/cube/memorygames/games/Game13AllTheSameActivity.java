package com.cube.memorygames.games;

import android.os.Bundle;
import android.os.Handler;
import android.util.Pair;
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
import com.cube.memorygames.ui.Element;
import com.cube.memorygames.ui.Game13Grid;
import com.memory.brain.training.games.R;
import java.util.ArrayList;
import java.util.List;
import java.util.TimerTask;

public class Game13AllTheSameActivity extends Game1MemoryGridActivity {
    private static final int GAME_TIME = 60000;
    private int gameTime;
    private long startedTime;
    private Handler timerHandler = new Handler();
    private Runnable timerRunnable = new Runnable() {
        public void run() {
            long currentPausedDuration = 0;
            if (Game13AllTheSameActivity.this.pausedTime != 0) {
                currentPausedDuration = System.currentTimeMillis() - Game13AllTheSameActivity.this.pausedTime;
            }
            final long time = ((((long) Game13AllTheSameActivity.this.gameTime) - (System.currentTimeMillis() - Game13AllTheSameActivity.this.startedTime)) + Game13AllTheSameActivity.this.pausedDuration) + currentPausedDuration;
            if (time > 0) {
                Game13AllTheSameActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        Game13AllTheSameActivity.this.updateTimerText(time);
                    }
                });
                Game13AllTheSameActivity.this.timerHandler.postDelayed(this, 50);
                return;
            }
            Game13AllTheSameActivity.this.startedTime = 0;
            Game13AllTheSameActivity.this.progressBar.setVisibility(4);
            if (!Game13AllTheSameActivity.this.isFinishing()) {
                Game13AllTheSameActivity.this.showTimeout(true);
            }
        }
    };

    protected class StatGameFlowState implements GameFlowState {
        protected StatGameFlowState() {
        }

        public void applyState() {
            Game13AllTheSameActivity.this.grid.hideChallengeCells();
            Game13AllTheSameActivity.this.grid.enableAllCells();
            Game13AllTheSameActivity.this.timerContainer.setVisibility(8);
        }

        public int getDuration() {
            return 0;
        }
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.panelLives.setVisibility(4);
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
        Game13Grid game13Grid = new Game13Grid(this);
        Pair<Element, Element> elementTypes = getElementTypes(this.progression.getLevelNumber());
        game13Grid.setElements(elementTypes, getGroups(elementTypes, this.progression.getLevelNumber()));
        game13Grid.setGridEventsListener(this);
        game13Grid.hideChallengeCells();
        LayoutParams params = new LayoutParams(-1, -1);
        params.addRule(13, -1);
        this.gridContainer.addView(game13Grid, 0, params);
        this.grid = game13Grid;
        if (this.startedTime <= 0) {
            this.progressBar.setVisibility(0);
            this.startedTime = System.currentTimeMillis();
            if (this.progression.getLevelNumber() == 1) {
                if (this.isOnlineGame) {
                    this.gameTime = Game1MemoryGridActivity.GAME_TIME_ONLINE;
                    this.progressBar.setMax(4500);
                }
                if (this.isChallenge) {
                    this.gameTime = this.challengeJsonGame.getLevel() * miniLevelTime;
                    this.progressBar.setMax(this.gameTime / 10);
                } else {
                    this.gameTime = GAME_TIME;
                    this.progressBar.setMax(6000);
                }
            }
            this.timerHandler.postDelayed(this.timerRunnable, 0);
        }
        this.stateTimer.start();
    }

    private List<Element> getGroups(Pair<Element, Element> elementTypes, int levelNumber) {
        int group1Count;
        int i;
        Element element1 = elementTypes.first;
        Element element2 = elementTypes.second;
        double max = 1.0d - 0.35d;
        double randomValue = 0.35d + ((max - 0.35d) * GameRandom.nextDouble());
        int elementsCount = levelNumber + 4;
        int value1 = (int) (((double) elementsCount) * randomValue);
        int value2 = (int) Math.ceil(((double) elementsCount) * randomValue);
        if (((double) value1) / ((double) elementsCount) >= 0.35d && ((double) value1) / ((double) elementsCount) <= max) {
            group1Count = value1;
        } else if (((double) value2) / ((double) elementsCount) < 0.35d || ((double) value2) / ((double) elementsCount) > max) {
            group1Count = elementsCount / 2;
        } else {
            group1Count = value2;
        }
        int group2Count = elementsCount - group1Count;
        List<Element> result = new ArrayList();
        for (i = 0; i < group1Count; i++) {
            result.add(element1);
        }
        for (i = 0; i < group2Count; i++) {
            result.add(element2);
        }
        GameRandom.shuffle(result);
        return result;
    }

    private Pair<Element, Element> getElementTypes(int levelNumber) {
        if (levelNumber > 46) {
            levelNumber = 46;
        }
        int[] SHAPE_COLORS = getResources().getIntArray(R.array.game13_colors);
        int NUMBER_OF_COLORS = SHAPE_COLORS.length;
        Element element1 = new Element();
        Element element2 = new Element();
        if (levelNumber <= 5) {
            element1.setFigure(GameRandom.nextInt(7));
            do {
                element2.setFigure(GameRandom.nextInt(7));
            } while (element1.getFigure() == element2.getFigure());
            element1.setColor(SHAPE_COLORS[GameRandom.nextInt(NUMBER_OF_COLORS)]);
            do {
                element2.setColor(SHAPE_COLORS[GameRandom.nextInt(NUMBER_OF_COLORS)]);
            } while (element1.getColor() == element2.getColor());
        } else if (levelNumber <= 10) {
            element1.setFigure(GameRandom.nextInt(7));
            element2.setFigure(element1.getFigure());
            element1.setColor(SHAPE_COLORS[GameRandom.nextInt(NUMBER_OF_COLORS)]);
            do {
                element2.setColor(SHAPE_COLORS[GameRandom.nextInt(NUMBER_OF_COLORS)]);
            } while (element1.getColor() == element2.getColor());
        } else {
            element1.setFigure(GameRandom.nextInt(7));
            do {
                element2.setFigure(GameRandom.nextInt(7));
            } while (element1.getFigure() == element2.getFigure());
            element1.setColor(SHAPE_COLORS[GameRandom.nextInt(NUMBER_OF_COLORS)]);
            element2.setColor(element1.getColor());
        }
        return new Pair(element1, element2);
    }

    protected void createGameFlowStates() {
        List<GameFlowState> states = new ArrayList();
        states.add(new StatGameFlowState());
        this.stateTimer = new GameFlowStateTimer(states);
    }

    public void onSuccessCellClicked(int lastAddedCellClicked) {
        this.userScore += getLevelScore(this.progression.getLevelNumber());
        updateOnlineScore();
        giveStars();
        this.textLevelReady.setText("");
        this.levelHint.setVisibility(0);
        this.timerContainer.setVisibility(0);
        this.stateTimer.scheduleAndRunTask(new TimerTask() {
            public void run() {
                Game13AllTheSameActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        if ((((long) Game13AllTheSameActivity.this.gameTime) - (System.currentTimeMillis() - Game13AllTheSameActivity.this.startedTime)) + Game13AllTheSameActivity.this.pausedDuration > 0) {
                            Game13AllTheSameActivity.this.startNextLevel();
                        }
                    }
                });
            }
        }, 800);
        SoundUtils.playSound(this, SOUND.WIN);
    }

    public void onFailCellClicked() {
    }

    protected void exitFromPauseClicked() {
        retryFromPauseClicked();
    }

    protected void retryFromPauseClicked() {
        this.progressBar.setVisibility(4);
        this.startedTime = 0;
        this.timerHandler.removeCallbacks(this.timerRunnable);
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
