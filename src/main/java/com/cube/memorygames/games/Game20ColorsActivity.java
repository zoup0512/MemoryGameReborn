package com.cube.memorygames.games;

import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Handler;
import android.support.v7.app.AlertDialog.Builder;
import android.view.View;
import android.widget.RelativeLayout.LayoutParams;
import butterknife.BindView;
import com.cmcm.adsdk.Const.res;
import com.cube.memorygames.MemoryApplicationModel;
import com.cube.memorygames.SoundUtils;
import com.cube.memorygames.SoundUtils.SOUND;
import com.cube.memorygames.api.local.LocalDataManager;
import com.cube.memorygames.api.local.model.LocalGameSession;
import com.cube.memorygames.logic.GameFlowState;
import com.cube.memorygames.logic.GameFlowStateTimer;
import com.cube.memorygames.logic.GameProgression3;
import com.cube.memorygames.ui.Game20Grid;
import com.github.florent37.viewanimator.AnimationListener.Start;
import com.github.florent37.viewanimator.AnimationListener.Stop;
import com.github.florent37.viewanimator.ViewAnimator;
import com.memory.brain.training.games.R;
import com.mopub.volley.DefaultRetryPolicy;
import java.util.ArrayList;
import java.util.List;

public class Game20ColorsActivity extends Game1MemoryGridActivity {
    private static final int GAME_TIME = 30000;
    @BindView(R.id.correct_hint)
    View correctHint;
    private long gameTime = 30000;
    private long startedTime;
    private Handler timerHandler = new Handler();
    private Runnable timerRunnable = new Runnable() {
        public void run() {
            long currentPausedDuration = 0;
            if (Game20ColorsActivity.this.pausedTime != 0) {
                currentPausedDuration = System.currentTimeMillis() - Game20ColorsActivity.this.pausedTime;
            }
            final long time = ((Game20ColorsActivity.this.gameTime - (System.currentTimeMillis() - Game20ColorsActivity.this.startedTime)) + Game20ColorsActivity.this.pausedDuration) + currentPausedDuration;
            if (time > 0) {
                Game20ColorsActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        Game20ColorsActivity.this.updateTimerText(time);
                    }
                });
                Game20ColorsActivity.this.timerHandler.postDelayed(this, 50);
                return;
            }
            Game20ColorsActivity.this.startedTime = 0;
            Game20ColorsActivity.this.progressBar.setVisibility(4);
            if (!Game20ColorsActivity.this.isFinishing()) {
                Game20ColorsActivity.this.showTimeout(true);
            }
        }
    };

    protected class StatGameFlowState implements GameFlowState {
        protected StatGameFlowState() {
        }

        public void applyState() {
            Game20ColorsActivity.this.grid.hideChallengeCells();
            Game20ColorsActivity.this.grid.enableAllCells();
            Game20ColorsActivity.this.timerContainer.setVisibility(8);
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
                Game20ColorsActivity.this.confirmBackPress();
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
        Game20Grid game20Grid = (Game20Grid) this.grid;
        if (game20Grid == null) {
            game20Grid = new Game20Grid(this);
        }
        game20Grid.setGridEventsListener(this);
        game20Grid.startLevel();
        game20Grid.hideChallengeCells();
        LayoutParams params = new LayoutParams(-1, -1);
        params.addRule(13, -1);
        this.gridContainer.addView(game20Grid, 0, params);
        this.grid = game20Grid;
        if (this.startedTime <= 0) {
            this.progressBar.setVisibility(0);
            this.progressBar.setMax(res.facebook);
            this.startedTime = System.currentTimeMillis();
            if (this.progression.getLevelNumber() == 1) {
                this.gameTime = this.isOnlineGame
                        ? com.cube.memorygames.api.online.OnlineSession.get().roundDurationMs(this, 30000L) : 30000L;
            }
            this.timerHandler.postDelayed(this.timerRunnable, 0);
        }
        this.stateTimer.start();
    }

    protected void createGameFlowStates() {
        List<GameFlowState> states = new ArrayList();
        states.add(new StatGameFlowState());
        this.stateTimer = new GameFlowStateTimer(states);
    }

    public void onSuccessCellClicked(int lastAddedCellClicked) {
        ViewAnimator.animate(this.correctHint).alpha(DefaultRetryPolicy.DEFAULT_BACKOFF_MULT, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT, 0.0f).duration(500).onStart(new Start() {
            public void onStart() {
                Game20ColorsActivity.this.correctHint.setVisibility(0);
            }
        }).onStop(new Stop() {
            public void onStop() {
                Game20ColorsActivity.this.correctHint.setVisibility(8);
            }
        }).start();
        giveStars();
        this.userScore += getLevelScore(this.progression.getLevelNumber());
        updateOnlineScore();
        if ((this.gameTime - (System.currentTimeMillis() - this.startedTime)) + this.pausedDuration > 0) {
            startNextLevel();
        }
        SoundUtils.playSound(this, SOUND.WIN);
    }

    public void onFailCellClicked() {
        if (!this.isOnlineGame) {
            this.progressBar.setVisibility(4);
            this.gameTime -= System.currentTimeMillis() - this.startedTime;
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
