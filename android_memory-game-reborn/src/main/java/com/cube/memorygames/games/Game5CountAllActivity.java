package com.cube.memorygames.games;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnShowListener;
import android.graphics.Point;
import android.graphics.Typeface;
import android.os.Handler;
import android.view.Display;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TextView;
import butterknife.BindView;
import butterknife.ButterKnife;
import com.cube.memorygames.MemoryApplicationModel;
import com.cube.memorygames.SoundUtils;
import com.cube.memorygames.SoundUtils.SOUND;
import com.cube.memorygames.games.Game1MemoryGridActivity.ReadyFlowState;
import com.cube.memorygames.logic.GameFlowState;
import com.cube.memorygames.logic.GameFlowStateTimer;
import com.cube.memorygames.logic.GameProgression5;
import com.cube.memorygames.logic.GameRandom;
import com.cube.memorygames.ui.CountAllGrid;
import com.github.florent37.viewanimator.ViewAnimator;
import com.memory.brain.training.games.R;
import com.mopub.volley.DefaultRetryPolicy;
import java.util.ArrayList;
import java.util.List;

public class Game5CountAllActivity extends Game1MemoryGridActivity {
    private OnClickListener cellButtonClickListener = new OnClickListener() {
        public void onClick(View v) {
            Game5CountAllActivity.this.disablePausePanel();
            if (((TextView) v).getText().equals(new Integer(Game5CountAllActivity.this.currentWinCells).toString())) {
                Game5CountAllActivity.this.dialog.button1.setClickable(false);
                Game5CountAllActivity.this.dialog.button2.setClickable(false);
                Game5CountAllActivity.this.dialog.button3.setClickable(false);
                Game5CountAllActivity.this.dialog.button4.setClickable(false);
                ViewAnimator.animate(Game5CountAllActivity.this.dialog.button1).scale(DefaultRetryPolicy.DEFAULT_BACKOFF_MULT, 0.0f).duration(200).start();
                ViewAnimator.animate(Game5CountAllActivity.this.dialog.button2).scale(DefaultRetryPolicy.DEFAULT_BACKOFF_MULT, 0.0f).duration(200).start();
                ViewAnimator.animate(Game5CountAllActivity.this.dialog.button3).scale(DefaultRetryPolicy.DEFAULT_BACKOFF_MULT, 0.0f).duration(200).start();
                ViewAnimator.animate(Game5CountAllActivity.this.dialog.button4).scale(DefaultRetryPolicy.DEFAULT_BACKOFF_MULT, 0.0f).duration(200).start();
                Game5CountAllActivity.this.dialog.button1.postDelayed(new Runnable() {
                    public void run() {
                        if (!Game5CountAllActivity.this.isFinishing()) {
                            Game5CountAllActivity.this.dialog.dismiss();
                            Game5CountAllActivity.this.disablePausePanel();
                            Game5CountAllActivity.this.showWin();
                        }
                    }
                }, 200);
                Game5CountAllActivity.this.disablePausePanel();
                return;
            }
            SoundUtils.playSound(Game5CountAllActivity.this, SOUND.FAIL);
            Game5CountAllActivity.this.grid.disableAllCells();
            Game5CountAllActivity.this.grid.showChallengeCells();
            if (Game5CountAllActivity.this.dialog.button1.getText().equals(new Integer(Game5CountAllActivity.this.currentWinCells).toString())) {
                Game5CountAllActivity.this.dialog.button1.setBackgroundResource(R.drawable.button_count_win_background);
            }
            if (Game5CountAllActivity.this.dialog.button2.getText().equals(new Integer(Game5CountAllActivity.this.currentWinCells).toString())) {
                Game5CountAllActivity.this.dialog.button2.setBackgroundResource(R.drawable.button_count_win_background);
            }
            if (Game5CountAllActivity.this.dialog.button3.getText().equals(new Integer(Game5CountAllActivity.this.currentWinCells).toString())) {
                Game5CountAllActivity.this.dialog.button3.setBackgroundResource(R.drawable.button_count_win_background);
            }
            if (Game5CountAllActivity.this.dialog.button4.getText().equals(new Integer(Game5CountAllActivity.this.currentWinCells).toString())) {
                Game5CountAllActivity.this.dialog.button4.setBackgroundResource(R.drawable.button_count_win_background);
            }
            Game5CountAllActivity.this.dialog.button1.setClickable(false);
            Game5CountAllActivity.this.dialog.button2.setClickable(false);
            Game5CountAllActivity.this.dialog.button3.setClickable(false);
            Game5CountAllActivity.this.dialog.button4.setClickable(false);
            Handler handler = new Handler();
            Game5CountAllActivity.this.timerContainer.setVisibility(0);
            Game5CountAllActivity.this.textLevelReady.setText("");
            handler.postDelayed(new Runnable() {
                public void run() {
                    if (!Game5CountAllActivity.this.isFinishing()) {
                        Game5CountAllActivity.this.dialog.dismiss();
                        Game5CountAllActivity.this.disablePausePanel();
                        Game5CountAllActivity.this.showFailureDialog();
                    }
                }
            }, 2000);
            Game5CountAllActivity.this.dialog.button1.postDelayed(new Runnable() {
                public void run() {
                    if (!Game5CountAllActivity.this.isFinishing()) {
                        ViewAnimator.animate(Game5CountAllActivity.this.dialog.button1).scale(DefaultRetryPolicy.DEFAULT_BACKOFF_MULT, 0.0f).duration(200).start();
                        ViewAnimator.animate(Game5CountAllActivity.this.dialog.button2).scale(DefaultRetryPolicy.DEFAULT_BACKOFF_MULT, 0.0f).duration(200).start();
                        ViewAnimator.animate(Game5CountAllActivity.this.dialog.button3).scale(DefaultRetryPolicy.DEFAULT_BACKOFF_MULT, 0.0f).duration(200).start();
                        ViewAnimator.animate(Game5CountAllActivity.this.dialog.button4).scale(DefaultRetryPolicy.DEFAULT_BACKOFF_MULT, 0.0f).duration(200).start();
                    }
                }
            }, 1800);
        }
    };
    private int currentWinCells;
    private CellsCountDialog dialog;

