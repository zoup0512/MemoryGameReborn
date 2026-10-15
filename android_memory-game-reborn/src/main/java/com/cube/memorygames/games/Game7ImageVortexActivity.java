package com.cube.memorygames.games;

import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.os.CountDownTimer;
import android.os.Handler;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout.LayoutParams;
import com.cube.memorygames.MemoryApplicationModel;
import com.cube.memorygames.SoundUtils;
import com.cube.memorygames.SoundUtils.SOUND;
import com.cube.memorygames.api.local.model.LocalGameSession;
import com.cube.memorygames.logic.GameFlowState;
import com.cube.memorygames.logic.GameFlowStateTimer;
import com.cube.memorygames.logic.GameProgression7;
import com.cube.memorygames.logic.GameRandom;
import com.cube.memorygames.ui.ImageVortexGrid;
import com.memory.brain.training.games.R;
import java.util.ArrayList;
import java.util.List;
import java.util.TimerTask;

public class Game7ImageVortexActivity extends Game1MemoryGridActivity {
    private static final int GAME_TIME = 61000;
    private int failCount = 0;
    private long gameTime;
    private CountDownTimer gameTimer;
    private Drawable nextCellImage;
    private List<Integer> remainingUnusedCellImageIds = new ArrayList();
    private long startedTime;
    private long timeLeft = 61000;
    private Handler timerHandler = new Handler();
    private Runnable timerRunnable = new Runnable() {
        public void run() {
            final long time = Game7ImageVortexActivity.this.gameTime - (System.currentTimeMillis() - Game7ImageVortexActivity.this.startedTime);
            if (time > 0) {
                Game7ImageVortexActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        Game7ImageVortexActivity.this.updateTimerText(time);
                    }
                });
                Game7ImageVortexActivity.this.timerHandler.postDelayed(this, 20);
                return;
            }
            Game7ImageVortexActivity.this.startedTime = 0;
            Game7ImageVortexActivity.this.progressBar.setVisibility(4);
            if (!Game7ImageVortexActivity.this.isFinishing()) {
                Game7ImageVortexActivity.this.killTimer();
                Game7ImageVortexActivity.this.showFailure();
            }
        }
    };
    private List<Drawable> usedCellImages = new ArrayList();

    private class GameTimer extends CountDownTimer {
        private GameTimerUIRunnable runnable = new GameTimerUIRunnable();

        private class GameTimerUIRunnable implements Runnable {
            private GameTimerUIRunnable() {
            }

            public void run() {
                Game7ImageVortexActivity.this.updateTimerText(Game7ImageVortexActivity.this.timeLeft);
            }
        }

        public GameTimer(long millisInFuture, long countDownInterval) {
            super(millisInFuture, countDownInterval);
            System.err.println("millisInFuture = " + millisInFuture);
        }

        public void onTick(long millisUntiFinish) {
            Game7ImageVortexActivity.this.timeLeft = millisUntiFinish;
            Game7ImageVortexActivity.this.runOnUiThread(this.runnable);
        }

        public void onFinish() {
            Game7ImageVortexActivity.this.progressBar.setVisibility(4);
            if (!Game7ImageVortexActivity.this.isFinishing() && Game7ImageVortexActivity.this.gameTimer != null) {
                Game7ImageVortexActivity.this.showTimeout(true);
            }
        }
    }

    private class HideAllCellsFlowState implements GameFlowState {
        private HideAllCellsFlowState() {
        }

        public void applyState() {
            Game7ImageVortexActivity.this.disablePausePanel();
            Game7ImageVortexActivity.this.grid.hideAllCells();
        }

        public int getDuration() {
            return 800;
        }
    }

    private class HideCellsAndShowChallengeFlowState implements GameFlowState {
        private HideCellsAndShowChallengeFlowState() {
        }

        public void applyState() {
            Game7ImageVortexActivity.this.timerContainer.setVisibility(8);
            Game7ImageVortexActivity.this.grid.hideAllCells();
            Game7ImageVortexActivity.this.grid.showChallengeCells();
            ((ImageVortexGrid) Game7ImageVortexActivity.this.grid).prepareForAnimation();
            Game7ImageVortexActivity.this.grid.enableAllCells();
            Game7ImageVortexActivity.this.enablePausePanel();
            Game7ImageVortexActivity.this.grid.animateCells();
        }

        public int getDuration() {
            return 800;
        }
    }

    protected boolean isEnableLevelTimer() {
        return false;
    }

    protected void onDestroy() {
        super.onDestroy();
        killTimer();
    }

    protected void createProgression() {
        this.progression = new GameProgression7();
    }

    protected void startGame() {
        this.remainingUnusedCellImageIds = new ArrayList(MemoryApplicationModel.getInstance().getAllCellDrawableIds());
        this.usedCellImages = new ArrayList();
        super.startGame();
    }

    protected void startLevel() {
        displayLevelNumber();
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int side = Math.min(size.x, size.y);
        LayoutParams params = new LayoutParams(side, side);
        params.addRule(13, -1);
        int currentWinCells = this.progression.getCurrentWinCells();
        ImageVortexGrid imageVortexGrid = new ImageVortexGrid(this, this.progression.getCurrentGridSize(), this.progression.getCurrentGridSize(), currentWinCells, side, side);
        this.gridContainer.addView(imageVortexGrid, 0, params);
        this.grid = imageVortexGrid;
        this.grid.setGridEventsListener(this);
        while (currentWinCells > this.usedCellImages.size()) {
            if (!this.remainingUnusedCellImageIds.isEmpty()) {
                int nextCellImageId = GameRandom.nextInt(this.remainingUnusedCellImageIds.size() - 1);
                this.nextCellImage = getResources().getDrawable(((Integer) this.remainingUnusedCellImageIds.get(nextCellImageId)).intValue());
                this.remainingUnusedCellImageIds.remove(nextCellImageId);
                this.usedCellImages.add(this.nextCellImage);
            }
        }
        this.grid.setDrawablesToUse(this.usedCellImages);
        this.grid.setUserEachDrawableOnlyOnce(true);
        this.grid.buildGrid();
        if (this.progression.getLevelNumber() == 1) {
            if (this.isOnlineGame) {
                this.timeLeft = 45000;
            } else {
                this.timeLeft = 61000;
            }
            disablePausePanel();
            this.grid.hideAllCells();
            this.grid.disableAllCells();
            this.grid.showChallengeCells();
            ((ImageVortexGrid) this.grid).prepareForAnimation();
            this.grid.animateCells();
            this.stateTimer.scheduleAndRunTask(new TimerTask() {
                public void run() {
                    Game7ImageVortexActivity.this.runOnUiThread(new Runnable() {
                        public void run() {
                            Game7ImageVortexActivity.this.grid.animateFinishCells();
                            Game7ImageVortexActivity.this.foreground.postDelayed(new Runnable() {
                                public void run() {
                                    Game7ImageVortexActivity game7ImageVortexActivity = Game7ImageVortexActivity.this;
                                    game7ImageVortexActivity.userScore += Game7ImageVortexActivity.this.getLevelScore(Game7ImageVortexActivity.this.progression.getLevelNumber());
                                    Game7ImageVortexActivity.this.startNextLevel();
                                }
                            }, 200);
                        }
                    });
                }
            }, 1000);
            initTimer();
            return;
        }
        this.stateTimer.start();
    }

    protected void exitFromPauseClicked() {
        this.progressBar.setVisibility(4);
    }

    private void initTimer() {
        this.progressBar.setVisibility(0);
        if (this.isOnlineGame) {
            this.progressBar.setMax(4500);
            this.gameTimer = new GameTimer(this.timeLeft, 20);
        } else {
            this.progressBar.setMax(6100);
            this.gameTimer = new GameTimer(this.timeLeft, 20);
        }
        this.gameTimer.start();
    }

    public void onSuccessCellClicked(int cellClickedIndex) {
        if (((ViewGroup) this.grid).getChildAt(cellClickedIndex).getBackground().equals(this.nextCellImage)) {
            disablePausePanel();
            this.userScore += getLevelScore(this.progression.getLevelNumber());
            updateOnlineScore();
            giveStars();
            this.textLevelReady.setText("");
            this.levelHint.setVisibility(0);
            this.timerContainer.setVisibility(0);
            this.stateTimer.scheduleAndRunTask(new TimerTask() {
                public void run() {
                    Game7ImageVortexActivity.this.runOnUiThread(new Runnable() {
                        public void run() {
                            Game7ImageVortexActivity.this.startNextLevel();
                        }
                    });
                }
            }, 800);
            SoundUtils.playSound(this, SOUND.WIN);
            ((ImageVortexGrid) this.grid).fillFinishCells(cellClickedIndex, this.nextCellImage, true);
            this.grid.animateFinishCells();
            return;
        }
        this.failCount++;
        if (!this.isOnlineGame) {
            killTimer();
            this.progressBar.setVisibility(4);
        }
        showFailure();
        ((ImageVortexGrid) this.grid).fillFinishCells(cellClickedIndex, this.nextCellImage, false);
    }

    protected void startNextLevel() {
        if (this.grid != null) {
            this.gridContainer.removeView((View) this.grid);
        }
        this.timerContainer.setVisibility(8);
        if (!this.isChallenge || this.progression.getLevelNumber() < this.challengeJsonGame.getLevel() - this.failCount) {
            this.progression.nextLevel();
            startLevel();
            return;
        }
        this.grid.disableAllCells();
        this.grid.showChallengeCells();
        this.grid.animateFinishCells();
        disablePausePanel();
        this.progressBar.setVisibility(4);
        killTimer();
        new Handler().postDelayed(new Runnable() {
            public void run() {
                if (!Game7ImageVortexActivity.this.isFinishing()) {
                    Game7ImageVortexActivity.this.enablePausePanel();
                    Game7ImageVortexActivity.this.showChallengeFinishDialog();
                }
            }
        }, 200);
    }

    protected void showPause() {
        killTimer();
    }

    protected void hidePause() {
        initTimer();
    }

    public void retryGame() {
        this.buyStarsClickedCountCount = 0;
        this.videoWatchedCount = 0;
        this.pausedDuration = 0;
        this.pausedTime = 0;
        this.pausedCount = 0;
        this.failCount = 0;
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
        this.remainingUnusedCellImageIds = new ArrayList(MemoryApplicationModel.getInstance().getAllCellDrawableIds());
        this.usedCellImages = new ArrayList();
        startLevel();
    }

    protected void createGameFlowStates() {
        List<GameFlowState> states = new ArrayList();
        states.add(new HideAllCellsFlowState());
        states.add(new HideCellsAndShowChallengeFlowState());
        this.stateTimer = new GameFlowStateTimer(states);
    }

    public void restartLevel(boolean takeMoney) {
        initTimer();
        super.restartLevel(takeMoney);
    }

    private void updateTimerText(long time) {
        this.progressBar.setProgress((int) (time / 10));
    }

    private void killTimer() {
        if (this.gameTimer != null) {
            this.gameTimer.cancel();
            this.gameTimer = null;
        }
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


}
