package com.cube.memorygames.ui;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.graphics.RectF;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;
import com.memory.brain.training.games.R;
import com.mopub.mobileads.resource.DrawableConstants.RadialCountdown;

public class TimerView extends View {
    private Float angle;
    private Paint anglePaint;
    private Paint circlePaint;
    private Integer digit;
    private Paint digitPaint;
    private boolean isDigit = false;
    private Paint strokePaint;

    public TimerView(Context context) {
        super(context);
        init();
    }

    public TimerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TimerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        this.circlePaint = new Paint();
        this.anglePaint = new Paint();
        this.strokePaint = new Paint();
        this.digitPaint = new Paint();
        this.circlePaint.setColor(ContextCompat.getColor(getContext(), R.color.background));
        this.anglePaint.setColor(ContextCompat.getColor(getContext(), R.color.grid_empty));
        this.strokePaint.setColor(ContextCompat.getColor(getContext(), R.color.grid_stroke));
        this.digitPaint.setColor(ContextCompat.getColor(getContext(), R.color.grid_correct));
        this.circlePaint.setStyle(Style.FILL_AND_STROKE);
        this.anglePaint.setStyle(Style.FILL_AND_STROKE);
        this.strokePaint.setStyle(Style.STROKE);
        this.strokePaint.setStrokeWidth(getResources().getDimension(R.dimen.cell_stroke_width));
        this.digitPaint.setStyle(Style.FILL);
        this.digitPaint.setTextSize(40.0f);
        this.circlePaint.setFlags(1);
        this.anglePaint.setFlags(1);
        this.strokePaint.setFlags(1);
        this.digitPaint.setFlags(1);
    }

    public void showTimer(int duration, boolean isDigit) {
        this.isDigit = isDigit;
        this.digit = Integer.valueOf(duration / 1000);
        ObjectAnimator objectAnimator = ObjectAnimator.ofFloat(this, "angle", new float[]{0.0f, 360.0f}).setDuration((long) duration);
        objectAnimator.setInterpolator(new LinearInterpolator());
        objectAnimator.addListener(new AnimatorListener() {
            public void onAnimationStart(Animator animation) {
            }

            public void onAnimationEnd(Animator animation) {
                TimerView.this.angle = null;
            }

            public void onAnimationCancel(Animator animation) {
                TimerView.this.angle = null;
            }

            public void onAnimationRepeat(Animator animation) {
            }
        });
        objectAnimator.start();
        ObjectAnimator digitAnimator = ObjectAnimator.ofInt(this, "digit", new int[]{this.digit.intValue(), 0}).setDuration((long) duration);
        digitAnimator.setInterpolator(new LinearInterpolator());
        digitAnimator.addListener(new AnimatorListener() {
            public void onAnimationStart(Animator animation) {
            }

            public void onAnimationEnd(Animator animation) {
                TimerView.this.digit = null;
            }

            public void onAnimationCancel(Animator animation) {
                TimerView.this.digit = null;
            }

            public void onAnimationRepeat(Animator animation) {
            }
        });
        digitAnimator.start();
    }

    public void setDigit(int digit) {
        this.digit = Integer.valueOf(digit);
        invalidate();
    }

    private void setAngle(float angle) {
        this.angle = Float.valueOf(angle);
        invalidate();
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width > 0 && height > 0 && this.angle != null) {
            float x = ((float) width) / 2.0f;
            float y = ((float) height) / 2.0f;
            float radius = Math.min(x, y);
            canvas.drawCircle(x, y, radius, this.circlePaint);
            canvas.drawArc(new RectF(0.0f, 0.0f, (float) width, (float) height), RadialCountdown.START_ANGLE, this.angle.floatValue(), true, this.anglePaint);
            canvas.drawCircle(x, y, radius, this.strokePaint);
            if (this.isDigit) {
                String text = String.valueOf(this.digit);
                Rect bounds = new Rect();
                this.digitPaint.getTextBounds(text, 0, text.length(), bounds);
                float offsetX = (float) (bounds.width() / 2);
                float offsetY = (float) (bounds.height() / 2);
                canvas.drawText(String.valueOf(this.digit), x - offsetX, y + offsetY, this.digitPaint);
            }
        }
    }
}
