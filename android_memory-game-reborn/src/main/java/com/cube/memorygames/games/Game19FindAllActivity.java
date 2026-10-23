package com.cube.memorygames.games;

import android.os.Handler;
import androidx.annotation.LayoutRes;
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
import com.cube.memorygames.ui.Game19Grid;
import com.cube.memorygames.ui.Game19Grid.Element;
import com.memory.brain.training.games.R;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TimerTask;

public class Game19FindAllActivity extends Game1MemoryGridActivity {
    private long gameTime;
    private long startedTime;
    private Handler timerHandler = new Handler();
    private Runnable timerRunnable = new Runnable() {
        public void run() {
            long currentPausedDuration = 0;
            if (Game19FindAllActivity.this.pausedTime != 0) {
                currentPausedDuration = System.currentTimeMillis() - Game19FindAllActivity.this.pausedTime;
            }
            final long time = ((Game19FindAllActivity.this.gameTime - (System.currentTimeMillis() - Game19FindAllActivity.this.startedTime)) + Game19FindAllActivity.this.pausedDuration) + currentPausedDuration;
            if (time > 0) {
                Game19FindAllActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        Game19FindAllActivity.this.updateTimerText(time);
                    }
                });
                Game19FindAllActivity.this.timerHandler.postDelayed(this, 20);
                return;
            }
            Game19FindAllActivity.this.startedTime = 0;
            Game19FindAllActivity.this.progressBar.setVisibility(4);
            if (!Game19FindAllActivity.this.isFinishing()) {
                Game19FindAllActivity.this.showTimeout(true);
            }
        }
    };

    protected class StatGameFlowState implements GameFlowState {
        protected StatGameFlowState() {
        }

        public void applyState() {
            Game19FindAllActivity.this.grid.hideChallengeCells();
            Game19FindAllActivity.this.grid.enableAllCells();
            Game19FindAllActivity.this.timerContainer.setVisibility(8);
        }

        public int getDuration() {
            return 0;
        }
    }

    public void setContentView(@LayoutRes int layoutResID) {
        super.setContentView((int) R.layout.activity_game19);
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
        Game19Grid game19Grid = new Game19Grid(this);
        fillElements(this.progression.getLevelNumber(), game19Grid);
        game19Grid.setGridEventsListener(this);
        game19Grid.hideChallengeCells();
        LayoutParams params = new LayoutParams(-1, -1);
        params.addRule(13, -1);
        this.gridContainer.addView(game19Grid, 0, params);
        this.grid = game19Grid;
        if (this.startedTime <= 0) {
            this.progressBar.setVisibility(0);
            this.startedTime = System.currentTimeMillis();
            if (this.progression.getLevelNumber() == 1) {
                if (this.isOnlineGame) {
                    this.gameTime = (long) (this.gameInfo.getOnlineLevelTime() * 1000);
                    this.progressBar.setMax((this.gameInfo.getOnlineLevelTime() * 1000) / 10);
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

    private void fillElements(int levelNumber, Game19Grid game19Grid) {
        int i;
        int elementsCount = getElementCount(levelNumber);
        int numberOfGroups = getNumberOfGroups(levelNumber);
        List<Integer> colors = getColors(levelNumber);
        List<Pair<Integer, Integer>> colorSets = new ArrayList();
        for (Integer colorCenter : colors) {
            for (Integer colorSide : colors) {
                if (!colorCenter.equals(colorSide)) {
                    colorSets.add(new Pair(colorCenter, colorSide));
                }
            }
        }
        Collections.shuffle(colorSets);
        colorSets = colorSets.subList(0, numberOfGroups);
        Pair<Integer, Integer> fieldSize = getFieldSize(levelNumber);
        List<Pair<Integer, Integer>> fieldPositions = new ArrayList();
        for (i = 0; i < ((Integer) fieldSize.first).intValue(); i++) {
            for (int j = 0; j < ((Integer) fieldSize.second).intValue(); j++) {
                fieldPositions.add(new Pair(Integer.valueOf(i), Integer.valueOf(j)));
            }
        }
        Collections.shuffle(fieldPositions);
        List<Element> elements = new ArrayList();
        for (i = 0; i < elementsCount; i++) {
            Pair<Integer, Integer> colorSet = (Pair) colorSets.get(i % numberOfGroups);
            Pair<Integer, Integer> position = (Pair) fieldPositions.get(i);
            elements.add(new Element(((Integer) colorSet.first).intValue(), ((Integer) colorSet.second).intValue(), ((Integer) position.first).intValue(), ((Integer) position.second).intValue()));
        }
        game19Grid.setElements(colorSets.subList(0, getGuessElementCount(levelNumber)), elements, ((Integer) fieldSize.first).intValue(), ((Integer) fieldSize.second).intValue());
    }

    private int getNumberOfGroups(int levelNumber) {
        if (levelNumber <= 5) {
            return 3;
        }
        if (levelNumber <= 10) {
            return 4;
        }
        return 6;
    }

    protected void exitFromPauseClicked() {
        retryFromPauseClicked();
    }

    protected void retryFromPauseClicked() {
        this.progressBar.setVisibility(4);
        this.startedTime = 0;
        this.timerHandler.removeCallbacks(this.timerRunnable);
    }

    private List<Integer> getColors(int levelNumber) {
        int[] shapeColors = getResources().getIntArray(R.array.game19_colors);
        List<Integer> colors = new ArrayList();
        for (int shapeColor : shapeColors) {
            colors.add(Integer.valueOf(shapeColor));
        }
        GameRandom.shuffle(colors);
        return colors.subList(0, 6);
    }

    private int getGuessElementCount(int levelNumber) {
        if (levelNumber <= 7) {
            return 1;
        }
        return 2;
    }

    private int getElementCount(int levelNumber) {
        return levelNumber + 2;
    }

    private Pair<Integer, Integer> getFieldSize(int levelNumber) {
        int y;
        int x;
        if (levelNumber <= 5) {
            y = 3;
            x = 3;
        } else if (levelNumber <= 8) {
            y = 4;
            x = 4;
        } else if (levelNumber <= 12) {
            y = 5;
            x = 5;
        } else {
            x = 5;
            y = 7;
        }
        return new Pair(Integer.valueOf(x), Integer.valueOf(y));
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
                Game19FindAllActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        if ((Game19FindAllActivity.this.gameTime - (System.currentTimeMillis() - Game19FindAllActivity.this.startedTime)) + Game19FindAllActivity.this.pausedDuration > 0) {
                            Game19FindAllActivity.this.startNextLevel();
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
