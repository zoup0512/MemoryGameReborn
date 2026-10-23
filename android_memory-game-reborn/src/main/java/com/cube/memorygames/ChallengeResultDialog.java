package com.cube.memorygames;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Typeface;
import androidx.core.content.ContextCompat;
import android.widget.ImageView;
import android.widget.TextView;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import com.memory.brain.training.games.R;
import com.cube.memorygames.ui.DialogUi;

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
    private int achievedScore;
    @BindView(R.id.dg_challenge_goal) TextView goal;
    @BindView(R.id.dg_challenge_difference) TextView difference;
    @BindView(R.id.dg_challenge_stars) TextView starsDescription;

    public interface OnDialogClickListener {
        void onDialogClick(Dialog dialog);
    }

    public ChallengeResultDialog(Activity activity, int gameName, int score, int stars, OnDialogClickListener homeClickListener, OnDialogClickListener nextClickListener, OnDialogClickListener replayClickListener) {
        super(activity);
        setCancelable(true);
        setCanceledOnTouchOutside(false);
        this.homeClickListener = homeClickListener;
        this.nextClickListener = nextClickListener;
        this.replayClickListener = replayClickListener;
        this.stars = Math.max(0, Math.min(3, stars));
        this.achievedScore = score;
        DialogUi.setContent(this, R.layout.dialog_challenge_result);
        ButterKnife.bind((Dialog) this);
        Typeface typeface = Typeface.createFromAsset(activity.getAssets(), "Roboto-Light.ttf");
        this.gameName.setTypeface(typeface);
        this.score.setTypeface(typeface);
        this.home.setTypeface(typeface);
        this.next.setTypeface(typeface);
        this.gameName.setText(gameName);
        if (stars < 3) {
            this.star3.setColorFilter(ContextCompat.getColor(activity, R.color.dialog_text_secondary));
        }
        if (stars < 2) {
            this.star2.setColorFilter(ContextCompat.getColor(activity, R.color.dialog_text_secondary));
        }
        if (stars < 1) {
            this.star1.setColorFilter(ContextCompat.getColor(activity, R.color.dialog_text_secondary));
            this.next.setText(R.string.replay);
        }
        this.score.setText(activity.getString(R.string.dg_challenge_score, score));
        this.starsDescription.setText(activity.getString(R.string.dg_challenge_stars, this.stars));
        this.next.setText(this.stars > 0 ? R.string.dg_challenge_next : R.string.dg_challenge_retry);
    }

    public void setGoalScore(int goalScore) {
        this.goal.setText(getContext().getString(R.string.dg_challenge_goal, goalScore));
        long gap = (long) goalScore - this.achievedScore;
        this.difference.setText(gap > 0
                ? getContext().getString(R.string.dg_challenge_gap, gap)
                : getContext().getString(R.string.dg_challenge_goal_met));
        this.starsDescription.setText(getContext().getString(R.string.dg_challenge_star_rules,
                this.stars, (int) (goalScore * 0.5d), (int) (goalScore * 0.75d), goalScore));
        this.goal.setVisibility(android.view.View.VISIBLE);
        this.difference.setVisibility(android.view.View.VISIBLE);
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
