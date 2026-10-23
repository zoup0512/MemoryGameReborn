package com.cube.memorygames.games;

import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.LayoutRes;
import android.view.Display;
import android.view.View;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TextView;
import butterknife.BindView;
import com.cube.memorygames.MemoryApplicationModel;
import com.cube.memorygames.SoundUtils;
import com.cube.memorygames.SoundUtils.SOUND;
import com.cube.memorygames.api.local.LocalDataManager;
import com.cube.memorygames.api.local.model.LocalGameSession;
import com.cube.memorygames.logic.GameFlowState;
import com.cube.memorygames.logic.GameFlowStateTimer;
import com.cube.memorygames.logic.GameProgression3;
import com.cube.memorygames.ui.grids.TwoFoldGrid;
import com.facebook.appevents.AppEventsConstants;
import com.github.florent37.viewanimator.AnimationListener.Stop;
import com.github.florent37.viewanimator.ViewAnimator;
import com.memory.brain.training.games.R;
import com.mopub.volley.DefaultRetryPolicy;
import java.util.ArrayList;
import java.util.List;

public class Game17_248Activity extends Game1MemoryGridActivity {
    private static final int GAME_TIME = 60000;
    private static final long FIRST_COIN_SCORE = 1024L;
    @BindView(R.id.currentScore)
    TextView currentScoreText;
    private long currentScoreValue;
    private long gameTime;
    private long startedTime;
    private Handler timerHandler = new Handler();
    private Runnable timerRunnable = new Runnable() {
        public void run() {
            long currentPausedDuration = 0;
            if (Game17_248Activity.this.pausedTime != 0) {
                currentPausedDuration = System.currentTimeMillis() - Game17_248Activity.this.pausedTime;
            }
            final long time = ((Game17_248Activity.this.gameTime - (System.currentTimeMillis() - Game17_248Activity.this.startedTime)) + Game17_248Activity.this.pausedDuration) + currentPausedDuration;
            if (time > 0) {
                Game17_248Activity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        Game17_248Activity.this.updateTimerText(time);
                    }
                });
                Game17_248Activity.this.timerHandler.postDelayed(this, 20);
                return;
            }
            Game17_248Activity.this.startedTime = 0;
            Game17_248Activity.this.progressBar.setVisibility(4);
            if (!Game17_248Activity.this.isFinishing()) {
                int maxLevel;
                int i;
                if (Game17_248Activity.this.isChallenge) {
                    maxLevel = (int) (1 + (Game17_248Activity.this.totalScore / 100));
                    for (i = 1; i < maxLevel; i++) {
                        Game17_248Activity.this.progression.nextLevel();
                    }
                    Game17_248Activity.this.userScore = (int) (Game17_248Activity.this.totalScore / 10);
                    Game17_248Activity.this.showTimeout(true);
                    return;
                }
                Game17_248Activity.this.levelNumber.setText(Game17_248Activity.this.getString(R.string.level_number_248, new Object[]{Long.valueOf(1 + (Game17_248Activity.this.totalScore / 100)), Long.valueOf(Game17_248Activity.this.totalScore)}));
                maxLevel = (int) (1 + (Game17_248Activity.this.totalScore / 100));
                Game17_248Activity.this.gameSession.endLevel = Math.max(maxLevel, Game17_248Activity.this.gameSession.endLevel);
                Game17_248Activity.this.saveGameSession();
                for (i = 1; i < Game17_248Activity.this.gameSession.endLevel; i++) {
                    Game17_248Activity.this.progression.nextLevel();
                }
                Game17_248Activity.this.showTimeout(true);
            }
        }
    };
    private long totalScore;
    @BindView(R.id.level_number)
    TextView totalScoreTextView;

    protected class StatGameFlowState implements GameFlowState {
        protected StatGameFlowState() {
        }

        public void applyState() {
            Game17_248Activity.this.grid.hideChallengeCells();
            Game17_248Activity.this.grid.enableAllCells();
            Game17_248Activity.this.timerContainer.setVisibility(8);
        }

        public int getDuration() {
            return 0;
        }
    }

    public void setContentView(@LayoutRes int layoutResID) {
        super.setContentView((int) R.layout.activity_game17);
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.panelLives.setVisibility(4);
    }

    protected void startGame() {
        super.startGame();
        this.totalScore = 0;
        this.totalScoreTextView.setText(getString(R.string.score_format, this.totalScore));
    }

    protected void exitFromPauseClicked() {
        retryFromPauseClicked();
    }

    protected void retryFromPauseClicked() {
        this.progressBar.setVisibility(4);
        this.startedTime = 0;
        this.timerHandler.removeCallbacks(this.timerRunnable);
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
        if (this.progression.getLevelNumber() > this.gameSession.endLevel) {
            this.gameSession.endLevel = (int) (this.totalScore / 100);
        }
        saveGameSession();
        super.onBackPressed();
    }



    protected void onDestroy() {
        super.onDestroy();
        killTimer();
    }

    protected void startLevel() {
        if (this.isChallenge) {
            this.totalScoreTextView.setText(String.valueOf(this.userScore) + " / " + getMinPassScore());
        } else {
            this.totalScoreTextView.setText(getString(R.string.score_format, this.totalScore));
        }
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int side = Math.min(size.x, size.y);
        LayoutParams params = new LayoutParams(side, side);
        params.addRule(13, -1);
        this.grid = new TwoFoldGrid(this, side, side);
        this.gridContainer.addView((View) this.grid, 0, params);
        this.grid.setGridEventsListener(this);
        this.grid.buildGrid();
        if (this.startedTime <= 0) {
            this.progressBar.setVisibility(0);
            this.startedTime = System.currentTimeMillis();
            if (this.progression.getLevelNumber() == 1) {
                if (this.isOnlineGame) {
                    this.gameTime = 45000;
                    this.progressBar.setMax(4500);
                } else {
                    this.gameTime = 60000;
                    this.progressBar.setMax(6000);
                }
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
        if (lastAddedCellClicked <= 1) {
            this.currentScoreText.setVisibility(4);
            this.currentScoreText.setText(AppEventsConstants.EVENT_PARAM_VALUE_NO);
            this.currentScoreValue = 0;
            return;
        }
        this.currentScoreText.setVisibility(0);
        this.currentScoreValue = (long) calculateScore(lastAddedCellClicked);
        this.currentScoreText.setText(String.valueOf(this.currentScoreValue));
    }

    private int calculateScore(int sequenceLength) {
        if (sequenceLength < 10) {
            return (int) Math.pow(2.0d, (double) sequenceLength);
        }
        return ((sequenceLength - 9) * 512) + 512;
    }

    public void onFailCellClicked() {
        if (this.currentScoreValue != 0) {
            SoundUtils.playSound(this, SOUND.WIN);
            this.currentScoreText.setTranslationX(0.0f);
            this.currentScoreText.setTranslationY(0.0f);
            this.currentScoreText.setScaleX(DefaultRetryPolicy.DEFAULT_BACKOFF_MULT);
            this.currentScoreText.setScaleY(DefaultRetryPolicy.DEFAULT_BACKOFF_MULT);
            ViewAnimator.animate(this.currentScoreText).translationX((float) (((this.totalScoreTextView.getLeft() + this.totalScoreTextView.getWidth()) - this.currentScoreText.getLeft()) - (this.currentScoreText.getWidth() / 2))).translationY((float) (this.totalScoreTextView.getTop() - this.currentScoreText.getTop())).scale(DefaultRetryPolicy.DEFAULT_BACKOFF_MULT, 0.2f).duration(300).onStop(new Stop() {
                public void onStop() {
                    if (Game17_248Activity.this.isChallenge) {
                        Game17_248Activity.this.totalScore = Game17_248Activity.this.totalScore + Game17_248Activity.this.currentScoreValue;
                        Game17_248Activity.this.totalScoreTextView.setText(String.valueOf(Game17_248Activity.this.totalScore / 10));
                        Game17_248Activity.this.currentScoreText.setTranslationX(0.0f);
                        Game17_248Activity.this.currentScoreText.setTranslationY(0.0f);
                        Game17_248Activity.this.currentScoreText.setScaleX(DefaultRetryPolicy.DEFAULT_BACKOFF_MULT);
                        Game17_248Activity.this.currentScoreText.setScaleY(DefaultRetryPolicy.DEFAULT_BACKOFF_MULT);
                        Game17_248Activity.this.currentScoreText.setVisibility(4);
                        Game17_248Activity.this.currentScoreValue = 0;
                        if (((int) (Game17_248Activity.this.totalScore / 10)) >= Game17_248Activity.this.calculateGoalScore()) {
                            Game17_248Activity.this.startedTime = 0;
                            return;
                        }
                        return;
                    }
                    int oldCoinsValue = Game17_248Activity.this.getCoinsForScore(Game17_248Activity.this.totalScore);
                    Game17_248Activity.this.totalScore = Game17_248Activity.this.totalScore + Game17_248Activity.this.currentScoreValue;
                    Game17_248Activity.this.saveAndDisplayCoins(Game17_248Activity.this.getCoinsForScore(Game17_248Activity.this.totalScore) - oldCoinsValue);
                    Game17_248Activity.this.totalScoreTextView.setText(Game17_248Activity.this.getString(R.string.score_format, Game17_248Activity.this.totalScore));
                    Game17_248Activity.this.currentScoreText.setTranslationX(0.0f);
                    Game17_248Activity.this.currentScoreText.setTranslationY(0.0f);
                    Game17_248Activity.this.currentScoreText.setScaleX(DefaultRetryPolicy.DEFAULT_BACKOFF_MULT);
                    Game17_248Activity.this.currentScoreText.setScaleY(DefaultRetryPolicy.DEFAULT_BACKOFF_MULT);
                    Game17_248Activity.this.currentScoreText.setVisibility(4);
                    Game17_248Activity.this.currentScoreValue = 0;
                }
            }).start();
        }
    }

    private int getCoinsForScore(long score) {
        if (score < FIRST_COIN_SCORE) {
            return 0;
        }
        int result = 1;
        score -= FIRST_COIN_SCORE;
        for (int i = 0; i < ((int) (score / 512)); i++) {
            int i2;
            int i3 = result + 2;
            if (i > 3) {
                i2 = 3;
            } else {
                i2 = i;
            }
            result = i3 + i2;
        }
        return result;
    }

    private void saveAndDisplayCoins(int coins) {
        if (coins > 0 && !this.isOnlineGame && !this.isWorkout) {
            this.gameSession.moneyEarned += coins;
            this.localDataManager.addCoinsTransaction(LocalDataManager.TYPE_GAME_EARNED, coins);
        }
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
        this.totalScore = 0;
        if (this.grid != null) {
            this.gridContainer.removeView((View) this.grid);
        }
        this.timerContainer.setVisibility(8);
        startLevel();
    }

    protected void showOnlineReplayDialog() {
        updateOnlineScore();
        super.showOnlineReplayDialog();
    }

    protected void updateOnlineScore() {
        this.onlineScore += (int) (this.totalScore / 20);
    }

    protected int getLevelScore(int level) {
        return 10;
    }
}
