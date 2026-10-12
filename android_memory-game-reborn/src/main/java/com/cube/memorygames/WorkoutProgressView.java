package com.cube.memorygames;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.View;
import com.memory.brain.training.games.R;

public class WorkoutProgressView extends View {
    private static final int DEFAULT_MAX = 50;
    private Paint emptyPaint;
    private int max = 50;
    private int progress = 0;
    private Paint progressPaint;

    public WorkoutProgressView(Context context) {
        super(context);
        init();
    }

    public WorkoutProgressView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WorkoutProgressView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        this.emptyPaint = new Paint();
        this.emptyPaint.setColor(ContextCompat.getColor(getContext(), R.color.workout_progress_empty));
        this.emptyPaint.setAntiAlias(true);
        this.progressPaint = new Paint();
        this.progressPaint.setColor(ContextCompat.getColor(getContext(), R.color.workout_progress_filled));
        this.progressPaint.setAntiAlias(true);
    }

    public void setMax(int max) {
        this.max = max;
        if (this.progress > max) {
            this.progress = max;
        }
        invalidate();
    }

    public void setProgress(int progress) {
        this.progress = progress;
        if (this.max < progress) {
            this.max = progress;
        }
        invalidate();
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width > 0 && height > 0) {
            float paintMax = (float) ((this.max * 2) - 1);
            for (int i = this.progress * 2; ((float) i) < paintMax; i += 2) {
                canvas.drawRect((((float) width) / paintMax) * ((float) i), 0.0f, (((float) width) / paintMax) * ((float) (i + 1)), (float) height, this.emptyPaint);
            }
            if (this.progress > 0) {
                canvas.drawRect(0.0f, 0.0f, (((float) width) / paintMax) * ((float) ((this.progress * 2) - 1)), (float) height, this.progressPaint);
            }
        }
    }
}
