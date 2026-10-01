package com.cube.memorygames.ui;

import android.app.Activity;
import android.app.Dialog;
import android.support.v7.app.AppCompatDialog;
import android.view.View;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import com.memory.brain.training.games.R;

public class WorkoutHelpDialog extends AppCompatDialog {
    @BindView(R.id.close)
    View close;

    public WorkoutHelpDialog(Activity activity) {
        super(activity, R.style.GdxTheme);
        setContentView((int) R.layout.dialog_workout_help);
        ButterKnife.bind((Dialog) this);
        getWindow().setBackgroundDrawableResource(R.color.dark_transparent);
        getWindow().clearFlags(2);
    }

    @OnClick({R.id.close})
    public void onCloseClick() {
        dismiss();
    }
}
