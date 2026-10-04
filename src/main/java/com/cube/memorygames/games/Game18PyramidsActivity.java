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
import com.cube.memorygames.ui.Game18Grid;
import com.memory.brain.training.games.R;
import com.mopub.volley.DefaultRetryPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.TimerTask;

public class Game18PyramidsActivity extends Game1MemoryGridActivity {
    public static final int FIGURE_COUNT = 3;
    private static final int GAME_TIME = 60000;
    public static final int MAX_CIRCLES = 8;
    private long gameTime;
    private long startedTime;
    private Handler timerHandler = new Handler();
    private Runnable timerRunnable = new Runnable() {
        public void run() {
            long currentPausedDuration = 0;
            if (Game18PyramidsActivity.this.pausedTime != 0) {
                currentPausedDuration = System.currentTimeMillis() - Game18PyramidsActivity.this.pausedTime;
            }
            final long time = ((Game18PyramidsActivity.this.gameTime - (System.currentTimeMillis() - Game18PyramidsActivity.this.startedTime)) + Game18PyramidsActivity.this.pausedDuration) + currentPausedDuration;
            if (time > 0) {
                Game18PyramidsActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        Game18PyramidsActivity.this.updateTimerText(time);
                    }
                });
                Game18PyramidsActivity.this.timerHandler.postDelayed(this, 20);
                return;
            }
            Game18PyramidsActivity.this.startedTime = 0;
            Game18PyramidsActivity.this.progressBar.setVisibility(4);
            if (!Game18PyramidsActivity.this.isFinishing()) {
                Game18PyramidsActivity.this.showTimeout(true);
            }
        }
    };

    protected class StatGameFlowState implements GameFlowState {
        protected StatGameFlowState() {
        }

        public void applyState() {
            Game18PyramidsActivity.this.grid.hideChallengeCells();
            Game18PyramidsActivity.this.grid.enableAllCells();
            Game18PyramidsActivity.this.enablePausePanel();
            Game18PyramidsActivity.this.timerContainer.setVisibility(8);
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
                Game18PyramidsActivity.this.openMainMenu();
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
        Game18Grid game18Grid = new Game18Grid(this);
        game18Grid.setElements(getCirclesColors(this.progression.getLevelNumber()), getSizes(this.progression.getLevelNumber()), GameRandom.nextInt(3));
        game18Grid.setGridEventsListener(this);
        game18Grid.hideChallengeCells();
        LayoutParams params = new LayoutParams(-1, -1);
        params.addRule(13, -1);
        this.gridContainer.addView(game18Grid, 0, params);
        this.grid = game18Grid;
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

    private List<Float> getSizes(int levelNumber) {
        int numberOfCircles = getNumberOfCircles(levelNumber);
        List<Float> sizes = new ArrayList();
        sizes.add(Float.valueOf(DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        float oneStep = DefaultRetryPolicy.DEFAULT_BACKOFF_MULT / ((float) numberOfCircles);
        for (int i = 1; i < numberOfCircles; i++) {
            sizes.add(Float.valueOf((((((float) numberOfCircles) - ((float) i)) / ((float) numberOfCircles)) + (oneStep / 2.0f)) - ((float) ((Math.random() * ((double) oneStep)) * 0.8d))));
        }
        return sizes;
    }

    private List<List<Integer>> getCirclesColors(int levelNumber) {
        int numberOfCircles = getNumberOfCircles(levelNumber);
        List<List<Integer>> result = new ArrayList();
        List<Integer> colors = getCircleColors(numberOfCircles);
        for (int i = 0; i < 3; i++) {
            do {
                GameRandom.shuffle(colors);
            } while (!isValidColors(colors, result, levelNumber));
            List<Integer> nextCircle = new ArrayList();
            nextCircle.addAll(colors);
            result.add(nextCircle);
        }
        return result;
    }

    private boolean isValidColors(List<Integer> colors, List<List<Integer>> alreadyAddedColors, int levelNumber) {
        int i;
        for (i = 1; i < colors.size(); i++) {
            if (((Integer) colors.get(i)).equals(colors.get(i - 1))) {
                return false;
            }
        }
        for (List<Integer> alreadyAddedColor : alreadyAddedColors) {
            if (levelNumber > 5) {
                if (levelNumber <= 8) {
                    if (!((Integer) colors.get(colors.size() - 1)).equals(alreadyAddedColor.get(colors.size() - 1))) {
                        return false;
                    }
                } else if (levelNumber <= 18) {
                    if (!((Integer) colors.get(0)).equals(alreadyAddedColor.get(0))) {
                        return false;
                    }
                    if (!((Integer) colors.get(colors.size() - 1)).equals(alreadyAddedColor.get(colors.size() - 1))) {
                        return false;
                    }
                } else if (!((Integer) colors.get(colors.size() - 2)).equals(alreadyAddedColor.get(colors.size() - 2))) {
                    return false;
                } else {
                    if (!((Integer) colors.get(colors.size() - 1)).equals(alreadyAddedColor.get(colors.size() - 1))) {
                        return false;
                    }
                    if (!((Integer) colors.get(0)).equals(alreadyAddedColor.get(0))) {
                        return false;
                    }
                }
            }
            i = 0;
            while (i < colors.size() && ((Integer) colors.get(i)).equals(alreadyAddedColor.get(i))) {
                if (i == colors.size() - 1) {
                    return false;
                }
                i++;
            }
        }
        return true;
    }

    private List<Integer> getCircleColors(int numberOfCircles) {
        int[] arrayColors = getResources().getIntArray(R.array.game18_colors);
        List<Integer> colors = new ArrayList();
        for (int color : arrayColors) {
            colors.add(Integer.valueOf(color));
        }
        List<Integer> result = new ArrayList();
        GameRandom.shuffle(colors);
        result.addAll(colors);
        GameRandom.shuffle(colors);
        result.addAll(colors);
        return result.subList(0, numberOfCircles);
    }

    private int getNumberOfCircles(int levelNumber) {
        if (levelNumber <= 1) {
            return 3;
        }
        if (levelNumber <= 3) {
            return 3;
        }
        if (levelNumber <= 7) {
            return 4;
        }
        if (levelNumber <= 13) {
            return 5;
        }
        if (levelNumber <= 18) {
            return 6;
        }
        if (levelNumber <= 23) {
            return 7;
        }
        return 8;
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
        disablePausePanel();
        this.stateTimer.scheduleAndRunTask(new TimerTask() {
            public void run() {
                Game18PyramidsActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        if ((Game18PyramidsActivity.this.gameTime - (System.currentTimeMillis() - Game18PyramidsActivity.this.startedTime)) + Game18PyramidsActivity.this.pausedDuration > 0) {
                            Game18PyramidsActivity.this.startNextLevel();
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
