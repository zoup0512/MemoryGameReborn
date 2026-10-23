package com.cube.memorygames.games;

import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup.MarginLayoutParams;
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
import com.cube.memorygames.ui.Element;
import com.cube.memorygames.ui.Game16Grid;
import com.github.florent37.viewanimator.AnimationListener.Start;
import com.github.florent37.viewanimator.AnimationListener.Stop;
import com.github.florent37.viewanimator.ViewAnimator;
import com.memory.brain.training.games.R;
import java.util.ArrayList;
import java.util.List;
import java.util.TimerTask;

public class Game16LikePreviousActivity extends Game1MemoryGridActivity {
    @BindView(R.id.correct_hint)
    View correctHint;
    private long gameTime = 30000;
    private Element previousElement;
    private long startedTime;
    private Handler timerHandler = new Handler();
    private Runnable timerRunnable = new Runnable() {
        public void run() {
            long currentPausedDuration = 0;
            if (Game16LikePreviousActivity.this.pausedTime != 0) {
                currentPausedDuration = System.currentTimeMillis() - Game16LikePreviousActivity.this.pausedTime;
            }
            final long time = ((Game16LikePreviousActivity.this.gameTime - (System.currentTimeMillis() - Game16LikePreviousActivity.this.startedTime)) + Game16LikePreviousActivity.this.pausedDuration) + currentPausedDuration;
            if (time > 0) {
                Game16LikePreviousActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        Game16LikePreviousActivity.this.updateTimerText(time);
                    }
                });
                Game16LikePreviousActivity.this.timerHandler.postDelayed(this, 50);
                return;
            }
            Game16LikePreviousActivity.this.startedTime = 0;
            Game16LikePreviousActivity.this.progressBar.setVisibility(4);
            if (!Game16LikePreviousActivity.this.isFinishing()) {
                Game16LikePreviousActivity.this.showTimeout(true);
            }
        }
    };

    protected class StatGameFlowState implements GameFlowState {
        protected StatGameFlowState() {
        }

        public void applyState() {
            Game16LikePreviousActivity.this.grid.hideChallengeCells();
            Game16LikePreviousActivity.this.grid.enableAllCells();
            Game16LikePreviousActivity.this.timerContainer.setVisibility(8);
        }

        public int getDuration() {
            return 0;
        }
    }

    protected boolean isEnableLevelTimer() {
        return false;
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MarginLayoutParams layoutParams = (MarginLayoutParams) this.correctHint.getLayoutParams();
        layoutParams.setMargins(0, 0, 0, getResources().getDimensionPixelSize(R.dimen.game16_figure_margin));
        this.correctHint.setLayoutParams(layoutParams);
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
        Game16Grid game16Grid = (Game16Grid) this.grid;
        if (game16Grid == null) {
            game16Grid = new Game16Grid(this);
        }
        game16Grid.setGridEventsListener(this);
        game16Grid.hideChallengeCells();
        Element currentElement = getNextElement(this.previousElement, this.progression.getLevelNumber());
        game16Grid.setGameParams(this.previousElement, currentElement);
        this.previousElement = currentElement;
        LayoutParams params = new LayoutParams(-1, -1);
        params.addRule(13, -1);
        this.gridContainer.addView(game16Grid, 0, params);
        this.grid = game16Grid;
        if (!isPartyWaiting() && this.startedTime <= 0) {
            this.progressBar.setVisibility(0);
            this.progressBar.setMax(res.facebook);
            this.startedTime = System.currentTimeMillis();
            if (this.progression.getLevelNumber() == 1) {
                this.gameTime = this.isOnlineGame
                        ? com.cube.memorygames.api.online.OnlineSession.get().roundDurationMs(this, 30000L) : 30000L;
            }
            this.timerHandler.postDelayed(this.timerRunnable, 0);
        }
        if (this.progression.getLevelNumber() == 1) {
            this.stateTimer.scheduleAndRunTask(new TimerTask() {
                public void run() {
                    Game16LikePreviousActivity.this.runOnUiThread(new Runnable() {
                        public void run() {
                            Game16LikePreviousActivity game16LikePreviousActivity = Game16LikePreviousActivity.this;
                            game16LikePreviousActivity.userScore += Game16LikePreviousActivity.this.getLevelScore(Game16LikePreviousActivity.this.progression.getLevelNumber());
                            Game16LikePreviousActivity.this.onSuccessCellClicked(false);
                            ((Game16Grid) Game16LikePreviousActivity.this.grid).skipLevel();
                        }
                    });
                }
            }, 800);
        }
        this.stateTimer.start();
    }

    @Override protected void onPartyClockStart() {
        this.startedTime = System.currentTimeMillis();
        this.gameTime = com.cube.memorygames.api.online.PartySession.get().remaining();
        this.progressBar.setVisibility(0);
        this.progressBar.setMax((int) (gameTime / 10));
        this.timerHandler.post(this.timerRunnable);
    }

    private Element getNextElement(Element previousElement, int levelNumber) {
        int colorsCount;
        int figuresCount;
        boolean correct = GameRandom.nextBoolean();
        if (levelNumber <= 5) {
            colorsCount = 4;
            figuresCount = 7;
        } else if (levelNumber <= 10) {
            colorsCount = 4;
            figuresCount = 5;
        } else if (levelNumber <= 15) {
            colorsCount = 4;
            figuresCount = 4;
        } else {
            colorsCount = 1;
            figuresCount = 3;
        }
        Element result;
        if (previousElement == null) {
            result = new Element();
            result.setColor(((Integer) getColors(colorsCount).get(0)).intValue());
            result.setFigure(((Integer) getFigures(figuresCount).get(0)).intValue());
            result.setWin(true);
            return result;
        } else if (correct) {
            result = previousElement;
            result.setWin(true);
            return result;
        } else {
            result = new Element();
            if (colorsCount > 1) {
                for (Integer intValue : getColors(colorsCount)) {
                    int nextColor = intValue.intValue();
                    if (nextColor != previousElement.getColor()) {
                        result.setColor(nextColor);
                        break;
                    }
                }
            }
            result.setColor(previousElement.getColor());
            for (Integer intValue2 : getFigures(figuresCount)) {
                int nextFigure = intValue2.intValue();
                if (nextFigure != previousElement.getFigure()) {
                    result.setFigure(nextFigure);
                    break;
                }
            }
            result.setWin(false);
            return result;
        }
    }

    private List<Integer> getColors(int colorsCount) {
        int[] shapeColors = getResources().getIntArray(R.array.game10_colors);
        List<Integer> colors = new ArrayList();
        for (int shapeColor : shapeColors) {
            colors.add(Integer.valueOf(shapeColor));
        }
        GameRandom.shuffle(colors);
        return colors.subList(0, colorsCount);
    }

    private List<Integer> getFigures(int figuresCount) {
        List<Integer> figures = new ArrayList();
        for (int i = 0; i < figuresCount; i++) {
            figures.add(Integer.valueOf(i));
        }
        GameRandom.shuffle(figures);
        return figures;
    }

    protected void createGameFlowStates() {
        List<GameFlowState> states = new ArrayList();
        states.add(new StatGameFlowState());
        this.stateTimer = new GameFlowStateTimer(states);
    }

    public void onSuccessCellClicked(int lastAddedCellClicked) {
        onSuccessCellClicked(true);
    }

    private void onSuccessCellClicked(boolean chowCheckMark) {
        if (chowCheckMark) {
            animateCheckbox();
            SoundUtils.playSound(this, SOUND.WIN);
            updateOnlineScore();
            this.userScore += getLevelScore(this.progression.getLevelNumber());
        }
        giveStars();
        if ((this.gameTime - (System.currentTimeMillis() - this.startedTime)) + this.pausedDuration > 0) {
            startNextLevel();
        }
    }

    private void animateCheckbox() {
        ViewAnimator.animate(this.correctHint).alpha(0.8f, 0.8f, 0.0f).duration(500).onStart(new Start() {
            public void onStart() {
                Game16LikePreviousActivity.this.correctHint.setVisibility(0);
            }
        }).onStop(new Stop() {
            public void onStop() {
                Game16LikePreviousActivity.this.correctHint.setVisibility(8);
            }
        }).start();
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

    protected void clearWrongCells() {
        if (this.grid != null) {
            this.gridContainer.removeView((View) this.grid);
            this.grid = null;
        }
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
        this.previousElement = null;
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
            this.grid = null;
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
