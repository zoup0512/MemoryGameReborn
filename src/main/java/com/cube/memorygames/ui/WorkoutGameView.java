package com.cube.memorygames.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.support.annotation.AttrRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import butterknife.BindView;
import butterknife.ButterKnife;
import com.cube.memorygames.Games;
import com.cube.memorygames.api.local.workout.LevelInfo;
import com.cube.memorygames.model.GameInfo;
import com.memory.brain.training.games.R;
import com.squareup.picasso.Picasso;

public class WorkoutGameView extends FrameLayout {
    @BindView(R.id.categoryColorView)
    View categoryColorView;
    @BindView(R.id.finishedLayout)
    View finishedLayout;
    @BindView(R.id.gameImage)
    ImageView gameImage;
    @BindView(R.id.gameName)
    TextView gameName;
    private LevelInfo levelInfo;

    public WorkoutGameView(@NonNull Context context) {
        super(context);
        init();
    }

    public WorkoutGameView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WorkoutGameView(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        inflate(getContext(), R.layout.view_game_workout, this);
        ButterKnife.bind((View) this);
    }

    public LevelInfo getLevelInfo() {
        return this.levelInfo;
    }

    public void setLevelInfo(LevelInfo levelInfo) {
        this.levelInfo = levelInfo;
        if (levelInfo == null) {
            this.gameImage.setImageBitmap(null);
            this.gameName.setText(null);
            this.categoryColorView.setBackgroundColor(0);
            this.finishedLayout.setVisibility(8);
            return;
        }
        GameInfo gameInfo = levelInfo.getGameInfo();
        this.gameName.setText(gameInfo.getGameNameRes());
        this.gameImage.setImageBitmap(null);
        Picasso.with(getContext()).load(gameInfo.getGameImageRes()).into(this.gameImage);
        if (levelInfo.isFinished()) {
            this.categoryColorView.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.grid_unclicked));
            this.gameName.setTextColor(ContextCompat.getColor(getContext(), R.color.grid_unclicked));
            this.finishedLayout.setVisibility(0);
            return;
        }
        this.categoryColorView.setBackgroundColor(ContextCompat.getColor(getContext(), Games.get().getCategoryInfo(gameInfo).getColorResId()));
        this.gameName.setTextColor(-16777216);
        this.finishedLayout.setVisibility(8);
    }

    public void setTypeface(Typeface typeface) {
        this.gameName.setTypeface(typeface);
    }
}
