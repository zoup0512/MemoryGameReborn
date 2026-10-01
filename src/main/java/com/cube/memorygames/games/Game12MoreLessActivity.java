package com.cube.memorygames.games;

import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Handler;
import android.support.v7.app.AlertDialog.Builder;
import android.widget.RelativeLayout.LayoutParams;
import com.cube.memorygames.logic.GameFlowState;
import com.cube.memorygames.logic.GameFlowStateTimer;
import com.cube.memorygames.logic.GameProgression11;
import com.cube.memorygames.logic.GameRandom;
import com.cube.memorygames.ui.Game12Grid;
import com.facebook.login.widget.ToolTipPopup;
import com.memory.brain.training.games.R;
import java.util.ArrayList;
import java.util.List;

public class Game12MoreLessActivity extends Game1MemoryGridActivity {
    private long gameTime;
    private long startedTime;
    private Handler timerHandler = new Handler();
    private Runnable timerRunnable = new Runnable() {
        public void run() {
            long currentPausedDuration = 0;
            if (Game12MoreLessActivity.this.pausedTime != 0) {
                currentPausedDuration = System.currentTimeMillis() - Game12MoreLessActivity.this.pausedTime;
            }
            final long time = ((Game12MoreLessActivity.this.gameTime - (System.currentTimeMillis() - Game12MoreLessActivity.this.startedTime)) + Game12MoreLessActivity.this.pausedDuration) + currentPausedDuration;
            if (time > 0) {
                Game12MoreLessActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        Game12MoreLessActivity.this.updateTimerText(time);
                    }
                });
                Game12MoreLessActivity.this.timerHandler.postDelayed(this, 20);
                return;
            }
            Game12MoreLessActivity.this.startedTime = 0;
            Game12MoreLessActivity.this.progressBar.setVisibility(4);
            if (!Game12MoreLessActivity.this.isFinishing()) {
                Game12MoreLessActivity.this.onFailCellClicked();
            }
        }
    };

    protected class UserInputEnabledFlowState implements GameFlowState {
        protected UserInputEnabledFlowState() {
        }

        public void applyState() {
            Game12MoreLessActivity.this.grid.hideChallengeCells();
            Game12MoreLessActivity.this.grid.enableAllCells();
            Game12MoreLessActivity.this.enablePausePanel();
            if (!Game12MoreLessActivity.this.isOnlineGame) {
                Game12MoreLessActivity.this.startedTime = System.currentTimeMillis();
                if (Game12MoreLessActivity.this.progression.getLevelNumber() <= 10) {
                    Game12MoreLessActivity.this.gameTime = ToolTipPopup.DEFAULT_POPUP_DISPLAY_TIME;
                } else {
                    Game12MoreLessActivity.this.gameTime = 10000;
                }
                Game12MoreLessActivity.this.progressBar.setVisibility(0);
                Game12MoreLessActivity.this.progressBar.setMax((int) (Game12MoreLessActivity.this.gameTime / 10));
                Game12MoreLessActivity.this.timerHandler.postDelayed(Game12MoreLessActivity.this.timerRunnable, 0);
            }
            Game12MoreLessActivity.this.timerContainer.setVisibility(8);
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
                Game12MoreLessActivity.this.confirmBackPress();
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
        this.progression = new GameProgression11();
    }

    protected void startLevel() {
        displayLevelNumber();
        Game12Grid game12Grid = (Game12Grid) this.grid;
        if (game12Grid == null) {
            game12Grid = new Game12Grid(this);
        }
        game12Grid.setGridEventsListener(this);
        game12Grid.hideChallengeCells();
        setGameParams(game12Grid);
        LayoutParams params = new LayoutParams(-1, -1);
        params.addRule(13, -1);
        this.gridContainer.addView(game12Grid, 0, params);
        this.grid = game12Grid;
        this.stateTimer.start();
    }

    protected void createGameFlowStates() {
        List<GameFlowState> states = new ArrayList();
        states.add(new UserInputEnabledFlowState());
        this.stateTimer = new GameFlowStateTimer(states);
    }

    private void setGameParams(Game12Grid game12Grid) {
        switch (this.progression.getLevelNumber()) {
            case 1:
            case 2:
                levelType1(game12Grid, rand(1, 10), rand(1, 10));
                return;
            case 3:
                levelType2(game12Grid, rand(1, 10), rand(1, 10), rand(1, 10), rand(1, 10));
                return;
            case 4:
                levelType3(game12Grid, rand(5, 20), rand(5, 20), rand(2, 3), rand(3, 4));
                return;
            case 5:
            case 6:
                levelType4(game12Grid, rand(10, 20), rand(4, 9), rand(2, 5), rand(1, 3));
                return;
            case 7:
                levelType4(game12Grid, rand(15, 30), rand(5, 12), rand(1, 5), rand(1, 3));
                return;
            case 8:
            case 9:
                levelType4(game12Grid, rand(20, 40), rand(5, 20), rand(1, 4), rand(1, 4));
                return;
            case 10:
            case 11:
                levelType4(game12Grid, rand(20, 50), rand(15, 40), rand(1, 5), rand(1, 5));
                return;
            case 12:
            case 13:
                levelType4(game12Grid, rand(30, 70), rand(25, 55), rand(1, 7), rand(1, 7));
                return;
            case 14:
            case 15:
                levelType5(game12Grid, rand(7, 11), rand(6, 12), 1, rand(1, 2));
                return;
            case 16:
                levelType5(game12Grid, rand(9, 13), rand(8, 14), rand(1, 3), rand(1, 3));
                return;
            case 17:
                levelType6(game12Grid, rand(5, 11), rand(5, 9), 1, 1);
                return;
            case 18:
            case 19:
            case 20:
                levelType7(game12Grid, rand(30, 49), rand(5, 9), rand(5, 9), rand(4, 5), rand(1, 3), rand(1, 3));
                return;
            case 21:
                levelType7(game12Grid, rand(70, 99), rand(5, 12), rand(5, 9), rand(4, 10), rand(1, 3), rand(1, 3));
                return;
            case 22:
            case 23:
            case 24:
                levelType7(game12Grid, rand(110, 149), rand(8, 15), rand(6, 12), rand(4, 10), rand(1, 3), rand(1, 3));
                return;
            default:
                levelType7(game12Grid, rand(200, 300), rand(11, 20), rand(11, 30), rand(4, 10), rand(2, 5), rand(3, 6));
                return;
        }
    }

    private void levelType1(Game12Grid game12Grid, int x1, int x2) {
        int correct;
        String str1 = "" + x1;
        String str2 = "" + x2;
        if (x1 > x2) {
            correct = 0;
        } else if (x1 < x2) {
            correct = 2;
        } else {
            correct = 1;
        }
        game12Grid.setGameParams(str1, str2, correct);
    }

    private void levelType2(Game12Grid game12Grid, int x1, int y1, int x2, int y2) {
        int correct;
        int rez1 = x1 + y1;
        int rez2 = x2 + y2;
        String str1 = x1 + " + " + y1;
        String str2 = x2 + " + " + y2;
        if (rez1 > rez2) {
            correct = 0;
        } else if (rez1 < rez2) {
            correct = 2;
        } else {
            correct = 1;
        }
        game12Grid.setGameParams(str1, str2, correct);
    }

    private void levelType3(Game12Grid game12Grid, int x1, int y1, int f1, int f2) {
        int correct;
        boolean sign1 = GameRandom.nextBoolean();
        boolean sign2 = !sign1;
        int x2 = sign1 ? x1 + f1 : x1 - f1;
        int y2 = sign2 ? y1 + f2 : y1 - f2;
        int rez1 = x1 + y1;
        int rez2 = x2 + y2;
        String str1 = x1 + " + " + y1;
        String str2 = x2 + " + " + y2;
        if (rez1 > rez2) {
            correct = 0;
        } else if (rez1 < rez2) {
            correct = 2;
        } else {
            correct = 1;
        }
        game12Grid.setGameParams(str1, str2, correct);
    }

    private void levelType4(Game12Grid game12Grid, int x1, int y1, int f1, int f2) {
        int rez1;
        int rez2;
        String str1;
        String str2;
        int correct;
        boolean sign1 = GameRandom.nextBoolean();
        boolean sign2 = !sign1;
        int x2 = sign1 ? x1 + f1 : x1 - f1;
        int y2 = sign2 ? y1 + f2 : y1 - f2;
        if (GameRandom.nextBoolean()) {
            rez1 = x1 + y1;
            rez2 = x2 + y2;
            str1 = x1 + " + " + y1;
            str2 = x2 + " + " + y2;
        } else {
            rez1 = x1 - y1;
            rez2 = x2 - y2;
            str1 = x1 + " - " + y1;
            str2 = x2 + " - " + y2;
        }
        if (rez1 > rez2) {
            correct = 0;
        } else if (rez1 < rez2) {
            correct = 2;
        } else {
            correct = 1;
        }
        game12Grid.setGameParams(str1, str2, correct);
    }

    private void levelType5(Game12Grid game12Grid, int x1, int y1, int f1, int f2) {
        int correct;
        boolean sign1 = GameRandom.nextBoolean();
        boolean sign2 = !sign1;
        int x2 = sign1 ? x1 + f1 : x1 - f1;
        int y2 = sign2 ? y1 + f2 : y1 - f2;
        int rez1 = x1 * y1;
        int rez2 = x2 * y2;
        String str1 = x1 + " * " + y1;
        String str2 = x2 + " * " + y2;
        if (rez1 > rez2) {
            correct = 0;
        } else if (rez1 < rez2) {
            correct = 2;
        } else {
            correct = 1;
        }
        game12Grid.setGameParams(str1, str2, correct);
    }

    private void levelType6(Game12Grid game12Grid, int y1, int z1, int f1, int f2) {
        int correct;
        boolean sign1 = GameRandom.nextBoolean();
        int y2 = !sign1 ? y1 + f2 : y1 - f2;
        int z2 = sign1 ? z1 + f1 : z1 - f1;
        int x2 = y2 * z2;
        String str1 = (y1 * z1) + " / " + y1;
        String str2 = x2 + " / " + y2;
        if (z1 > z2) {
            correct = 0;
        } else if (z1 < z2) {
            correct = 2;
        } else {
            correct = 1;
        }
        game12Grid.setGameParams(str1, str2, correct);
    }

    private void levelType7(Game12Grid game12Grid, int x1, int y1, int z1, int f1, int f2, int f3) {
        int rez1;
        int rez2;
        String str1;
        String str2;
        int correct;
        boolean sign1 = GameRandom.nextBoolean();
        boolean sign2 = GameRandom.nextBoolean();
        boolean sign3 = !sign2;
        int x2 = sign1 ? x1 + f1 : x1 - f1;
        int y2 = sign2 ? y1 + f2 : y1 - f2;
        int z2 = sign3 ? z1 + f3 : z1 - f3;
        if (GameRandom.nextBoolean()) {
            rez1 = x1 + (y1 * z1);
            rez2 = x2 + (y2 * z2);
            str1 = x1 + " + " + y1 + " * " + z1;
            str2 = x2 + " + " + y2 + " * " + z2;
        } else {
            rez1 = x1 - (y1 * z1);
            rez2 = x2 - (y2 * z2);
            str1 = x1 + " - " + y1 + " * " + z1;
            str2 = x2 + " - " + y2 + " * " + z2;
        }
        if (rez1 > rez2) {
            correct = 0;
        } else if (rez1 < rez2) {
            correct = 2;
        } else {
            correct = 1;
        }
        game12Grid.setGameParams(str1, str2, correct);
    }

    private int rand(int min, int max) {
        return GameRandom.nextInt((max - min) + 1) + min;
    }
}
