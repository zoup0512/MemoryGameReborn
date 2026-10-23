package com.cube.memorygames.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface.OnDismissListener;
import android.graphics.Typeface;
import androidx.appcompat.app.AppCompatDialog;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import com.cube.memorygames.MemoryApplicationModel;
import com.memory.brain.training.games.R;

public class TrophyDialog extends AppCompatDialog {
    private final OnDismissListener continueListener;
    @BindView(R.id.continueButton)
    TextView continueButton;
    @BindView(R.id.cupImage)
    ImageView cupImage;
    @BindView(R.id.root)
    View root;
    @BindView(R.id.subTitle)
    TextView subTitle;
    @BindView(R.id.title)
    TextView title;

    public TrophyDialog(Context context, int cupResId, int textResId, OnDismissListener onDismissListener) {
        super(context, R.style.AppDialogTheme);
        DialogUi.setContent(this, R.layout.dialog_trophy);
        // Only the explicit continue action may advance the game's dismiss callback.
        setCancelable(false);
        setCanceledOnTouchOutside(false);
        getWindow().setWindowAnimations(R.style.WinDialogAnimation);
        ButterKnife.bind((Dialog) this);
        this.continueListener = onDismissListener;
        this.cupImage.setImageResource(cupResId);
        Typeface typeFaceRoboto = Typeface.createFromAsset(context.getAssets(), "Roboto-Light.ttf");
        this.continueButton.setTypeface(typeFaceRoboto);
        this.title.setTypeface(typeFaceRoboto);
        this.subTitle.setTypeface(typeFaceRoboto);
        this.subTitle.setText(textResId);
    }

    public TrophyDialog setAchievementLevel(int level) {
        TextView achievement = (TextView) findViewById(R.id.dg_achievement_level);
        achievement.setText(getContext().getString(R.string.dg_achievement_level, level));
        achievement.setVisibility(View.VISIBLE);
        this.cupImage.setContentDescription(this.subTitle.getText() + ", " + achievement.getText());
        return this;
    }

    @Override
    public void cancel() {
        // A lifecycle/programmatic cancellation must not be interpreted as Continue.
        setOnDismissListener(null);
        super.cancel();
    }

    @Override
    public void onBackPressed() {
        // The underlying game stays paused until Continue is chosen.
    }

    @OnClick({R.id.continueButton})
    void continueButtonClick() {
        // Arm the original callback only for an explicit user confirmation.
        this.continueButton.setEnabled(false);
        setOnDismissListener(this.continueListener);
        dismiss();
    }
}
