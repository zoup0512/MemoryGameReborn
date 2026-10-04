package com.cube.memorygames.games;

import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.graphics.Point;
import android.os.Handler;
import android.support.v7.app.AlertDialog.Builder;
import android.view.Display;
import android.view.View;
import android.widget.RelativeLayout.LayoutParams;
import com.cube.memorygames.MemoryApplicationModel;
import com.cube.memorygames.api.local.LocalDataManager;
import com.cube.memorygames.api.local.model.LocalGameSession;
import com.cube.memorygames.logic.GameFlowState;
import com.cube.memorygames.logic.GameFlowStateTimer;
import com.cube.memorygames.logic.GameProgression4;
import com.cube.memorygames.ui.WhoNewGrid;
import com.memory.brain.training.games.R;
import java.util.ArrayList;
import java.util.List;

public class Game4WhoNewActivity extends Game1MemoryGridActivity {
    private int failCount = 0;
    private long gameTime;
    private int lastAddedSuccessCellIndex;
    private long startedTime;
    private Handler timerHandler = new Handler();
    private Runnable timerRunnable = new Runnable() {
        public void run() {
            final long time = Game4WhoNewActivity.this.gameTime - (System.currentTimeMillis() - Game4WhoNewActivity.this.startedTime);
            if (time > 0) {
                Game4WhoNewActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        Game4WhoNewActivity.this.updateTimerText(time);
                    }
                });
                Game4WhoNewActivity.this.timerHandler.postDelayed(this, 20);
                return;
            }
            Game4WhoNewActivity.this.startedTime = 0;
            Game4WhoNewActivity.this.progressBar.setVisibility(4);
            if (!Game4WhoNewActivity.this.isFinishing()) {
                Game4WhoNewActivity.this.progressBar.setVisibility(4);
                Game4WhoNewActivity.this.killTimer();
                Game4WhoNewActivity.this.showFailure();
            }
        }
    };

    private class AddNewCellFlowState implements GameFlowState {
        private AddNewCellFlowState() {
        }

        public void applyState() {
            Game4WhoNewActivity.this.grid.hideChallengeCells();
            Game4WhoNewActivity.this.lastAddedSuccessCellIndex = Game4WhoNewActivity.this.grid.addSuccessCell();
        }

        public int getDuration() {
            return 800;
        }
    }

    protected class ShowChallengeAndEnableSuccessCellsFlowState implements GameFlowState {
        protected ShowChallengeAndEnableSuccessCellsFlowState() {
        }

        public void applyState() {
            Game4WhoNewActivity.this.grid.showChallengeCells();
            Game4WhoNewActivity.this.grid.enableSuccessCells();
            Game4WhoNewActivity.this.enablePausePanel();
        }

        public int getDuration() {
            return 0;
        }
    }

    protected class ShowChallengeFlowState implements GameFlowState {
        protected ShowChallengeFlowState() {
        }

        public void applyState() {
            Game4WhoNewActivity.this.disablePausePanel();
            Game4WhoNewActivity.this.showChallenge();
        }

        public int getDuration() {
            return Game4WhoNewActivity.this.challengeDuration;
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

    private void confirmBackPress() {
        killTimer();
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
                Game4WhoNewActivity.this.openMainMenu();
                dialog.dismiss();
            }
        });
        builder.show();
    }

    protected void onDestroy() {
        super.onDestroy();
        killTimer();
    }

    protected void createProgression() {
        this.progression = new GameProgression4();
    }

    protected void startLevel() {
        displayLevelNumber();
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int side = Math.min(size.x, size.y);
        if (((double) this.progression.getCurrentGridSize()) < 5.0d) {
            side = (int) ((((double) side) / 5.0d) * ((double) this.progression.getCurrentGridSize()));
        }
        LayoutParams params = new LayoutParams(side, side);
        params.addRule(13, -1);
        this.grid = new WhoNewGrid(this, this.progression.getCurrentGridSize(), this.progression.getCurrentGridSize(), this.progression.getCurrentWinCells(), side, side);
        this.gridContainer.addView((View) this.grid, 0, params);
        this.grid.setGridEventsListener(this);
        this.grid.buildGrid();
        this.stateTimer.start();
    }

    protected void startNextLevel() {
        if (!this.isChallenge || this.progression.getLevelNumber() < this.challengeJsonGame.getLevel() - this.failCount) {
            this.progression.nextLevel();
            this.stateTimer.start();
            displayLevelNumber();
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
                if (!Game4WhoNewActivity.this.isFinishing()) {
                    Game4WhoNewActivity.this.enablePausePanel();
                    Game4WhoNewActivity.this.showChallengeFinishDialog();
                }
            }
        }, 200);
    }

    public void onSuccessCellClicked(int cellClickedIndex) {
        if (cellClickedIndex == this.lastAddedSuccessCellIndex) {
            showWin();
            return;
        }
        this.failCount++;
        showFailure();
    }

    protected void createGameFlowStates() {
        List<GameFlowState> states = new ArrayList();
        states.add(new ShowChallengeFlowState());
        states.add(new AddNewCellFlowState());
        states.add(new ShowChallengeAndEnableSuccessCellsFlowState());
        this.stateTimer = new GameFlowStateTimer(states);
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
        startLevel();
    }

    protected void clearWrongCells() {
        this.grid.clearWrongCells();
    }

    public void restartLevel(boolean takeMoney) {
        this.timerContainer.setVisibility(8);
        this.grid.clearWrongCells();
        this.stateTimer.start();
        displayLevelNumber();
        if (takeMoney) {
            this.gameSession.moneyPaid += 30;
            this.localDataManager.addCoinsTransaction(LocalDataManager.TYPE_BUY_LEVEL, -30);
        }
    }

    protected int giveStars() {
        int newStars;
        if (this.isChallenge || this.isOnlineGame || this.isWorkout) {
            newStars = 0;
        } else if (this.progression.getLevelNumber() <= 3) {
            newStars = 0;
        } else {
            newStars = 1;
        }
        if (newStars > 0) {
            this.gameSession.moneyEarned += newStars;
            this.localDataManager.addCoinsTransaction(LocalDataManager.TYPE_GAME_EARNED, newStars);
        }
        return newStars;
    }
}
