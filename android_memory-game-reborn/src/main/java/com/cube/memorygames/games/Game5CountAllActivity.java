package com.cube.memorygames.games;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Point;
import android.graphics.Typeface;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.view.Display;
import android.view.KeyEvent;
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
import com.cube.memorygames.diagnostics.GameDiagnostics;
import com.cube.memorygames.logic.CountAnswerOptions;
import com.cube.memorygames.logic.GameFlowState;
import com.cube.memorygames.logic.GameFlowStateTimer;
import com.cube.memorygames.logic.GameProgression5;
import com.cube.memorygames.logic.GameRandom;
import com.cube.memorygames.ui.CountAllGrid;
import com.cube.memorygames.ui.DialogUi;
import com.github.florent37.viewanimator.ViewAnimator;
import com.memory.brain.training.games.R;
import com.mopub.volley.DefaultRetryPolicy;
import java.util.ArrayList;
import java.util.List;

public class Game5CountAllActivity extends Game1MemoryGridActivity {
    private OnClickListener cellButtonClickListener = new OnClickListener() {
        public void onClick(View v) {
            if (Game5CountAllActivity.this.dialog == null
                    || Game5CountAllActivity.this.dialog.answerLocked) {
                return;
            }
            boolean correct = Game5CountAllActivity.this.dialog.showAnswerFeedback((TextView) v);
            diagnosticQuestion("question_answer", correct ? "correct" : "wrong",
                    correct ? "answer_correct" : "answer_wrong");
            Game5CountAllActivity.this.disablePausePanel();
            if (correct) {
                ViewAnimator.animate(Game5CountAllActivity.this.dialog.button1).scale(DefaultRetryPolicy.DEFAULT_BACKOFF_MULT, 0.0f).duration(200).start();
                ViewAnimator.animate(Game5CountAllActivity.this.dialog.button2).scale(DefaultRetryPolicy.DEFAULT_BACKOFF_MULT, 0.0f).duration(200).start();
                ViewAnimator.animate(Game5CountAllActivity.this.dialog.button3).scale(DefaultRetryPolicy.DEFAULT_BACKOFF_MULT, 0.0f).duration(200).start();
                ViewAnimator.animate(Game5CountAllActivity.this.dialog.button4).scale(DefaultRetryPolicy.DEFAULT_BACKOFF_MULT, 0.0f).duration(200).start();
                Game5CountAllActivity.this.dialog.button1.postDelayed(diagnosticCallback("win_feedback", 200, new Runnable() {
                    public void run() {
                        if (!Game5CountAllActivity.this.isFinishing()) {
                            Game5CountAllActivity.this.dialog.dismiss();
                            Game5CountAllActivity.this.disablePausePanel();
                            Game5CountAllActivity.this.showWin();
                        }
                    }
                }), 200);
                Game5CountAllActivity.this.disablePausePanel();
                return;
            }
            SoundUtils.playSound(Game5CountAllActivity.this, SOUND.FAIL);
            Game5CountAllActivity.this.grid.disableAllCells();
            Game5CountAllActivity.this.grid.showChallengeCells();
            Handler handler = new Handler();
            Game5CountAllActivity.this.timerContainer.setVisibility(0);
            Game5CountAllActivity.this.textLevelReady.setText("");
            handler.postDelayed(diagnosticCallback("failure_dialog", 2000, new Runnable() {
                public void run() {
                    if (!Game5CountAllActivity.this.isFinishing()) {
                        Game5CountAllActivity.this.dialog.dismiss();
                        Game5CountAllActivity.this.disablePausePanel();
                        Game5CountAllActivity.this.showFailureDialog();
                    }
                }
            }), 2000);
            Game5CountAllActivity.this.dialog.button1.postDelayed(diagnosticCallback("failure_feedback", 1800, new Runnable() {
                public void run() {
                    if (!Game5CountAllActivity.this.isFinishing()) {
                        ViewAnimator.animate(Game5CountAllActivity.this.dialog.button1).scale(DefaultRetryPolicy.DEFAULT_BACKOFF_MULT, 0.0f).duration(200).start();
                        ViewAnimator.animate(Game5CountAllActivity.this.dialog.button2).scale(DefaultRetryPolicy.DEFAULT_BACKOFF_MULT, 0.0f).duration(200).start();
                        ViewAnimator.animate(Game5CountAllActivity.this.dialog.button3).scale(DefaultRetryPolicy.DEFAULT_BACKOFF_MULT, 0.0f).duration(200).start();
                        ViewAnimator.animate(Game5CountAllActivity.this.dialog.button4).scale(DefaultRetryPolicy.DEFAULT_BACKOFF_MULT, 0.0f).duration(200).start();
                    }
                }
            }), 1800);
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
        private boolean answerLocked;
        private TextView feedback;
        @BindView(R.id.title)
        TextView title;

        public CellsCountDialog(Context context) {
            super(context, R.style.AppDialogTheme);
            setCancelable(false);
            DialogUi.setContent(this, R.layout.dialog_game5);
            ButterKnife.bind((Object) this, (Dialog) this);
            this.feedback = (TextView) findViewById(R.id.count_answer_feedback);
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
            this.title.setText(Game5CountAllActivity.this.getString(R.string.game5_dialog_text));
            this.panelPause.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    // The inherited offline soundClick opens PauseDialog instead.
                    // This control is always sound-only; it must never stop a timer.
                    if (Game5CountAllActivity.this.isOnlineGame) {
                        Game5CountAllActivity.this.soundClick();
                    } else {
                        boolean enabled = !SoundUtils.isPlaySound(getContext());
                        Game5CountAllActivity.this.application.logEvent(Game5CountAllActivity.this,
                                MemoryApplicationModel.ANALYTICS_CATEGORY_GENERAL,
                                enabled ? MemoryApplicationModel.ANALYTICS_EVENT_SOUND_ON
                                        : MemoryApplicationModel.ANALYTICS_EVENT_SOUND_OFF);
                        SoundUtils.setPlaySound(getContext(), enabled);
                    }
                    updateSoundState();
                    panelPause.announceForAccessibility(((TextView) panelPause).getText());
                }
            });
            setOnShowListener(new DialogInterface.OnShowListener() {
                public void onShow(DialogInterface dialog) {
                    diagnosticQuestion("question_options", "options", "dialog_show");
                    CellsCountDialog.this.updateSoundState();
                    ViewAnimator.animate(CellsCountDialog.this.button1).scale(0.0f, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT).duration(200).start();
                    ViewAnimator.animate(CellsCountDialog.this.button2).scale(0.0f, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT).duration(200).start();
                    ViewAnimator.animate(CellsCountDialog.this.button3).scale(0.0f, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT).duration(200).start();
                    ViewAnimator.animate(CellsCountDialog.this.button4).scale(0.0f, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT).duration(200).start();
                }
            });
        }

        private TextView[] answerButtons() {
            return new TextView[]{this.button1, this.button2, this.button3, this.button4};
        }

        private void updateSoundState() {
            boolean enabled = SoundUtils.isPlaySound(getContext());
            TextView soundButton = (TextView) this.panelPause;
            soundButton.setText(enabled ? R.string.count_sound_on : R.string.count_sound_off);
            soundButton.setCompoundDrawablesWithIntrinsicBounds(enabled
                    ? android.R.drawable.ic_lock_silent_mode_off
                    : android.R.drawable.ic_lock_silent_mode, 0, 0, 0);
            soundButton.setContentDescription(getContext().getString(enabled
                    ? R.string.count_sound_on_action : R.string.count_sound_off_action));
        }

        private boolean showAnswerFeedback(TextView selected) {
            this.answerLocked = true;
            String correctValue = Integer.toString(this.rightAnswer);
            boolean correct = correctValue.contentEquals(selected.getText());
            String selectedValue = selected.getText().toString();
            for (TextView button : answerButtons()) {
                button.setClickable(false);
                if (correctValue.contentEquals(button.getText())) {
                    button.setBackgroundResource(R.drawable.dialog_primary_button);
                    button.setTextColor(ContextCompat.getColor(getContext(), R.color.dialog_surface));
                    button.setContentDescription(getContext().getString(
                            R.string.count_correct_feedback, this.rightAnswer));
                }
            }
            if (!correct) {
                selected.setTextColor(ContextCompat.getColor(getContext(), R.color.dialog_danger));
                selected.setContentDescription(getContext().getString(
                        R.string.count_wrong_choice, selectedValue));
            }
            this.feedback.setText(getContext().getString(correct
                    ? R.string.count_correct_feedback : R.string.count_wrong_feedback, this.rightAnswer));
            this.feedback.setTextColor(ContextCompat.getColor(getContext(), correct
                    ? R.color.dialog_primary : R.color.dialog_danger));
            // A visible result, not only a color change, with one explicit announcement.
            this.feedback.announceForAccessibility(this.feedback.getText());
            return correct;
        }

        @Override
        public void onWindowFocusChanged(boolean hasFocus) {
            super.onWindowFocusChanged(hasFocus);
            if (hasFocus) {
                // PauseDialog can change the shared sound preference while above us.
                updateSoundState();
            }
        }

        @Override
        public boolean onKeyUp(int keyCode, KeyEvent event) {
            // A non-cancelable Dialog otherwise swallows hardware Back without
            // reaching onBackPressed on older Android versions.
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (!event.isCanceled()) {
                    onBackPressed();
                }
                return true;
            }
            return super.onKeyUp(keyCode, event);
        }

        @Override
        public void onBackPressed() {
            if (this.answerLocked || Game5CountAllActivity.this.isFinishing()) {
                return;
            }
            if (Game5CountAllActivity.this.isOnlineGame) {
                Game5CountAllActivity.this.onBackPressed();
            } else {
                // Preserve the existing pause limit, resume/retry and exit handling.
                Game5CountAllActivity.this.soundClick();
            }
        }

        public void setRightAnswer(int rightAnswer) {
            int[] answers = CountAnswerOptions.generate(rightAnswer);
            this.rightAnswer = rightAnswer;
            this.answerLocked = false;
            this.feedback.setText(R.string.count_choose_answer);
            this.feedback.setTextColor(ContextCompat.getColor(getContext(), R.color.dialog_text_secondary));
            diagnosticQuestion("question_options", "generated", "dialog_show");
            TextView[] buttons = answerButtons();
            for (int i = 0; i < buttons.length; i++) {
                buttons[i].setText(Integer.toString(answers[i]));
                buttons[i].setContentDescription(null);
                buttons[i].setClickable(true);
                buttons[i].setBackgroundResource(R.drawable.dialog_secondary_button);
                buttons[i].setTextColor(ContextCompat.getColorStateList(getContext(), R.color.dialog_secondary_text));
            }
        }
    }

    private class HideAllCellsFlowState implements GameFlowState {
        private HideAllCellsFlowState() {
        }

        public void applyState() {
            Game5CountAllActivity.this.grid.animateFinishCells();
            Game5CountAllActivity.this.foreground.postDelayed(diagnosticCallback("hide_cells", 200, new Runnable() {
                public void run() {
                    Game5CountAllActivity.this.grid.hideAllCells();
                }
            }), 200);
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
        if (gameDiagnostics != null) gameDiagnostics.beginQuestion(this.progression.getLevelNumber());
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
        // This list is filled reflectively and can degrade to 0-1 entries (a
        // release build once shipped with the R.drawable field names renamed
        // away); a bound < 1 in nextInt throws and takes the whole app down.
        for (int i = 0; i < numberOfImagesToUse && !allCellDrawableIds.isEmpty(); i++) {
            int index = allCellDrawableIds.size() > 1
                    ? GameRandom.nextInt(allCellDrawableIds.size() - 1) : 0;
            drawableIdsToUse.add(allCellDrawableIds.get(index));
        }
        if (!drawableIdsToUse.isEmpty()) {
            this.grid.setDrawableIdsToUse(drawableIdsToUse);
        }
        if (gameDiagnostics != null && gameDiagnostics.detailed()) rectangularGrid.setDiagnostics(gameDiagnostics);
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
        if (gameDiagnostics != null && gameDiagnostics.detailed()) {
            this.stateTimer.setObserver(new GameFlowStateTimer.Observer() {
                public void flow(String event, String phase, long run, int step, int generation) {
                    gameDiagnostics.flow(event, phase, run, step, generation);
                }
                public Object scheduled(String reason, long delay, long run, int step, int generation) {
                    return gameDiagnostics.scheduled(reason, delay, run, step, generation);
                }
                public void executed(Object captured, boolean accepted) {
                    gameDiagnostics.executed((GameDiagnostics.Callback) captured, accepted);
                }
            });
        }
    }

    protected void showFailure() {
        super.showFailure();
        if (this.dialog != null) {
            this.dialog.dismiss();
            disablePausePanel();
        }
    }
}
