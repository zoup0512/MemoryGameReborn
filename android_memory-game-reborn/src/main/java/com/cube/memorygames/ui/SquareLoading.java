package com.cube.memorygames.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import com.memory.brain.training.games.R;
import com.mopub.mobileads.resource.DrawableConstants.RadialCountdown;
import java.util.ArrayList;
import java.util.List;

public class SquareLoading extends ViewGroup {
    private static final int DEFAULT_DIVIDER_SIZE = 8;
    private static final int DEFAULT_FIRST_INDEX = 8;
    private static final int DEFAULT_LAST_INDEX = 3;
    private static final int DEFAULT_SQUARE_COLOR = -1;
    private static final int DEFAULT_SQUARE_CORNER = 8;
    private static final int DEFAULT_SQUARE_SIZE = 36;
    private static final int DEFUALT_X_COUNT = 4;
    private static final int DEFUALT_Y_COUNT = 3;
    private int mDividerSize = 8;
    private int mFirstIndex = 8;
    private int mLastIndex = 3;
    private int mPaddingLeft = 0;
    private int mPaddingTop = 0;
    private int mSquareColor = -1;
    private int mSquareCorner = 8;
    private int mSquareSize = 36;
    private int mXCount = 4;
    private int mYCount = 3;
    private List<RotateAnimation> reverseAnims = new ArrayList();
    private List<RotateAnimation> startAnims = new ArrayList();

    public SquareLoading(Context context) {
        super(context);
        init(context, null);
    }

    public SquareLoading(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public SquareLoading(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    protected void onLayout(boolean changed, int l, int t, int r, int b) {
    }

    private void init(Context context, AttributeSet attrs) {
        removeViewsIfNeeded();
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.SquareLoading);
            this.mSquareColor = a.getColor(R.styleable.SquareLoading_squareColor, -1);
            this.mSquareSize = a.getDimensionPixelSize(R.styleable.SquareLoading_squareSize, 36);
            this.mSquareCorner = a.getDimensionPixelSize(R.styleable.SquareLoading_squareCorner, 8);
            this.mDividerSize = a.getDimensionPixelSize(R.styleable.SquareLoading_dividerSize, 8);
            int xCount = a.getInteger(R.styleable.SquareLoading_xCount, 4);
            int yCount = a.getInteger(R.styleable.SquareLoading_yCount, 3);
            if (xCount >= 2 && xCount <= 6) {
                this.mXCount = xCount;
            }
            if (yCount >= 2 && yCount <= 6) {
                this.mYCount = yCount;
            }
            a.recycle();
            this.mFirstIndex = this.mXCount * (this.mYCount - 1);
            this.mLastIndex = this.mXCount - 1;
        }
        initSquare(context);
        initAnim();
    }

    private void removeViewsIfNeeded() {
        if (getChildCount() > 0) {
            removeAllViews();
        }
    }

    private void initSquare(Context context) {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(this.mSquareColor);
        gradientDrawable.setSize(this.mSquareSize, this.mSquareSize);
        gradientDrawable.setCornerRadius((float) this.mSquareCorner);
        for (int i = 0; i < this.mXCount * this.mYCount; i++) {
            ImageView image = new ImageView(context);
            image.setImageDrawable(gradientDrawable);
            addView(image);
        }
    }

