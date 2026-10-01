package com.cube.memorygames.games;

import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.graphics.Point;
import android.os.Handler;
import android.support.annotation.LayoutRes;
import android.support.v7.app.AlertDialog.Builder;
import android.view.Display;
import android.view.View;
import android.widget.RelativeLayout.LayoutParams;
import com.cube.memorygames.SharingDialog;
import com.cube.memorygames.SoundUtils;
import com.cube.memorygames.SoundUtils.SOUND;
import com.cube.memorygames.logic.GameFlowState;
import com.cube.memorygames.logic.GameFlowStateTimer;
import com.cube.memorygames.logic.GameProgression21;
import com.cube.memorygames.ui.SymmetryGrid;
import com.memory.brain.training.games.R;
import java.util.ArrayList;
import java.util.List;

public class Game21SymmetryActivity extends Game1MemoryGridActivity {
    private static final int GAME_TIME = 80000;
    protected static int miniLevelTime = 5700;
    private long gameTime;
    private long startedTime;
    private Handler timerHandler = new Handler();
    private Runnable timerRunnable = new Runnable() {
        public void run() {
            long currentPausedDuration = 0;
            if (Game21SymmetryActivity.this.pausedTime != 0) {
                currentPausedDuration = System.currentTimeMillis() - Game21SymmetryActivity.this.pausedTime;
            }
            final long time = ((Game21SymmetryActivity.this.gameTime - (System.currentTimeMillis() - Game21SymmetryActivity.this.startedTime)) + Game21SymmetryActivity.this.pausedDuration) + currentPausedDuration;
            if (time > 0) {
                Game21SymmetryActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        Game21SymmetryActivity.this.updateTimerText(time);
                    }
                });
                Game21SymmetryActivity.this.timerHandler.postDelayed(this, 20);
                return;
            }
            Game21SymmetryActivity.this.startedTime = 0;
            Game21SymmetryActivity.this.progressBar.setVisibility(4);
            if (!Game21SymmetryActivity.this.isFinishing()) {
                Game21SymmetryActivity.this.grid.animateFinishCells();
                Game21SymmetryActivity.this.showTimeout(true);
            }
        }
    };

    protected class GridAnimationFlowState implements GameFlowState {
        protected GridAnimationFlowState() {
        }

        public void applyState() {
            Game21SymmetryActivity.this.textLevelReady.setText(R.string.level_ready);
            Game21SymmetryActivity.this.levelHint.setVisibility(8);
            Game21SymmetryActivity.this.timerContainer.setVisibility(8);
            Game21SymmetryActivity.this.grid.animateCells();
        }

        public int getDuration() {
            return SharingDialog.DOLLAR1_COINS;
        }
    }

    public class ReadyFlowState implements GameFlowState {
        public void applyState() {
            Game21SymmetryActivity.this.levelHint.setVisibility(8);
            Game21SymmetryActivity.this.enablePausePanel();
        }

        public int getDuration() {
            return 0;
        }
    }

    protected class ShowChallengeFlowState implements GameFlowState {
        protected ShowChallengeFlowState() {
        }

        public void applyState() {
            Game21SymmetryActivity.this.enablePausePanel();
            Game21SymmetryActivity.this.showChallenge();
        }

        public int getDuration() {
            return 0;
        }
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

    protected void onDestroy() {
        super.onDestroy();
        killTimer();
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
                Game21SymmetryActivity.this.confirmBackPress();
                dialog.dismiss();
            }
        });
        builder.show();
    }

    protected void exitFromPauseClicked() {
        retryFromPauseClicked();
        this.grid.animateFinishCells();
    }

    protected void retryFromPauseClicked() {
        this.progressBar.setVisibility(4);
        this.startedTime = 0;
        this.timerHandler.removeCallbacks(this.timerRunnable);
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

    public void onSuccessCellClicked(int lastAddedCellClicked) {
        SoundUtils.playSound(this, SOUND.TAP);
        if (this.grid.getCurrentSuccessCellsClicked() >= this.grid.getSuccessCells()) {
            this.userScore += getLevelScore(this.progression.getLevelNumber());
            this.foreground.setVisibility(8);
            this.panelPause.setVisibility(0);
            this.pauseButton.clearColorFilter();
            this.grid.animateFinishCells();
            this.foreground.postDelayed(new Runnable() {
                public void run() {
                    Game21SymmetryActivity.this.startNextLevel();
                }
            }, 500);
            this.grid.disableAllCells();
            SoundUtils.playSound(this, SOUND.WIN);
        }
    }

    protected void startLevel() {
        displayLevelNumber();
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int sideX = Math.min(size.x, size.y);
        int sideY = Math.min(size.x, size.y);
        Point point = getGridSize(this.progression.getLevelNumber());
        if (((double) point.x) < 5.0d) {
            sideX = (int) ((((double) sideX) / 5.0d) * ((double) point.x));
            sideY = (int) ((((double) sideY) / 5.0d) * ((double) point.y));
        }
        LayoutParams params = new LayoutParams(sideX, sideY);
        params.addRule(13, -1);
        this.grid = new SymmetryGrid(this, point.x, point.y, this.progression.getCurrentWinCells(), sideX, sideY);
        ((SymmetryGrid) this.grid).setShowAnimation(true);
        this.gridContainer.addView((View) this.grid, 0, params);
        this.grid.setGridEventsListener(this);
        this.grid.buildGrid();
        if (this.startedTime <= 0) {
            this.progressBar.setVisibility(0);
            this.startedTime = System.currentTimeMillis();
            if (this.isChallenge) {
                if (this.progression.getLevelNumber() == getStartingLevel()) {
                    this.gameTime = (long) (((this.challengeJsonGame.getLevel() - getStartingLevel()) + 1) * miniLevelTime);
                    this.progressBar.setMax((int) (this.gameTime / 10));
                }
            } else if (this.progression.getLevelNumber() == 1) {
                if (this.isOnlineGame) {
                    this.gameTime = 45000;
                    this.progressBar.setMax(4500);
                } else {
                    this.gameTime = 80000;
                    this.progressBar.setMax(8000);
                }
            }
            this.timerHandler.postDelayed(this.timerRunnable, 0);
        }
        this.stateTimer.start();
    }

    protected void createProgression() {
        this.progression = new GameProgression21();
    }

    private Point getGridSize(int level) {
        int width;
        int height;
        switch (level) {
            case 1:
            case 2:
                width = 2;
                height = 3;
                break;
            case 3:
            case 4:
                width = 2;
                height = 4;
                break;
            case 5:
            case 6:
            case 7:
            case 8:
                width = 3;
                height = 5;
                break;
            case 9:
            case 10:
                width = 3;
                height = 6;
                break;
            case 11:
            case 12:
            case 13:
                width = 4;
                height = 7;
                break;
            default:
                width = 4;
                height = 8;
                break;
        }
        return new Point(width * 2, height);
    }

    public void setContentView(@LayoutRes int layoutResID) {
        super.setContentView((int) R.layout.activity_game21);
    }

    protected void createGameFlowStates() {
        List<GameFlowState> states = new ArrayList();
        states.add(new ReadyFlowState());
        states.add(new GridAnimationFlowState());
        states.add(new ShowChallengeFlowState());
        this.stateTimer = new GameFlowStateTimer(states);
    }
}
