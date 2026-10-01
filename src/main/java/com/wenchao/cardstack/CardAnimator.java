package com.wenchao.cardstack;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout.LayoutParams;
import com.mopub.mobileads.resource.DrawableConstants.CloseButton;
import com.wenchao.animation.RelativeLayoutParamsEvaluator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class CardAnimator {
    private static final String DEBUG_TAG = "CardAnimator";
    private static final int REMOTE_DISTANCE = 1000;
    private LayoutParams baseLayout;
    private int mBackgroundColor;
    public ArrayList<View> mCardCollection;
    private HashMap<View, LayoutParams> mLayoutsMap;
    private LayoutParams[] mRemoteLayouts = new LayoutParams[4];
    private float mRotation;
    private int mStackMargin = 20;

    public CardAnimator(ArrayList<View> viewCollection, int backgroundColor) {
        this.mCardCollection = viewCollection;
        this.mBackgroundColor = backgroundColor;
        setup();
    }

    private void setup() {
        this.mLayoutsMap = new HashMap();
        Iterator it = this.mCardCollection.iterator();
        while (it.hasNext()) {
            View v = (View) it.next();
            LayoutParams params = (LayoutParams) v.getLayoutParams();
            params.addRule(10);
            params.width = -1;
            params.height = -1;
            if (this.mBackgroundColor != -1) {
                v.setBackgroundColor(this.mBackgroundColor);
            }
            v.setLayoutParams(params);
        }
        this.baseLayout = (LayoutParams) ((View) this.mCardCollection.get(0)).getLayoutParams();
        this.baseLayout = CardUtils.cloneParams(this.baseLayout);
        initLayout();
        View v;
        it = this.mCardCollection.iterator();
        while (it.hasNext()) {
            v = (View) it.next();
            this.mLayoutsMap.put(v, CardUtils.cloneParams((LayoutParams) v.getLayoutParams()));
        }
        setupRemotes();
    }

    public void initLayout() {
        int size = this.mCardCollection.size();
        Iterator it = this.mCardCollection.iterator();
        while (it.hasNext()) {
            View v = (View) it.next();
            int index = this.mCardCollection.indexOf(v);
            if (index != 0) {
                index--;
            }
            v.setLayoutParams(CardUtils.cloneParams(this.baseLayout));
            CardUtils.scale(v, (-((size - index) - 1)) * 5);
            CardUtils.move(v, this.mStackMargin * index, 0);
            v.setRotation(0.0f);
        }
    }

    private void setupRemotes() {
        View topView = getTopView();
        this.mRemoteLayouts[0] = CardUtils.getMoveParams(topView, 1000, -1000);
        this.mRemoteLayouts[1] = CardUtils.getMoveParams(topView, 1000, 1000);
        this.mRemoteLayouts[2] = CardUtils.getMoveParams(topView, -1000, -1000);
        this.mRemoteLayouts[3] = CardUtils.getMoveParams(topView, -1000, 1000);
    }

    private View getTopView() {
        return (View) this.mCardCollection.get(this.mCardCollection.size() - 1);
    }

    private void moveToBack(View child) {
        ViewGroup parent = (ViewGroup) child.getParent();
        if (parent != null) {
            parent.removeView(child);
            parent.addView(child, 0);
        }
    }

    private void reorder() {
        View temp = getTopView();
        moveToBack(temp);
        for (int i = this.mCardCollection.size() - 1; i > 0; i--) {
            this.mCardCollection.set(i, (View) this.mCardCollection.get(i - 1));
        }
        this.mCardCollection.set(0, temp);
        temp = getTopView();
    }

    public void discard(int direction, AnimatorListener al) {
        AnimatorSet as = new AnimatorSet();
        ArrayList<Animator> aCollection = new ArrayList();
        final View topView = getTopView();
        LayoutParams layout = CardUtils.cloneParams((LayoutParams) topView.getLayoutParams());
        ValueAnimator discardAnim = ValueAnimator.ofObject(new RelativeLayoutParamsEvaluator(), new Object[]{layout, this.mRemoteLayouts[direction]});
        discardAnim.addUpdateListener(new AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator value) {
                topView.setLayoutParams((LayoutParams) value.getAnimatedValue());
            }
        });
        discardAnim.setDuration(250);
        aCollection.add(discardAnim);
        for (int i = 0; i < this.mCardCollection.size(); i++) {
            final View v = (View) this.mCardCollection.get(i);
            if (v != topView) {
                View nv = (View) this.mCardCollection.get(i + 1);
                LayoutParams endLayout = CardUtils.cloneParams((LayoutParams) v.getLayoutParams());
                ValueAnimator layoutAnim = ValueAnimator.ofObject(new RelativeLayoutParamsEvaluator(), new Object[]{endLayout, this.mLayoutsMap.get(nv)});
                layoutAnim.setDuration(250);
                layoutAnim.addUpdateListener(new AnimatorUpdateListener() {
                    public void onAnimationUpdate(ValueAnimator value) {
                        v.setLayoutParams((LayoutParams) value.getAnimatedValue());
                    }
                });
                aCollection.add(layoutAnim);
            }
        }
        final AnimatorListener animatorListener = al;
        as.addListener(new AnimatorListenerAdapter() {
            public void onAnimationEnd(Animator animation) {
                CardAnimator.this.reorder();
                if (animatorListener != null) {
                    animatorListener.onAnimationEnd(animation);
                }
                CardAnimator.this.mLayoutsMap = new HashMap();
                Iterator it = CardAnimator.this.mCardCollection.iterator();
                while (it.hasNext()) {
                    View v = (View) it.next();
                    CardAnimator.this.mLayoutsMap.put(v, CardUtils.cloneParams((LayoutParams) v.getLayoutParams()));
                }
            }
        });
        as.playTogether(aCollection);
        as.start();
    }

    public void reverse(MotionEvent e1, MotionEvent e2) {
        final View topView = getTopView();
        ValueAnimator rotationAnim = ValueAnimator.ofFloat(new float[]{this.mRotation, 0.0f});
        rotationAnim.setDuration(250);
        rotationAnim.addUpdateListener(new AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator v) {
                topView.setRotation(((Float) v.getAnimatedValue()).floatValue());
            }
        });
        rotationAnim.start();
        Iterator it = this.mCardCollection.iterator();
        while (it.hasNext()) {
            final View v = (View) it.next();
            LayoutParams endLayout = CardUtils.cloneParams((LayoutParams) v.getLayoutParams());
            ValueAnimator layoutAnim = ValueAnimator.ofObject(new RelativeLayoutParamsEvaluator(), new Object[]{endLayout, this.mLayoutsMap.get(v)});
            layoutAnim.setDuration(250);
            layoutAnim.addUpdateListener(new AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator value) {
                    v.setLayoutParams((LayoutParams) value.getAnimatedValue());
                }
            });
            layoutAnim.start();
        }
    }

    public void drag(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        View topView = getTopView();
        LayoutParams layoutParams = (LayoutParams) topView.getLayoutParams();
        LayoutParams topViewLayouts = (LayoutParams) this.mLayoutsMap.get(topView);
        int x_diff = (int) (e2.getRawX() - e1.getRawX());
        int y_diff = (int) (e2.getRawY() - e1.getRawY());
        layoutParams.leftMargin = topViewLayouts.leftMargin + x_diff;
        layoutParams.rightMargin = topViewLayouts.rightMargin - x_diff;
        layoutParams.topMargin = topViewLayouts.topMargin + y_diff;
        layoutParams.bottomMargin = topViewLayouts.bottomMargin - y_diff;
        this.mRotation = ((float) x_diff) / CloseButton.TEXT_SIZE_SP;
        topView.setRotation(this.mRotation);
        topView.setLayoutParams(layoutParams);
        Iterator it = this.mCardCollection.iterator();
        while (it.hasNext()) {
            View v = (View) it.next();
            int index = this.mCardCollection.indexOf(v);
            if (!(v == getTopView() || index == 0)) {
                CardUtils.moveFrom(v, CardUtils.scaleFrom(v, (LayoutParams) this.mLayoutsMap.get(v), (int) (((double) Math.abs(x_diff)) * 0.05d)), 0, (int) (((double) Math.abs(x_diff)) * 0.1d));
            }
        }
    }

    public void setStackMargin(int margin) {
        this.mStackMargin = margin;
        initLayout();
    }
}
