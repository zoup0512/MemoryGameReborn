package com.cube.memorygames;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Typeface;
import android.support.v4.content.ContextCompat;
import android.widget.ImageView;
import android.widget.TextView;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import com.memory.brain.training.games.R;

public class ChallengeResultDialog extends Dialog {
    @BindView(R.id.gameName)
    TextView gameName;
    @BindView(R.id.home)
    TextView home;
    private OnDialogClickListener homeClickListener;
    @BindView(R.id.next)
    TextView next;
    private OnDialogClickListener nextClickListener;
    private OnDialogClickListener replayClickListener;
    @BindView(R.id.score)
    TextView score;
    @BindView(R.id.star1)
    ImageView star1;
    @BindView(R.id.star2)
    ImageView star2;
    @BindView(R.id.star3)
    ImageView star3;
    private int stars;

    public interface OnDialogClickListener {
        void onDialogClick(Dialog dialog);
    }

    public ChallengeResultDialog(Activity activity, int gameName, int score, int stars, OnDialogClickListener homeClickListener, OnDialogClickListener nextClickListener, OnDialogClickListener replayClickListener) {
        super(activity);
        getWindow().setBackgroundDrawableResource(17170445);
        getWindow().requestFeature(1);
        getWindow().clearFlags(2);
        setCancelable(false);
        this.homeClickListener = homeClickListener;
        this.nextClickListener = nextClickListener;
        this.replayClickListener = replayClickListener;
        this.stars = stars;
        setContentView(R.layout.dialog_challenge_result);
        ButterKnife.bind((Dialog) this);
        Typeface typeface = Typeface.createFromAsset(activity.getAssets(), "Roboto-Light.ttf");
        this.gameName.setTypeface(typeface);
        this.score.setTypeface(typeface);
        this.home.setTypeface(typeface);
        this.next.setTypeface(typeface);
        this.gameName.setText(gameName);
        if (stars < 3) {
            this.star3.setColorFilter(ContextCompat.getColor(activity, R.color.background));
        }
        if (stars < 2) {
            this.star2.setColorFilter(ContextCompat.getColor(activity, R.color.background));
        }
        if (stars < 1) {
            this.star1.setColorFilter(ContextCompat.getColor(activity, R.color.background));
            this.next.setText(R.string.replay);
        }
        this.score.setText("" + score);
    }

    @OnClick({R.id.next})
    void nextClick() {
        if (this.stars <= 0) {
            if (this.replayClickListener != null) {
                this.replayClickListener.onDialogClick(this);
            }
        } else if (this.nextClickListener != null) {
            this.nextClickListener.onDialogClick(this);
        }
    }

    @OnClick({R.id.home})
    void homeClick() {
        if (this.homeClickListener != null) {
            this.homeClickListener.onDialogClick(this);
        }
    }

    public void onBackPressed() {
        if (this.homeClickListener != null) {
            this.homeClickListener.onDialogClick(this);
        }
    }
}
