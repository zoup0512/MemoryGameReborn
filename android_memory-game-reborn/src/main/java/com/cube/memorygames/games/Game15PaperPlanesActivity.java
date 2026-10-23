package com.cube.memorygames.games;

import android.os.Handler;
import androidx.annotation.LayoutRes;
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
import com.cube.memorygames.logic.GameRandom;
import com.cube.memorygames.ui.Game15Grid;
import com.cube.memorygames.ui.Game15Grid.GameType;
import com.cube.memorygames.ui.Game15Grid.Side;
import com.github.florent37.viewanimator.AnimationListener.Start;
import com.github.florent37.viewanimator.AnimationListener.Stop;
import com.github.florent37.viewanimator.ViewAnimator;
import com.memory.brain.training.games.R;
import java.util.ArrayList;
import java.util.List;

public class Game15PaperPlanesActivity extends Game1MemoryGridActivity {
    @BindView(R.id.correct_hint)
    View correctHint;
    private long gameTime = 30000;
    private Side movingClouds;
    private long startedTime;
    private Handler timerHandler = new Handler();
    private Runnable timerRunnable = new Runnable() {
        public void run() {
            long currentPausedDuration = 0;
            if (Game15PaperPlanesActivity.this.pausedTime != 0) {
                currentPausedDuration = System.currentTimeMillis() - Game15PaperPlanesActivity.this.pausedTime;
            }
            final long time = ((Game15PaperPlanesActivity.this.gameTime - (System.currentTimeMillis() - Game15PaperPlanesActivity.this.startedTime)) + Game15PaperPlanesActivity.this.pausedDuration) + currentPausedDuration;
            if (time > 0) {
                Game15PaperPlanesActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        Game15PaperPlanesActivity.this.updateTimerText(time);
                    }
                });
                Game15PaperPlanesActivity.this.timerHandler.postDelayed(this, 50);
                return;
            }
            Game15PaperPlanesActivity.this.startedTime = 0;
            Game15PaperPlanesActivity.this.progressBar.setVisibility(4);
            if (!Game15PaperPlanesActivity.this.isFinishing()) {
                Game15PaperPlanesActivity.this.showTimeout(true);
            }
        }
    };

    protected class StatGameFlowState implements GameFlowState {
        protected StatGameFlowState() {
        }

        public void applyState() {
            Game15PaperPlanesActivity.this.grid.hideChallengeCells();
            Game15PaperPlanesActivity.this.grid.enableAllCells();
            Game15PaperPlanesActivity.this.timerContainer.setVisibility(8);
        }

        public int getDuration() {
            return 0;
        }
    }

    protected boolean isEnableLevelTimer() {
        return false;
    }

    public void setContentView(@LayoutRes int layoutResID) {
        super.setContentView((int) R.layout.activity_game15);
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
        boolean differentSpeed;
        displayLevelNumber();
        Game15Grid game15Grid = new Game15Grid(this);
        game15Grid.setGridEventsListener(this);
        if (this.progression.getLevelNumber() > 10) {
            differentSpeed = true;
        } else {
            differentSpeed = false;
        }
        if (this.progression.getLevelNumber() > 15 && this.movingClouds == null) {
            this.movingClouds = GameRandom.nextBoolean() ? Side.LEFT : Side.RIGHT;
        }
        game15Grid.setGameParams(GameType.randomGameType(), Side.randomSide(), Side.randomSide(), this.movingClouds, differentSpeed);
        game15Grid.hideChallengeCells();
        LayoutParams params = new LayoutParams(-1, -1);
        params.addRule(13, -1);
        this.gridContainer.addView(game15Grid, 0, params);
        this.grid = game15Grid;
        if (this.startedTime <= 0) {
            this.progressBar.setVisibility(0);
            this.progressBar.setMax(res.facebook);
            this.startedTime = System.currentTimeMillis();
            if (this.progression.getLevelNumber() == 1) {
                this.gameTime = 30000;
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
        if (((Game15Grid) this.grid).getHintPosition() != null) {
            ViewAnimator.animate(this.correctHint).alpha(0.8f, 0.8f, 0.0f).duration(500).onStart(new Start() {
                public void onStart() {
                    Game15PaperPlanesActivity.this.correctHint.setVisibility(0);
                }
            }).onStop(new Stop() {
                public void onStop() {
                    Game15PaperPlanesActivity.this.correctHint.setVisibility(8);
                }
            }).start();
        }
        this.userScore += getLevelScore(this.progression.getLevelNumber());
        giveStars();
        updateOnlineScore();
        if ((this.gameTime - (System.currentTimeMillis() - this.startedTime)) + this.pausedDuration > 0) {
            startNextLevel();
        }
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
        this.movingClouds = null;
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
