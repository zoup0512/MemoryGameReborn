package com.cube.memorygames.ui;

import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.graphics.Typeface;
import android.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView.Adapter;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;
import android.text.TextUtils;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import butterknife.BindView;
import butterknife.BindViews;
import butterknife.ButterKnife;
import com.cube.memorygames.MainMenuActivity;
import com.cube.memorygames.MemoryApplicationModel;
import com.cube.memorygames.StartGameActivity;
import com.cube.memorygames.WorkoutProgressView;
import com.cube.memorygames.api.local.LocalDataManager;
import com.cube.memorygames.api.local.workout.DayInfo;
import com.cube.memorygames.api.local.workout.LevelInfo;
import com.cube.memorygames.games.Game1MemoryGridActivity;
import com.github.florent37.viewanimator.ViewAnimator;
import com.google.android.gms.common.ConnectionResult;
import com.memory.brain.training.games.R;
import io.fabric.sdk.android.services.settings.SettingsJsonConstants;
import java.util.List;

public class WorkoutAdapter extends Adapter<WorkoutAdapter.UserViewHolder> {
    private static final String PREF_LAST_RATING = "prefLastRating";
    private MainMenuActivity activity;
    private LocalDataManager dataManager = MemoryApplicationModel.getInstance().getLocalDataManager();
    private int[] levels = new int[]{50, 100, 200, Game1MemoryGridActivity.START_ANIMATION_DURATION, SettingsJsonConstants.ANALYTICS_FLUSH_INTERVAL_SECS_DEFAULT, 800, 1000, 1200, ConnectionResult.DRIVE_EXTERNAL_STORAGE_REQUIRED};
    private Typeface typeface;

    class UserViewHolder extends ViewHolder {
        @BindView(R.id.workout_current_iq)
        TextView currentIq;
        @BindViews({R.id.game1, R.id.game2, R.id.game3, R.id.game4})
        List<WorkoutGameView> gameViews;
        @BindView(R.id.generatingLayout)
        View generatingLayout;
        @BindView(R.id.generatingText)
        TextView generatingText;
        @BindView(R.id.workout_iq_rank)
        TextView iqRank;
        @BindView(R.id.workout_iq_to_next_level)
        TextView iqToNextLevel;
        @BindView(R.id.squareLoading)
        SquareLoading squareLoading;
        @BindView(R.id.weekResults)
        LinearLayout weekResults;
        @BindViews({R.id.week1, R.id.week2, R.id.week3, R.id.week4, R.id.week5, R.id.week6, R.id.week7})
        List<WorkoutWeekView> weekViews;
        @BindView(R.id.workout_info_button)
        View workoutInfoButton;
        @BindView(R.id.workoutProgressView)
        WorkoutProgressView workoutProgressView;

        UserViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind((Object) this, itemView);
        }

