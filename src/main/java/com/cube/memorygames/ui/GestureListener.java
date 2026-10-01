package com.cube.memorygames.ui;

import android.content.Context;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import com.cube.memorygames.ui.Game15Grid.Side;
import com.memory.brain.training.games.R;

public abstract class GestureListener extends SimpleOnGestureListener {
    private int swipeThreshold;
    private int swipeVelocityThreshold;

    public abstract void onSwipe(Side side, MotionEvent motionEvent);

    public GestureListener(Context context) {
        this.swipeThreshold = context.getResources().getDimensionPixelSize(R.dimen.swipeThreshold);
        this.swipeVelocityThreshold = context.getResources().getDimensionPixelSize(R.dimen.swipeVelocityThreshold);
    }

    public boolean onDown(MotionEvent e) {
        return true;
    }

    public boolean onSingleTapUp(MotionEvent e) {
        onClick();
        return super.onSingleTapUp(e);
    }

    public boolean onDoubleTap(MotionEvent e) {
        onDoubleClick();
        return super.onDoubleTap(e);
    }

    public void onLongPress(MotionEvent e) {
        onLongClick();
        super.onLongPress(e);
    }

    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        try {
            float diffY = e2.getY() - e1.getY();
            float diffX = e2.getX() - e1.getX();
            if (Math.abs(diffX) > Math.abs(diffY)) {
                if (Math.abs(diffX) > ((float) this.swipeThreshold) && Math.abs(velocityX) > ((float) this.swipeVelocityThreshold)) {
                    if (diffX > 0.0f) {
                        onSwipe(Side.RIGHT, e2);
                    } else {
                        onSwipe(Side.LEFT, e2);
                    }
                }
            } else if (Math.abs(diffY) > ((float) this.swipeThreshold) && Math.abs(velocityY) > ((float) this.swipeVelocityThreshold)) {
                if (diffY > 0.0f) {
                    onSwipe(Side.DOWN, e2);
                } else {
                    onSwipe(Side.UP, e2);
                }
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        return false;
    }

    public void onClick() {
    }

    public void onDoubleClick() {
    }

    public void onLongClick() {
    }
}
