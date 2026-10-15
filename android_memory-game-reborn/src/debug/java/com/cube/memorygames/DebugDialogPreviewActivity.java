package com.cube.memorygames;

import android.app.Dialog;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import com.cube.memorygames.api.online.OnlineDialogs;
import com.cube.memorygames.model.GameInfo;
import com.cube.memorygames.ui.DialogUi;
import com.cube.memorygames.ui.PauseDialog;
import com.cube.memorygames.ui.RateDialog;
import com.cube.memorygames.ui.TrophyDialog;
import com.cube.memorygames.ui.WorkoutHelpDialog;
import com.memory.brain.training.games.R;

/** Debug-only, local preview: callbacks never charge coins, submit scores or upload data. */
public final class DebugDialogPreviewActivity extends AppCompatActivity {
    private LinearLayout list;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        float fontScale = getIntent().getFloatExtra("previewFontScale", 0f);
        int widthDp = getIntent().getIntExtra("previewWidthDp", 0);
        if (fontScale > 0f || widthDp > 0) {
            android.content.res.Configuration configuration = new android.content.res.Configuration(getResources().getConfiguration());
            android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
            metrics.setTo(getResources().getDisplayMetrics());
            if (fontScale > 0f) configuration.fontScale = fontScale;
            if (widthDp > 0) {
                metrics.density = metrics.widthPixels / (float) widthDp;
                metrics.densityDpi = Math.round(metrics.density * 160f);
                configuration.densityDpi = metrics.densityDpi;
                configuration.screenWidthDp = widthDp;
            }
            metrics.scaledDensity = metrics.density * configuration.fontScale;
            getResources().updateConfiguration(configuration, metrics);
        }
        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int padding = DialogUi.dp(this, 20);
        list.setPadding(padding, padding, padding, padding);
        scroll.addView(list);
        setContentView(scroll);
        add("Pause", () -> {
            GameInfo game = new GameInfo(R.string.game_name_1, "Memory Grid", R.drawable.th2, 30, "0");
            new PauseDialog(this, game, v -> { }, v -> { }, d -> { }).show();
        });
        add("Retry", () -> new FailDialog(this, 0, Dialog::dismiss, Dialog::dismiss, Dialog::dismiss).show());
        add("Challenge result", () -> {
            ChallengeResultDialog result = new ChallengeResultDialog(this, R.string.game_name_1,
                    180, 2, Dialog::dismiss, Dialog::dismiss, Dialog::dismiss);
            result.setGoalScore(200);
            result.show();
        });
        add("Trophy", () -> new TrophyDialog(this, R.drawable.cup_bronze_1,
                Games.get().getTrophyStrings().get(0), d -> { }).setAchievementLevel(10).show());
        add("Feedback", () -> new RateDialog(this, R.string.dialog_feedback_title,
                R.string.dialog_leave_feedback_yes, R.string.dialog_leave_feedback_no,
                false, R.drawable.lionkid3, v -> { }, v -> { }).show());
        add("Workout help", () -> new WorkoutHelpDialog(this).show());
        add("Workout invitation", () -> new DialogTryWorkout(this, v -> { }).show());
        add("Practice exit", () -> DialogUi.builder(this)
                .setTitle(R.string.dialog_exit_practice_title).setMessage(R.string.dialog_exit_practice_body)
                .setNegativeButton(R.string.dialog_exit_practice, null)
                .setPositiveButton(R.string.dialog_continue_practice, null).show());
        add("Room code", () -> OnlineDialogs.showJoinRoomCode(this, code -> { }));
        add("Room waiting", () -> OnlineDialogs.showFriendRoomWaiting(this, "1234",
                android.os.SystemClock.elapsedRealtime(), v -> { }));
        add("Difficulty", () -> OnlineDialogs.showBotDifficulty(this, tier -> { }));
        add("Coins", () -> OnlineDialogs.showNotEnoughCoins(this));
        add("Result layout", () -> showLayout(R.layout.dialog_timeout));
        add("Answer layout", () -> {
            Dialog dialog = new Dialog(this, R.style.AppDialogTheme);
            DialogUi.setContent(dialog, R.layout.dialog_game5);
            int[] ids = {R.id.button_dialog_game5_cell_count_1, R.id.button_dialog_game5_cell_count_2,
                    R.id.button_dialog_game5_cell_count_3, R.id.button_dialog_game5_cell_count_4};
            int[] values = com.cube.memorygames.logic.CountAnswerOptions.generate(4);
            for (int i = 0; i < ids.length; i++) ((android.widget.TextView) dialog.findViewById(ids[i])).setText(Integer.toString(values[i]));
            ((android.widget.TextView) dialog.findViewById(R.id.title)).setText(R.string.game5_dialog_text);
            dialog.show();
        });
        add("Unlock layout", () -> showLayout(R.layout.dialog_unlock));
        String selected = getIntent().getStringExtra("preview");
        if (selected != null) {
            for (int i = 0; i < list.getChildCount(); i++) {
                Button button = (Button) list.getChildAt(i);
                if (selected.contentEquals(button.getText())) button.post(button::performClick);
            }
        }
    }

    private void showLayout(int layout) {
        Dialog dialog = new Dialog(this, R.style.AppDialogTheme);
        DialogUi.setContent(dialog, layout);
        if (layout == R.layout.dialog_timeout) {
            ((android.widget.TextView) dialog.findViewById(R.id.dialog_result_level)).setText(getString(R.string.dialog_result_level, 3));
            ((android.widget.TextView) dialog.findViewById(R.id.dialog_result_best)).setText(getString(R.string.dialog_result_best, 10));
        }
        if (layout == R.layout.dialog_unlock) {
            ((android.widget.TextView) dialog.findViewById(R.id.message)).setText(getString(R.string.dg_unlock_details, getString(R.string.game_name_1), 100, 60));
        }
        dialog.show();
    }

    private void add(String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(v -> action.run());
        list.addView(button, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }
}