        void populateGames() {
            int i;
            List<LevelInfo> games = WorkoutAdapter.this.dataManager.getWorkoutGames();
            for (i = 0; i < games.size(); i++) {
                WorkoutGameView workoutGameView = (WorkoutGameView) this.gameViews.get(i);
                workoutGameView.setLevelInfo((LevelInfo) games.get(i));
                final LevelInfo levelInfo = (LevelInfo) games.get(i);
                if (levelInfo.isFinished()) {
                    workoutGameView.setOnClickListener(null);
                } else {
                    final int finalI = i;
                    workoutGameView.setOnClickListener(new OnClickListener() {
                        public void onClick(View v) {
                            Intent intent = new Intent(WorkoutAdapter.this.activity, StartGameActivity.class);
                            intent.putExtra(StartGameActivity.EXTRA_CHALLENGE, false);
                            intent.putExtra(StartGameActivity.EXTRA_WORKOUT, true);
                            intent.putExtra(MainMenuActivity.EXTRA_GAME_INFO, levelInfo.getGameInfo());
                            WorkoutAdapter.this.activity.startActivity(intent);
                            WorkoutAdapter.this.activity.overridePendingTransition(R.anim.slide_to_left, R.anim.no_change);
                        }
                    });
                }
            }
            this.workoutInfoButton.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    new WorkoutHelpDialog(WorkoutAdapter.this.activity).show();
                }
            });
            this.workoutInfoButton.setOnLongClickListener(new OnLongClickListener() {
                public boolean onLongClick(View v) {
                    return true;
                }
            });
            List<DayInfo> dayInfoList = WorkoutAdapter.this.dataManager.getCurrentWeekGames();
            for (i = 0; i < this.weekViews.size(); i++) {
                ((WorkoutWeekView) this.weekViews.get(i)).setDayInfo((DayInfo) dayInfoList.get(i));
            }
            this.weekResults.setVisibility(0);
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(WorkoutAdapter.this.activity);
            int newRating = (int) WorkoutAdapter.this.dataManager.getWorkoutRating();
            animateRating(sharedPreferences.getInt(WorkoutAdapter.PREF_LAST_RATING, 0), newRating);
            sharedPreferences.edit().putInt(WorkoutAdapter.PREF_LAST_RATING, newRating).apply();
        }

        private void animateRating(int from, int to) {
            applyRating(from);
            if (from != to) {
                ValueAnimator animator = ValueAnimator.ofInt(new int[]{from, to});
                animator.addUpdateListener(new AnimatorUpdateListener() {
                    public void onAnimationUpdate(ValueAnimator animation) {
                        UserViewHolder.this.applyRating(((Integer) animation.getAnimatedValue()).intValue());
                    }
                });
                animator.setDuration((long) (Math.abs(to - from) * 20));
                animator.setStartDelay(400);
                animator.setInterpolator(new LinearInterpolator());
                animator.start();
            }
        }

        private void applyRating(int rating) {
            this.workoutProgressView.setProgress(WorkoutAdapter.this.getCurrentScore(rating));
            this.workoutProgressView.setMax(WorkoutAdapter.this.getMaxScore(rating));
            String level = String.valueOf(WorkoutAdapter.this.getLevelNumber(rating));
            String levelText = String.format(this.iqRank.getContext().getString(R.string.iq_level_text), new Object[]{level});
            if (!(TextUtils.isEmpty(this.iqRank.getText()) || levelText.equals(this.iqRank.getText().toString()))) {
                ViewAnimator.animate(this.iqRank).fadeIn().duration(400).start();
            }
            this.iqRank.setText(levelText);
            this.currentIq.setText(String.valueOf(rating));
            this.iqToNextLevel.setText(String.format(this.iqToNextLevel.getContext().getString(R.string.workout_sp_to_next_level), new Object[]{String.valueOf(WorkoutAdapter.this.getMaxScore(rating) - getCurrentScore(rating))}));
        }

        void hideGames() {
            for (WorkoutGameView workoutGameView : this.gameViews) {
                workoutGameView.setLevelInfo(null);
                workoutGameView.setTranslationX((float) WorkoutAdapter.this.getWindowWidth(WorkoutAdapter.this.activity));
            }
            this.weekResults.setVisibility(4);
            this.workoutProgressView.setProgress(0);
            this.iqRank.setText("");
            this.currentIq.setText("");
            this.iqToNextLevel.setText("");
            this.generatingLayout.setVisibility(0);
            this.squareLoading.startAnim();
        }

        void animateGames() {
            this.generatingLayout.setVisibility(8);
            for (int i = 0; i < this.gameViews.size(); i++) {
                ViewAnimator.animate((WorkoutGameView) this.gameViews.get(i)).translationX(0.0f).interpolator(new DecelerateInterpolator()).duration(500).startDelay((long) (i * 100)).start();
            }
        }
    }

    public WorkoutAdapter(MainMenuActivity activity) {
        this.activity = activity;
        this.typeface = Typeface.createFromAsset(activity.getAssets(), "Roboto-Light.ttf");
    }

    public UserViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        UserViewHolder userViewHolder = new UserViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_workout, parent, false));
        for (WorkoutGameView workoutGameView : userViewHolder.gameViews) {
            workoutGameView.setTypeface(this.typeface);
        }
        for (WorkoutWeekView workoutWeekView : userViewHolder.weekViews) {
            workoutWeekView.setTypeface(this.typeface);
        }
        userViewHolder.generatingText.setTypeface(this.typeface);
        return userViewHolder;
    }

    public void onBindViewHolder(final UserViewHolder holder, int position) {
        if (this.dataManager.isWorkoutGameGenerated()) {
            holder.generatingLayout.setVisibility(8);
            holder.populateGames();
            return;
        }
        holder.hideGames();
        holder.populateGames();
        new Thread(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(2500);
                } catch (InterruptedException e) {
                }
                if (!WorkoutAdapter.this.activity.isFinishing()) {
                    WorkoutAdapter.this.activity.runOnUiThread(new Runnable() {
                        public void run() {
                            holder.animateGames();
                        }
                    });
                }
            }
        }).start();
    }

    public int getItemCount() {
        return 1;
    }

    private int getLevelNumber(int rating) {
        for (int i = 0; i < this.levels.length; i++) {
            if (rating < this.levels[i]) {
                return i + 1;
            }
        }
        return this.levels.length;
    }

    private int getMaxScore(int rating) {
        int i = 0;
        while (i < this.levels.length) {
            if (rating >= this.levels[i]) {
                i++;
            } else if (i == 0) {
                return this.levels[0];
            } else {
                return this.levels[i] - this.levels[i - 1];
            }
        }
        return this.levels[this.levels.length - 1] - this.levels[this.levels.length - 2];
    }

    private int getCurrentScore(int rating) {
        int i = 0;
        while (i < this.levels.length) {
            if (rating >= this.levels[i]) {
                i++;
            } else if (i == 0) {
                return rating;
            } else {
                return rating - this.levels[i - 1];
            }
        }
        return 0;
    }

    private int getWindowWidth(Context context) {
        Display display = ((WindowManager) context.getSystemService("window")).getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        return size.x;
    }
}
