package com.cube.memorygames.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.graphics.Typeface;
import androidx.appcompat.app.AppCompatDialog;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;
import android.widget.ToggleButton;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import com.cube.memorygames.MainMenuActivity;
import com.cube.memorygames.MemoryApplicationModel;
import com.cube.memorygames.SoundUtils;
import com.cube.memorygames.StartGameActivity;
import com.cube.memorygames.model.GameInfo;
import com.memory.brain.training.games.R;

public class PauseDialog extends AppCompatDialog {
    private Activity activity;
    @BindView(R.id.exitButton)
    TextView exitButton;
    private View.OnClickListener exitClickListener;
    private GameInfo gameInfo;
    @BindView(R.id.replayButton)
    TextView replayButton;
    private View.OnClickListener replayClickListener;
    @BindView(R.id.resumeButton)
    TextView resumeButton;
    @BindView(R.id.root)
    View root;
    @BindView(R.id.sound)
    ToggleButton sound;
    @BindView(R.id.title)
    TextView title;
    @BindView(R.id.tutorialButton)
    TextView tutorialButton;

    public PauseDialog(Activity activity, GameInfo gameInfo, View.OnClickListener exitClickListener, View.OnClickListener replayClickListener, OnDismissListener onDismissListener) {
        super(activity, R.style.AppDialogTheme);
        DialogUi.setContent(this, R.layout.dialog_pause);
        setCancelable(true);
        setCanceledOnTouchOutside(false);
        ButterKnife.bind((Dialog) this);
        setOnDismissListener(onDismissListener);
        this.activity = activity;
        this.gameInfo = gameInfo;
        this.exitClickListener = exitClickListener;
        this.replayClickListener = replayClickListener;
        Typeface typeFaceRoboto = Typeface.createFromAsset(activity.getAssets(), "Roboto-Light.ttf");
        this.title.setTypeface(typeFaceRoboto);
        this.resumeButton.setTypeface(typeFaceRoboto);
        this.replayButton.setTypeface(typeFaceRoboto);
        this.tutorialButton.setTypeface(typeFaceRoboto);
        this.exitButton.setTypeface(typeFaceRoboto);
        this.sound.setChecked(SoundUtils.isPlaySound(activity));
        configureActionRow(R.id.resumeContainer, this.resumeButton);
        configureActionRow(R.id.replayContainer, this.replayButton);
        configureActionRow(R.id.tutorialContainer, this.tutorialButton);
        configureActionRow(R.id.exitContainer, this.exitButton);
        updateSoundDescription();
    }

    private void configureActionRow(int id, TextView label) {
        View row = findViewById(id);
        row.setContentDescription(label.getText());
        row.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(android.widget.Button.class.getName());
            }
        });
    }

    private void updateSoundDescription() {
        View row = findViewById(R.id.panel_pause);
        row.setContentDescription(getContext().getString(this.sound.isChecked()
                ? R.string.dg_sound_on : R.string.dg_sound_off));
        row.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(android.widget.Button.class.getName());
                info.setCheckable(true);
                info.setChecked(PauseDialog.this.sound.isChecked());
            }
        });
        ((TextView) findViewById(R.id.dg_sound_label)).setText(this.sound.isChecked()
                ? R.string.dg_sound_on : R.string.dg_sound_off);
    }

    @OnClick({R.id.panel_pause})
    protected void soundClick() {
        MemoryApplicationModel application = MemoryApplicationModel.getInstance();
        this.sound.setChecked(!this.sound.isChecked());
        if (this.sound.isChecked()) {
            application.logEvent(getClass().getSimpleName(), MemoryApplicationModel.ANALYTICS_CATEGORY_GENERAL, MemoryApplicationModel.ANALYTICS_EVENT_SOUND_ON);
        } else {
            application.logEvent(getClass().getSimpleName(), MemoryApplicationModel.ANALYTICS_CATEGORY_GENERAL, MemoryApplicationModel.ANALYTICS_EVENT_SOUND_OFF);
        }
        SoundUtils.setPlaySound(getContext(), this.sound.isChecked());
        updateSoundDescription();
    }

    @OnClick({R.id.tutorialContainer})
    void tutorialContainerClick() {
        Intent intent = new Intent(this.activity, StartGameActivity.class);
        intent.putExtra(StartGameActivity.EXTRA_CHALLENGE, false);
        intent.putExtra(StartGameActivity.EXTRA_DISABLE_PLAY_BUTTON, true);
        intent.putExtra(MainMenuActivity.EXTRA_GAME_INFO, this.gameInfo);
        this.activity.startActivity(intent);
        this.activity.overridePendingTransition(R.anim.slide_to_left, R.anim.no_change);
    }

    @OnClick({R.id.exitContainer})
    void exitContainerClick() {
        setOnDismissListener(null);
        if (this.exitClickListener != null) {
            this.exitClickListener.onClick(null);
        }
        dismiss();
    }

    @OnClick({R.id.replayContainer})
    void replayContainerClick() {
        setOnDismissListener(null);
        if (this.replayClickListener != null) {
            this.replayClickListener.onClick(null);
        }
        dismiss();
    }

    @OnClick({R.id.resumeContainer})
    void resumeContainerClick() {
        dismiss();
    }

    public void onBackPressed() {
        resumeContainerClick();
    }
}
