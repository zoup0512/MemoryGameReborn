package com.cube.memorygames.games;

import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Handler;
import android.support.v7.app.AlertDialog.Builder;
import android.widget.RelativeLayout.LayoutParams;
import com.cube.memorygames.logic.GameFlowState;
import com.cube.memorygames.logic.GameFlowStateTimer;
import com.cube.memorygames.logic.GameProgression12;
import com.cube.memorygames.logic.GameRandom;
import com.cube.memorygames.ui.Game11Grid;
import com.memory.brain.training.games.R;
import java.util.ArrayList;
import java.util.List;

public class Game11CorrectlyActivity extends Game1MemoryGridActivity {
    private long gameTime;
    private long startedTime;
    private Handler timerHandler = new Handler();
    private Runnable timerRunnable = new Runnable() {
        public void run() {
            long currentPausedDuration = 0;
            if (Game11CorrectlyActivity.this.pausedTime != 0) {
                currentPausedDuration = System.currentTimeMillis() - Game11CorrectlyActivity.this.pausedTime;
            }
            final long time = ((Game11CorrectlyActivity.this.gameTime - (System.currentTimeMillis() - Game11CorrectlyActivity.this.startedTime)) + Game11CorrectlyActivity.this.pausedDuration) + currentPausedDuration;
            if (time > 0) {
                Game11CorrectlyActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        Game11CorrectlyActivity.this.updateTimerText(time);
                    }
                });
                Game11CorrectlyActivity.this.timerHandler.postDelayed(this, 20);
                return;
            }
            Game11CorrectlyActivity.this.startedTime = 0;
            Game11CorrectlyActivity.this.progressBar.setVisibility(4);
            if (!Game11CorrectlyActivity.this.isFinishing()) {
                Game11CorrectlyActivity.this.onFailCellClicked();
            }
        }
    };

    protected class UserInputEnabledFlowState implements GameFlowState {
        protected UserInputEnabledFlowState() {
        }

        public void applyState() {
            Game11CorrectlyActivity.this.grid.hideChallengeCells();
            Game11CorrectlyActivity.this.grid.enableAllCells();
            Game11CorrectlyActivity.this.enablePausePanel();
            if (!Game11CorrectlyActivity.this.isOnlineGame) {
                Game11CorrectlyActivity.this.startedTime = System.currentTimeMillis();
                if (Game11CorrectlyActivity.this.progression.getLevelNumber() <= 10) {
                    Game11CorrectlyActivity.this.gameTime = 4000;
                } else {
                    Game11CorrectlyActivity.this.gameTime = 8000;
                }
                Game11CorrectlyActivity.this.progressBar.setVisibility(0);
                Game11CorrectlyActivity.this.progressBar.setMax((int) (Game11CorrectlyActivity.this.gameTime / 10));
                Game11CorrectlyActivity.this.timerHandler.postDelayed(Game11CorrectlyActivity.this.timerRunnable, 0);
            }
            Game11CorrectlyActivity.this.timerContainer.setVisibility(8);
        }

        public int getDuration() {
            return 0;
        }
    }

    protected boolean isEnableLevelTimer() {
        return true;
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
                Game11CorrectlyActivity.this.confirmBackPress();
                dialog.dismiss();
            }
        });
        builder.show();
    }

    protected void onDestroy() {
        super.onDestroy();
        killTimer();
    }

    protected long getStartDialogOnlineDelay() {
        return 4000;
    }

    public void onFailCellClicked() {
        if (!this.isOnlineGame) {
            this.progressBar.setVisibility(4);
        }
        killTimer();
        this.pausedDuration = 0;
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

    public void onSuccessCellClicked(int lastAddedCellClicked) {
        if (!this.isOnlineGame) {
            this.progressBar.setVisibility(4);
        }
        killTimer();
        this.pausedDuration = 0;
        super.onSuccessCellClicked(lastAddedCellClicked);
    }

    protected void createProgression() {
        this.progression = new GameProgression12();
    }

    protected void startLevel() {
        displayLevelNumber();
        Game11Grid game11Grid = (Game11Grid) this.grid;
        if (game11Grid == null) {
            game11Grid = new Game11Grid(this);
        }
        game11Grid.setGridEventsListener(this);
        game11Grid.hideChallengeCells();
        setGameParams(game11Grid);
        LayoutParams params = new LayoutParams(-1, -1);
        params.addRule(13, -1);
        this.gridContainer.addView(game11Grid, 0, params);
        this.grid = game11Grid;
        this.stateTimer.start();
    }

    protected void createGameFlowStates() {
        List<GameFlowState> states = new ArrayList();
        states.add(new UserInputEnabledFlowState());
        this.stateTimer = new GameFlowStateTimer(states);
    }

    private void setGameParams(Game11Grid game11Grid) {
        switch (this.progression.getLevelNumber()) {
            case 1:
            case 2:
            case 3:
                levelType1(game11Grid, randInt(4, 10), randInt(1, 7), randInt(1, 2));
                return;
            case 4:
            case 5:
                levelType1(game11Grid, randInt(9, 25), randInt(5, 15), randInt(1, 3));
                return;
            case 6:
            case 7:
                levelType2(game11Grid, randInt(2, 8), randInt(3, 6), randInt(1, 2));
                return;
            case 8:
            case 9:
                levelType3(game11Grid, randInt(4, 10), randInt(2, 6), randInt(3, 8), randInt(1, 3));
                return;
            case 10:
            case 11:
                levelType3(game11Grid, randInt(15, 31), randInt(11, 20), randInt(8, 22), randNumber(1, 3, 10, 20));
                return;
            case 12:
            case 13:
                levelType6(game11Grid, randInt(9, 20), randInt(2, 9), randInt(15, 30), randNumber(1, 3, 5, 10));
                return;
            case 14:
            case 15:
                levelType6(game11Grid, randInt(11, 25), randInt(8, 12), randInt(25, 100), randNumber(1, 3, 10, 30));
                return;
            case 16:
            case 17:
            case 18:
            case 19:
            case 20:
                levelType7(game11Grid, randInt(6, 12), randInt(7, 15), randInt(5, 11), randInt(8, 16), randNumber(1, 3, 10, 30));
                return;
            case 21:
            case 22:
            case 23:
            case 24:
            case 25:
                levelType7(game11Grid, randInt(11, 19), randInt(11, 19), randInt(11, 19), randInt(11, 19), randNumber(1, 3, 10, 30));
                return;
            default:
                levelType5(game11Grid, randInt(21, 33), randInt(21, 33), randInt(21, 33), randInt(21, 33), randInt(100, 300), randNumber(1, 3, 20, 40));
                return;
        }
    }

    private void levelType1(Game11Grid game11Grid, int x1, int x2, int z) {
        int y;
        String str;
        if (GameRandom.nextBoolean()) {
            y = x1 + x2;
            str = x1 + " + " + x2 + " = ";
        } else {
            y = x1 - x2;
            str = x1 + " - " + x2 + " = ";
        }
        boolean correct = GameRandom.nextBoolean();
        if (!correct) {
            if (!GameRandom.nextBoolean()) {
                z = -z;
            }
            y += z;
        }
        game11Grid.setGameParams(str + y, correct);
    }

    private void levelType2(Game11Grid game11Grid, int x1, int x2, int z) {
        int y = x1 * x2;
        String str = x1 + " * " + x2 + " = ";
        boolean correct = GameRandom.nextBoolean();
        if (!correct) {
            if (!GameRandom.nextBoolean()) {
                z = -z;
            }
            y += z;
        }
        game11Grid.setGameParams(str + y, correct);
    }

    private void levelType3(Game11Grid game11Grid, int x1, int x2, int x3, int z) {
        int y;
        String str;
        if (GameRandom.nextBoolean()) {
            y = x1 + x2;
            str = x1 + " + " + x2;
            if (GameRandom.nextBoolean()) {
                y += x3;
                str = str + " + " + x3;
            } else {
                y -= x3;
                str = str + " - " + x3;
            }
        } else {
            y = x1 - x2;
            str = x1 + " - " + x2;
            if (GameRandom.nextBoolean()) {
                y += x3;
                str = str + " + " + x3;
            } else {
                y -= x3;
                str = str + " - " + x3;
            }
        }
        boolean correct = GameRandom.nextBoolean();
        if (!correct) {
            if (!GameRandom.nextBoolean()) {
                z = -z;
            }
            y += z;
        }
        game11Grid.setGameParams(str + " = " + y, correct);
    }

    private void levelType5(Game11Grid game11Grid, int x1, int x2, int x3, int x4, int x5, int z) {
        int y;
        String str;
        if (GameRandom.nextBoolean()) {
            y = (x1 * x2) + (x3 * x4);
            str = x1 + " * " + x2 + " + " + x3 + " * " + x4;
            if (GameRandom.nextBoolean()) {
                y += x5;
                str = str + " + " + x5;
            } else {
                y -= x5;
                str = str + " - " + x5;
            }
        } else {
            y = (x1 * x2) - (x3 * x4);
            str = x1 + " * " + x2 + " - " + x3 + " * " + x4;
            if (GameRandom.nextBoolean()) {
                y += x5;
                str = str + " + " + x5;
            } else {
                y -= x5;
                str = str + " - " + x5;
            }
        }
        boolean correct = GameRandom.nextBoolean();
        if (!correct) {
            if (!GameRandom.nextBoolean()) {
                z = -z;
            }
            y += z;
        }
        game11Grid.setGameParams(str + " = " + y, correct);
    }

    private void levelType6(Game11Grid game11Grid, int x1, int x2, int x3, int z) {
        int y;
        String str;
        if (GameRandom.nextBoolean()) {
            y = (x1 * x2) + x3;
            str = x1 + " * " + x2 + " + " + x3 + " = ";
        } else {
            y = (x1 * x2) - x3;
            str = x1 + " * " + x2 + " - " + x3 + " = ";
        }
        boolean correct = GameRandom.nextBoolean();
        if (!correct) {
            if (!GameRandom.nextBoolean()) {
                z = -z;
            }
            y += z;
        }
        game11Grid.setGameParams(str + y, correct);
    }

    private void levelType7(Game11Grid game11Grid, int x1, int x2, int x3, int x4, int z) {
        int y;
        String str;
        if (GameRandom.nextBoolean()) {
            y = (x1 * x2) + (x3 * x4);
            str = x1 + " * " + x2 + " + " + x3 + " * " + x4 + " = ";
        } else {
            y = (x1 * x2) - (x3 * x4);
            str = x1 + " * " + x2 + " - " + x3 + " * " + x4 + " = ";
        }
        boolean correct = GameRandom.nextBoolean();
        if (!correct) {
            if (!GameRandom.nextBoolean()) {
                z = -z;
            }
            y += z;
        }
        game11Grid.setGameParams(str + y, correct);
    }

    public int randInt(int min, int max) {
        return GameRandom.nextInt((max - min) + 1) + min;
    }

    public int randNumber(int... numbers) {
        return numbers[GameRandom.nextInt(numbers.length)];
    }
}
