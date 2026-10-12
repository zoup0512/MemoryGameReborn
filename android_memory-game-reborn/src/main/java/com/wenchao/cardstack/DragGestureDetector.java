package com.wenchao.cardstack;

import android.content.Context;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v4.view.MotionEventCompat;
import android.util.Log;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;

public class DragGestureDetector {
    public static String DEBUG_TAG = "DragGestureDetector";
    private GestureDetectorCompat mGestureDetector;
    private DragListener mListener;
    private MotionEvent mOriginalEvent;
    private boolean mStarted = false;

    public interface DragListener {
        boolean onDragContinue(MotionEvent motionEvent, MotionEvent motionEvent2, float f, float f2);

        boolean onDragEnd(MotionEvent motionEvent, MotionEvent motionEvent2);

        boolean onDragStart(MotionEvent motionEvent, MotionEvent motionEvent2, float f, float f2);

        boolean onTapUp();
    }

    class MyGestureListener extends SimpleOnGestureListener {
        MyGestureListener() {
        }

        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (DragGestureDetector.this.mListener != null) {
                if (DragGestureDetector.this.mStarted) {
                    DragGestureDetector.this.mListener.onDragContinue(e1, e2, distanceX, distanceY);
                } else {
                    DragGestureDetector.this.mListener.onDragStart(e1, e2, distanceX, distanceY);
                    DragGestureDetector.this.mStarted = true;
                }
                DragGestureDetector.this.mOriginalEvent = e1;
            }
            return true;
        }

        public boolean onSingleTapUp(MotionEvent e) {
            return DragGestureDetector.this.mListener.onTapUp();
        }
    }

    public DragGestureDetector(Context context, DragListener myDragListener) {
        this.mGestureDetector = new GestureDetectorCompat(context, new MyGestureListener());
        this.mListener = myDragListener;
    }

    public void onTouchEvent(MotionEvent event) {
        this.mGestureDetector.onTouchEvent(event);
        switch (MotionEventCompat.getActionMasked(event)) {
            case 0:
                break;
            case 1:
                Log.d(DEBUG_TAG, "Action was UP");
                if (this.mStarted) {
                    this.mListener.onDragEnd(this.mOriginalEvent, event);
                }
                this.mStarted = false;
                break;
            default:
                return;
        }
        this.mOriginalEvent = event;
    }
}
