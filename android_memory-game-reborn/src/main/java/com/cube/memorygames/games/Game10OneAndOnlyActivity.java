package com.cube.memorygames.games;

import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Handler;
import android.support.v7.app.AlertDialog.Builder;
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
import com.cube.memorygames.ui.Game10Grid;
import com.memory.brain.training.games.R;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimerTask;

public class Game10OneAndOnlyActivity extends Game1MemoryGridActivity {
    private static final int GAME_TIME = 60000;
    private static final int MAX_LEVEL = 46;
    private long gameTime;
    private long startedTime;
    private Handler timerHandler = new Handler();
    private Runnable timerRunnable = new Runnable() {
        public void run() {
            long currentPausedDuration = 0;
            if (Game10OneAndOnlyActivity.this.pausedTime != 0) {
                currentPausedDuration = System.currentTimeMillis() - Game10OneAndOnlyActivity.this.pausedTime;
            }
            final long time = ((Game10OneAndOnlyActivity.this.gameTime - (System.currentTimeMillis() - Game10OneAndOnlyActivity.this.startedTime)) + Game10OneAndOnlyActivity.this.pausedDuration) + currentPausedDuration;
            if (time > 0) {
                Game10OneAndOnlyActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        Game10OneAndOnlyActivity.this.updateTimerText(time);
                    }
                });
                Game10OneAndOnlyActivity.this.timerHandler.postDelayed(this, 20);
                return;
            }
            Game10OneAndOnlyActivity.this.startedTime = 0;
            Game10OneAndOnlyActivity.this.progressBar.setVisibility(4);
            if (!Game10OneAndOnlyActivity.this.isFinishing()) {
                Game10OneAndOnlyActivity.this.showTimeout(true);
            }
        }
    };

    protected class StatGameFlowState implements GameFlowState {
        protected StatGameFlowState() {
        }

        public void applyState() {
            Game10OneAndOnlyActivity.this.grid.hideChallengeCells();
            Game10OneAndOnlyActivity.this.grid.enableAllCells();
            Game10OneAndOnlyActivity.this.timerContainer.setVisibility(8);
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
                Game10OneAndOnlyActivity.this.openMainMenu();
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
        Game10Grid game10Grid = new Game10Grid(this);
        game10Grid.setElements(getGroups(this.progression.getLevelNumber()));
        game10Grid.setGridEventsListener(this);
        game10Grid.hideChallengeCells();
        LayoutParams params = new LayoutParams(-1, -1);
        params.addRule(13, -1);
        this.gridContainer.addView(game10Grid, 0, params);
        this.grid = game10Grid;
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

    private List<Element> getGroups(int levelNumber) {
        int i;
        if (levelNumber > 46) {
            levelNumber = 46;
        }
        int numberOfGroups = getNumberOfGroups(levelNumber);
        int groupElements = levelNumber + 1;
        int[] groups = new int[(numberOfGroups + 1)];
        Arrays.fill(groups, 2);
        groups[0] = 1;
        for (groupElements -= numberOfGroups * 2; groupElements > 0; groupElements--) {
            int nextInt = GameRandom.nextInt(numberOfGroups) + 1;
            groups[nextInt] = groups[nextInt] + 1;
        }
        List<Element> randomElements = new ArrayList();
        List<Integer> colors = getColors(levelNumber);
        for (i = 0; i < colors.size(); i++) {
            int j;
            for (j = 0; j < 4; j++) {
                randomElements.add(new Element(((Integer) colors.get(i)).intValue(), j));
            }
        }
        do {
            GameRandom.shuffle(randomElements);
        } while (!isCorrectSet(randomElements, groups.length, levelNumber));
        ((Element) randomElements.get(0)).setWin(true);
        List<Element> result = new ArrayList();
        int j;
        for (i = 0; i < groups.length; i++) {
            for (j = 0; j < groups[i]; j++) {
                result.add(randomElements.get(i));
            }
        }
        GameRandom.shuffle(result);
        return result;
    }

    private boolean isCorrectSet(List<Element> randomElements, int groupsCount, int levelNumber) {
        boolean isColor;
        boolean isShape;
        int numberOfColors;
        Element winElement = (Element) randomElements.get(0);
        if (levelNumber < 7) {
            isColor = true;
        } else {
            isColor = false;
        }
        if (levelNumber < 12) {
            isShape = true;
        } else {
            isShape = false;
        }
        Set<Integer> colors = new HashSet();
        colors.add(Integer.valueOf(winElement.getColor()));
        for (int i = 1; i < groupsCount; i++) {
            colors.add(Integer.valueOf(((Element) randomElements.get(i)).getColor()));
            if (winElement.getColor() == ((Element) randomElements.get(i)).getColor()) {
                isColor = true;
            }
            if (winElement.getFigure() == ((Element) randomElements.get(i)).getFigure()) {
                isShape = true;
            }
        }
        if (levelNumber <= 3) {
            numberOfColors = 2;
        } else {
            numberOfColors = 3;
        }
        boolean isColorCount;
        if (colors.size() == numberOfColors) {
            isColorCount = true;
        } else {
            isColorCount = false;
        }
        if (isColor && isShape && isColorCount) {
            return true;
        }
        return false;
    }

    private List<Integer> getColors(int levelNumber) {
        int numberOfColors;
        if (levelNumber <= 3) {
            numberOfColors = 2;
        } else {
            numberOfColors = 3;
        }
        int[] shapeColors = getResources().getIntArray(R.array.game10_colors);
        List<Integer> colors = new ArrayList();
        for (int shapeColor : shapeColors) {
            colors.add(Integer.valueOf(shapeColor));
        }
        GameRandom.shuffle(colors);
        return colors.subList(0, numberOfColors);
    }

    private int getNumberOfGroups(int levelNumber) {
        if (levelNumber <= 2) {
            return 1;
        }
        if (levelNumber <= 5) {
            return 2;
        }
        if (levelNumber <= 10) {
            return 3;
        }
        return 4;
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
                Game10OneAndOnlyActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        if ((Game10OneAndOnlyActivity.this.gameTime - (System.currentTimeMillis() - Game10OneAndOnlyActivity.this.startedTime)) + Game10OneAndOnlyActivity.this.pausedDuration > 0) {
                            Game10OneAndOnlyActivity.this.startNextLevel();
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
