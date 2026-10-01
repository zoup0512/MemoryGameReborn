package com.cube.memorygames;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatDialog;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import com.memory.brain.training.games.R;

public class DialogTryWorkout extends AppCompatDialog {
    public static final int STARTS_COUNT = 3;
    public static final String STARTS_COUNT_BEFORE_SHOW_KEY = "starts_before_show_workout";
    public static final String TRY_WORKOUT_DIALOG_SHOW_KEY = "try_workout_dialog_show";
    private View.OnClickListener mConfirmListener;
    @BindView(R.id.dialog_try_workout_cancel_button)
    Button nCancelButton;

    public DialogTryWorkout(Activity activity, View.OnClickListener confirmListener) {
        super(activity, R.style.GdxTheme);
        setContentView((int) R.layout.dialog_try_workout);
        ButterKnife.bind((Dialog) this);
        getWindow().setBackgroundDrawableResource(R.color.dark_transparent);
        getWindow().clearFlags(2);
        this.mConfirmListener = confirmListener;
        setOnDismissListener(new OnDismissListener() {
            public void onDismiss(DialogInterface dialogInterface) {
                PreferenceManager.getDefaultSharedPreferences(DialogTryWorkout.this.getContext()).edit().putBoolean(DialogTryWorkout.TRY_WORKOUT_DIALOG_SHOW_KEY, false).apply();
            }
        });
    }

    @OnClick({R.id.dialog_try_workout_confirm_button})
    public void onConfirmClick() {
        this.mConfirmListener.onClick(null);
        dismiss();
    }

    @OnClick({R.id.dialog_try_workout_cancel_button})
    public void onCancelClick() {
        dismiss();
    }
}
