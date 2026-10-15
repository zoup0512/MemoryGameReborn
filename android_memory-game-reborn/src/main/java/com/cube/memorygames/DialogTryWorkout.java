package com.cube.memorygames;

import android.app.Activity;
import android.app.Dialog;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatDialog;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import com.memory.brain.training.games.R;
import com.cube.memorygames.ui.DialogUi;

public class DialogTryWorkout extends AppCompatDialog {
    public static final int STARTS_COUNT = 3;
    public static final String STARTS_COUNT_BEFORE_SHOW_KEY = "starts_before_show_workout";
    public static final String TRY_WORKOUT_DIALOG_SHOW_KEY = "try_workout_dialog_show";
    private View.OnClickListener mConfirmListener;
    @BindView(R.id.dialog_try_workout_cancel_button)
    Button nCancelButton;

    public DialogTryWorkout(Activity activity, View.OnClickListener confirmListener) {
        super(activity, R.style.AppDialogTheme);
        DialogUi.setContent(this, R.layout.dialog_try_workout);
        ButterKnife.bind((Dialog) this);
        this.mConfirmListener = confirmListener;
    }

    @OnClick({R.id.dialog_try_workout_confirm_button})
    public void onConfirmClick() {
        dismiss();
        if (this.mConfirmListener != null) {
            this.mConfirmListener.onClick(null);
        }
    }

    @OnClick(R.id.dg_workout_never)
    public void onNeverClick() {
        PreferenceManager.getDefaultSharedPreferences(getContext()).edit()
                .putBoolean(TRY_WORKOUT_DIALOG_SHOW_KEY, false).apply();
        dismiss();
    }

    @OnClick({R.id.dialog_try_workout_cancel_button})
    public void onCancelClick() {
        dismiss();
    }
}
