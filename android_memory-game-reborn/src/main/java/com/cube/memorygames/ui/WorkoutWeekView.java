package com.cube.memorygames.ui;

import android.content.Context;
import android.graphics.Typeface;
import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import butterknife.BindView;
import butterknife.ButterKnife;
import com.cube.memorygames.api.local.workout.DayInfo;
import com.memory.brain.training.games.R;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class WorkoutWeekView extends FrameLayout {
    private DayInfo dayInfo;
    @BindView(R.id.dayOfMonth)
    TextView dayOfMonth;
    @BindView(R.id.dayOfWeek)
    TextView dayOfWeek;
    @BindView(R.id.result)
    ImageView result;

    public WorkoutWeekView(@NonNull Context context) {
        super(context);
        init();
    }

    public WorkoutWeekView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WorkoutWeekView(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        inflate(getContext(), R.layout.view_week_workout, this);
        ButterKnife.bind((View) this);
    }

    public DayInfo getDayInfo() {
        return this.dayInfo;
    }

    public void setDayInfo(DayInfo dayInfo) {
        this.dayInfo = dayInfo;
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Date workoutDate = null;
        try {
            workoutDate = dateFormat.parse(dayInfo.getDate());
        } catch (ParseException e) {
            e.printStackTrace();
        }
        if (dayInfo.getNumberOfCompletedGames() == 4) {
            this.dayOfWeek.setTextColor(ContextCompat.getColor(getContext(), R.color.workout_progress_filled));
            this.dayOfMonth.setTextColor(ContextCompat.getColor(getContext(), R.color.workout_progress_filled));
            this.result.setImageResource(R.drawable.calendar_1);
        } else if (dayInfo.getDate().equals(dateFormat.format(new Date()))) {
            this.dayOfWeek.setTextColor(ContextCompat.getColor(getContext(), R.color.workout_progress_filled));
            this.dayOfMonth.setTextColor(ContextCompat.getColor(getContext(), R.color.workout_progress_filled));
            this.result.setImageResource(R.drawable.calendar_3);
        } else if (workoutDate != null && workoutDate.before(new Date())) {
            this.dayOfWeek.setTextColor(ContextCompat.getColor(getContext(), R.color.workout_calendar_progress_empty));
            this.dayOfMonth.setTextColor(ContextCompat.getColor(getContext(), R.color.workout_calendar_progress_empty));
            this.result.setImageResource(R.drawable.calendar_2);
        } else if (workoutDate != null && workoutDate.after(new Date())) {
            this.dayOfWeek.setTextColor(ContextCompat.getColor(getContext(), R.color.workout_calendar_progress_empty));
            this.dayOfMonth.setTextColor(ContextCompat.getColor(getContext(), R.color.workout_calendar_progress_empty));
            this.result.setImageResource(R.drawable.calendar_4);
        }
        this.dayOfWeek.setText(dayInfo.getDayOfWeek());
        this.dayOfMonth.setText(dayInfo.getDate().substring(8));
    }

    public void setTypeface(Typeface typeface) {
        this.dayOfWeek.setTypeface(typeface);
        this.dayOfMonth.setTypeface(typeface);
    }
}