    private void initAnim() {
        for (int i = 0; i < getChildCount(); i++) {
            RotateAnimation startAnim = new RotateAnimation(0.0f, RadialCountdown.START_ANGLE, 0.0f, (float) this.mSquareSize);
            startAnim.setDuration(300);
            startAnim.setFillAfter(true);
            startAnim.setInterpolator(new DecelerateInterpolator());
            final int finalI = i;
            startAnim.setAnimationListener(new AnimationListener() {
                public void onAnimationStart(Animation animation) {
                    if (finalI != SquareLoading.this.mLastIndex) {
                        int index = SquareLoading.this.getNextAnimChild(true, finalI);
                        SquareLoading.this.startRotateAnim(index, index > SquareLoading.this.mFirstIndex ? 100 : 50);
                    }
                }

                public void onAnimationEnd(Animation animation) {
                    if (finalI == SquareLoading.this.mLastIndex) {
                        SquareLoading.this.startReverseAnim(SquareLoading.this.mLastIndex, 300);
                    }
                }

                public void onAnimationRepeat(Animation animation) {
                }
            });
            this.startAnims.add(startAnim);
            RotateAnimation reverseAnim = new RotateAnimation(RadialCountdown.START_ANGLE, 0.0f, 0.0f, (float) this.mSquareSize);
            reverseAnim.setDuration(300);
            reverseAnim.setFillAfter(true);
            reverseAnim.setInterpolator(new DecelerateInterpolator());
            final int finalI1 = i;
            reverseAnim.setAnimationListener(new AnimationListener() {
                public void onAnimationStart(Animation animation) {
                    if (finalI1 != SquareLoading.this.mFirstIndex) {
                        int index = SquareLoading.this.getNextAnimChild(false, finalI1);
                        SquareLoading.this.startReverseAnim(index, index < SquareLoading.this.mXCount ? 100 : 50);
                    }
                }

                public void onAnimationEnd(Animation animation) {
                    if (finalI1 == SquareLoading.this.mFirstIndex) {
                        SquareLoading.this.startRotateAnim(SquareLoading.this.mFirstIndex, 300);
                    }
                }

                public void onAnimationRepeat(Animation animation) {
                }
            });
            this.reverseAnims.add(reverseAnim);
        }
    }

    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (getChildCount() > 0) {
            startRotateAnim(this.mFirstIndex);
        }
    }

    public void startAnim() {
        if (getChildCount() > 0) {
            if (this.startAnims.isEmpty()) {
                initAnim();
            }
            startRotateAnim(this.mFirstIndex);
        }
    }

    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int sizeWidth = MeasureSpec.getSize(widthMeasureSpec);
        int sizeHeight = MeasureSpec.getSize(heightMeasureSpec);
        measureChildren(widthMeasureSpec, heightMeasureSpec);
        int minWidth = (this.mSquareSize * (this.mXCount + 1)) + ((this.mXCount - 1) * this.mDividerSize);
        int minHeight = (this.mSquareSize * (this.mYCount + 1)) + ((this.mYCount - 1) * this.mDividerSize);
        if (widthMode == Integer.MIN_VALUE || (widthMode == 1073741824 && sizeWidth < minWidth)) {
            sizeWidth = minWidth;
        }
        if (heightMode == Integer.MIN_VALUE || (heightMode == 1073741824 && sizeHeight < minHeight)) {
            sizeHeight = minHeight;
        }
        if (sizeHeight > minHeight) {
            this.mPaddingTop = (sizeHeight - minHeight) / 2;
        }
        if (sizeWidth > minWidth) {
            this.mPaddingLeft = (sizeWidth - minWidth) / 2;
        }
        childLayout();
        setMeasuredDimension(sizeWidth, sizeHeight);
    }

    private void childLayout() {
        for (int i = 0; i < getChildCount(); i++) {
            int l = ((((i % this.mXCount) + 1) * this.mSquareSize) + ((i % this.mXCount) * this.mDividerSize)) + this.mPaddingLeft;
            int t = ((((i / this.mXCount) + 1) * this.mSquareSize) + ((i / this.mXCount) * this.mDividerSize)) + this.mPaddingTop;
            getChildAt(i).layout(l, t, l + this.mSquareSize, t + this.mSquareSize);
        }
    }

    private void startRotateAnim(int index) {
        if (this.startAnims != null && this.startAnims.size() > index) {
            getChildAt(index).startAnimation((Animation) this.startAnims.get(index));
        }
    }

    private void startReverseAnim(int index) {
        if (this.reverseAnims != null && this.reverseAnims.size() > index) {
            getChildAt(index).startAnimation((Animation) this.reverseAnims.get(index));
        }
    }

    private void startRotateAnim(final int index, int delayMillis) {
        postDelayed(new Runnable() {
            public void run() {
                SquareLoading.this.startRotateAnim(index);
            }
        }, (long) delayMillis);
    }

    private void startReverseAnim(final int index, int delayMillis) {
        postDelayed(new Runnable() {
            public void run() {
                SquareLoading.this.startReverseAnim(index);
            }
        }, (long) delayMillis);
    }

    private int getNextAnimChild(boolean isStart, int i) {
        if (isStart) {
            if (i < this.mXCount) {
                return i + (this.mFirstIndex + 1);
            }
            return i - this.mXCount;
        } else if (i > this.mFirstIndex) {
            return i - (this.mFirstIndex + 1);
        } else {
            return i + this.mXCount;
        }
    }

    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelAnims(this.startAnims);
        cancelAnims(this.reverseAnims);
    }

    private void cancelAnims(List<RotateAnimation> anims) {
        if (anims != null) {
            for (RotateAnimation anim : anims) {
                anim.cancel();
            }
            anims.clear();
        }
    }
}
