package com.cube.memorygames;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Typeface;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import com.cube.memorygames.api.local.challenge.ChallengeJsonGame;
import com.cube.memorygames.model.CategoryInfo;
import com.cube.memorygames.model.GameInfo;
import com.memory.brain.training.games.R;
import com.squareup.picasso.Picasso;

public class ChallengeStartDialog extends Dialog {
    private Activity activity;
    @BindView(R.id.categoryColorView)
    View categoryColorView;
    private ChallengeJsonGame challengeJsonGame;
    private GameInfo gameInfo;
    @BindView(R.id.gameName)
    TextView gameName;
    @BindView(R.id.image)
    ImageView image;
    @BindView(R.id.play)
    TextView play;
    @BindView(R.id.score)
    TextView score;
    @BindView(R.id.tutorial)
    TextView tutorial;

    public ChallengeStartDialog(Activity activity, ChallengeJsonGame challengeJsonGame) {
        super(activity);
        getWindow().requestFeature(1);
        setCancelable(true);
        this.activity = activity;
        this.challengeJsonGame = challengeJsonGame;
        this.gameInfo = Games.get().getGameByName(challengeJsonGame.getName());
        CategoryInfo categoryInfo = Games.get().getCategoryInfo(this.gameInfo);
        setContentView(R.layout.dialog_challenge_start);
        ButterKnife.bind((Dialog) this);
        Typeface typeface = Typeface.createFromAsset(activity.getAssets(), "Roboto-Light.ttf");
        this.play.setTypeface(typeface);
        this.tutorial.setTypeface(typeface);
        this.gameName.setTypeface(typeface);
        this.gameName.setText(this.gameInfo.getGameNameRes());
        this.categoryColorView.setBackgroundColor(ContextCompat.getColor(activity, categoryInfo.getColorResId()));
        Picasso.with(activity).load(this.gameInfo.getGameImageRes()).into(this.image);
    }

    @OnClick({R.id.tutorial})
    void tutorialClick() {
        dismiss();
        Games.get().forceStartChallengeTutorial(this.activity, this.challengeJsonGame, false);
    }

    @OnClick({R.id.play})
    void playClick() {
        dismiss();
        Games.get().startChallengeGame(this.activity, this.challengeJsonGame, this.gameInfo);
    }
}