    class CellsCountDialog extends Dialog {
        @BindView(R.id.button_dialog_game5_cell_count_1)
        TextView button1;
        @BindView(R.id.button_dialog_game5_cell_count_2)
        TextView button2;
        @BindView(R.id.button_dialog_game5_cell_count_3)
        TextView button3;
        @BindView(R.id.button_dialog_game5_cell_count_4)
        TextView button4;
        @BindView(R.id.panel_pause)
        View panelPause;
        private int rightAnswer;
        @BindView(R.id.title)
        TextView title;

        public CellsCountDialog(Context context) {
            super(context, R.style.GdxTheme);
            setCancelable(false);
            getWindow().setBackgroundDrawableResource(17170445);
            getWindow().requestFeature(1);
            setContentView(R.layout.dialog_game5);
            ButterKnife.bind((Object) this, (Dialog) this);
            Typeface typeFaceRoboto = Typeface.createFromAsset(Game5CountAllActivity.this.getAssets(), "Roboto-Light.ttf");
            this.button1.setOnClickListener(Game5CountAllActivity.this.cellButtonClickListener);
            this.button2.setOnClickListener(Game5CountAllActivity.this.cellButtonClickListener);
            this.button3.setOnClickListener(Game5CountAllActivity.this.cellButtonClickListener);
            this.button4.setOnClickListener(Game5CountAllActivity.this.cellButtonClickListener);
            this.button1.setTypeface(typeFaceRoboto);
            this.button2.setTypeface(typeFaceRoboto);
            this.button3.setTypeface(typeFaceRoboto);
            this.button4.setTypeface(typeFaceRoboto);
            this.title.setTypeface(typeFaceRoboto, 2);
            this.title.setText(Game5CountAllActivity.this.getString(R.string.game5_dialog_text) + " ");
            this.panelPause.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Game5CountAllActivity.this.soundClick();
                }
            });
            setOnShowListener(new DialogInterface.OnShowListener() {
                public void onShow(DialogInterface dialog) {
                    ViewAnimator.animate(CellsCountDialog.this.button1).scale(0.0f, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT).duration(200).start();
                    ViewAnimator.animate(CellsCountDialog.this.button2).scale(0.0f, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT).duration(200).start();
                    ViewAnimator.animate(CellsCountDialog.this.button3).scale(0.0f, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT).duration(200).start();
                    ViewAnimator.animate(CellsCountDialog.this.button4).scale(0.0f, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT).duration(200).start();
                }
            });
        }

        public void setRightAnswer(int rightAnswer) {
            this.rightAnswer = rightAnswer;
            int rightAnswerPosition = rightAnswer - 1;
            if (rightAnswer > 3) {
                rightAnswerPosition = GameRandom.nextInt(3);
            }
            int[] answers = new int[4];
            for (int i = 0; i < answers.length; i++) {
                if (i == rightAnswerPosition) {
                    answers[i] = rightAnswer;
                } else {
                    answers[i] = (i - rightAnswerPosition) + rightAnswer;
                }
            }
            this.button1.setText(answers[0] + "");
            this.button2.setText(answers[1] + "");
            this.button3.setText(answers[2] + "");
            this.button4.setText(answers[3] + "");
            this.button1.setClickable(true);
            this.button2.setClickable(true);
            this.button3.setClickable(true);
            this.button4.setClickable(true);
            this.button1.setBackgroundResource(R.drawable.button_count);
            this.button2.setBackgroundResource(R.drawable.button_count);
            this.button3.setBackgroundResource(R.drawable.button_count);
            this.button4.setBackgroundResource(R.drawable.button_count);
        }
    }

    private class HideAllCellsFlowState implements GameFlowState {
        private HideAllCellsFlowState() {
        }

        public void applyState() {
            Game5CountAllActivity.this.grid.animateFinishCells();
            Game5CountAllActivity.this.foreground.postDelayed(new Runnable() {
                public void run() {
                    Game5CountAllActivity.this.grid.hideAllCells();
                }
            }, 200);
        }

        public int getDuration() {
            return 200;
        }
    }

    private class HideCellsAndShowChallengeFlowState implements GameFlowState {
        private HideCellsAndShowChallengeFlowState() {
        }

        public void applyState() {
            Game5CountAllActivity.this.timerContainer.setVisibility(8);
            Game5CountAllActivity.this.grid.hideAllCells();
            Game5CountAllActivity.this.grid.showChallengeCells();
        }

        public int getDuration() {
            return 1000;
        }
    }

    private class ShowDialogFlowState implements GameFlowState {
        private ShowDialogFlowState() {
        }

        public void applyState() {
            if (!Game5CountAllActivity.this.isFinishing()) {
                if (Game5CountAllActivity.this.dialog == null) {
                    Game5CountAllActivity.this.dialog = new CellsCountDialog(Game5CountAllActivity.this);
                }
                Game5CountAllActivity.this.dialog.setRightAnswer(Game5CountAllActivity.this.currentWinCells);
                Game5CountAllActivity.this.dialog.show();
                Game5CountAllActivity.this.enablePausePanel();
            }
        }

        public int getDuration() {
            return 0;
        }
    }

    protected void createProgression() {
        this.progression = new GameProgression5();
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
        this.currentWinCells = this.progression.getCurrentWinCells();
        CountAllGrid rectangularGrid = new CountAllGrid(this, this.progression.getCurrentGridSize(), this.progression.getCurrentGridSize(), this.currentWinCells, side, side);
        rectangularGrid.setShowAnimation(true);
        this.gridContainer.addView(rectangularGrid, 0, params);
        this.grid = rectangularGrid;
        this.grid.setGridEventsListener(this);
        int numberOfImagesToUse = this.currentWinCells / 10;
        if (this.currentWinCells % 10 > 0) {
            numberOfImagesToUse++;
        }
        List<Integer> allCellDrawableIds = MemoryApplicationModel.getInstance().getAllCellDrawableIds();
        List<Integer> drawableIdsToUse = new ArrayList();
        for (int i = 0; i < numberOfImagesToUse; i++) {
            drawableIdsToUse.add(allCellDrawableIds.get(GameRandom.nextInt(allCellDrawableIds.size() - 1)));
        }
        this.grid.setDrawableIdsToUse(drawableIdsToUse);
        this.grid.buildGrid();
        this.grid.hideAllCells();
        this.stateTimer.start();
    }

    protected void exitFromPauseClicked() {
        if (this.dialog != null) {
            this.dialog.dismiss();
        }
    }

    protected void retryFromPauseClicked() {
        if (this.dialog != null) {
            this.dialog.dismiss();
        }
    }

    protected void createGameFlowStates() {
        List<GameFlowState> states = new ArrayList();
        states.add(new ReadyFlowState());
        states.add(new GridAnimationFlowState());
        states.add(new HideCellsAndShowChallengeFlowState());
        states.add(new HideAllCellsFlowState());
        states.add(new ShowDialogFlowState());
        this.stateTimer = new GameFlowStateTimer(states);
    }

    protected void showFailure() {
        super.showFailure();
        if (this.dialog != null) {
            this.dialog.dismiss();
            disablePausePanel();
        }
    }
}
