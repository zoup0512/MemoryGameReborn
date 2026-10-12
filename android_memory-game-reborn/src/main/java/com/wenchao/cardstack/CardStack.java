package com.wenchao.cardstack;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import com.wenchao.cardstack.DragGestureDetector.DragListener;
import java.util.ArrayList;

public class CardStack extends RelativeLayout {
    private boolean canSwipe = true;
    private ArrayAdapter<?> mAdapter;
    private CardAnimator mCardAnimator;
    private int mColor = -1;
    private int mContentResource = 0;
    private CardEventListener mEventListener = new DefaultStackEventListener(300);
    private int mIndex = 0;
    private int mNumVisible = 4;
    private DataSetObserver mOb = new DataSetObserver() {
        public void onChanged() {
            CardStack.this.reset(false);
        }
    };
    private OnTouchListener mOnTouchListener;
    ArrayList<View> viewCollection = new ArrayList();

    public interface CardEventListener {
        void discarded(int i, int i2);

        boolean swipeContinue(int i, float f, float f2);

        boolean swipeEnd(int i, float f);

        boolean swipeStart(int i, float f);

        void topCardTapped();
    }

    public void discardTop(final int direction) {
        this.mCardAnimator.discard(direction, new AnimatorListenerAdapter() {
            public void onAnimationEnd(Animator arg0) {
                CardStack.this.mCardAnimator.initLayout();
                CardStack.this.mIndex = CardStack.this.mIndex + 1;
                CardStack.this.loadLast();
                ((View) CardStack.this.viewCollection.get(0)).setOnTouchListener(null);
                ((View) CardStack.this.viewCollection.get(CardStack.this.viewCollection.size() - 1)).setOnTouchListener(CardStack.this.mOnTouchListener);
                CardStack.this.mEventListener.discarded(CardStack.this.mIndex - 1, direction);
            }
        });
    }

    public int getCurrIndex() {
        return this.mIndex;
    }

    public CardStack(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (attrs != null) {
            TypedArray array = context.obtainStyledAttributes(attrs, R.styleable.CardStack);
            this.mColor = array.getColor(R.styleable.CardStack_backgroundColor, this.mColor);
            array.recycle();
        }
        for (int i = 0; i < this.mNumVisible; i++) {
            addContainerViews();
        }
        setupAnimation();
    }

    private void addContainerViews() {
        FrameLayout v = new FrameLayout(getContext());
        this.viewCollection.add(v);
        addView(v);
    }

    public void setStackMargin(int margin) {
        this.mCardAnimator.setStackMargin(margin);
        this.mCardAnimator.initLayout();
    }

    public void setContentResource(int res) {
        this.mContentResource = res;
    }

    public void setCanSwipe(boolean can) {
        this.canSwipe = can;
    }

    public void reset(boolean resetIndex) {
        if (resetIndex) {
            this.mIndex = 0;
        }
        removeAllViews();
        this.viewCollection.clear();
        for (int i = 0; i < this.mNumVisible; i++) {
            addContainerViews();
        }
        setupAnimation();
        loadData();
    }

    public void setVisibleCardNum(int visiableNum) {
        this.mNumVisible = visiableNum;
        reset(false);
    }

    public void setThreshold(int t) {
        this.mEventListener = new DefaultStackEventListener(t);
    }

    public void setListener(CardEventListener cel) {
        this.mEventListener = cel;
    }

    private void setupAnimation() {
        View cardView = (View) this.viewCollection.get(this.viewCollection.size() - 1);
        this.mCardAnimator = new CardAnimator(this.viewCollection, this.mColor);
        this.mCardAnimator.initLayout();
        final DragGestureDetector dd = new DragGestureDetector(getContext(), new DragListener() {
            public boolean onDragStart(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (CardStack.this.canSwipe) {
                    CardStack.this.mCardAnimator.drag(e1, e2, distanceX, distanceY);
                }
                float x1 = e1.getRawX();
                float y1 = e1.getRawY();
                float x2 = e2.getRawX();
                float y2 = e2.getRawY();
                CardStack.this.mEventListener.swipeStart(CardUtils.direction(x1, y1, x2, y2), CardUtils.distance(x1, y1, x2, y2));
                return true;
            }

            public boolean onDragContinue(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                float x1 = e1.getRawX();
                float y1 = e1.getRawY();
                float x2 = e2.getRawX();
                float y2 = e2.getRawY();
                int direction = CardUtils.direction(x1, y1, x2, y2);
                if (CardStack.this.canSwipe) {
                    CardStack.this.mCardAnimator.drag(e1, e2, distanceX, distanceY);
                }
                CardStack.this.mEventListener.swipeContinue(direction, Math.abs(x2 - x1), Math.abs(y2 - y1));
                return true;
            }

            public boolean onDragEnd(MotionEvent e1, MotionEvent e2) {
                float x1 = e1.getRawX();
                float y1 = e1.getRawY();
                float x2 = e2.getRawX();
                float y2 = e2.getRawY();
                float distance = CardUtils.distance(x1, y1, x2, y2);
                final int direction = CardUtils.direction(x1, y1, x2, y2);
                if (CardStack.this.mEventListener.swipeEnd(direction, distance)) {
                    if (CardStack.this.canSwipe) {
                        CardStack.this.mCardAnimator.discard(direction, new AnimatorListenerAdapter() {
                            public void onAnimationEnd(Animator arg0) {
                                CardStack.this.mCardAnimator.initLayout();
                                CardStack.this.mIndex = CardStack.this.mIndex + 1;
                                CardStack.this.mEventListener.discarded(CardStack.this.mIndex, direction);
                                CardStack.this.loadLast();
                                ((View) CardStack.this.viewCollection.get(0)).setOnTouchListener(null);
                                ((View) CardStack.this.viewCollection.get(CardStack.this.viewCollection.size() - 1)).setOnTouchListener(CardStack.this.mOnTouchListener);
                            }
                        });
                    }
                } else if (CardStack.this.canSwipe) {
                    CardStack.this.mCardAnimator.reverse(e1, e2);
                }
                return true;
            }

            public boolean onTapUp() {
                CardStack.this.mEventListener.topCardTapped();
                return true;
            }
        });
        this.mOnTouchListener = new OnTouchListener() {
            private static final String DEBUG_TAG = "MotionEvents";

            public boolean onTouch(View arg0, MotionEvent event) {
                dd.onTouchEvent(event);
                return true;
            }
        };
        cardView.setOnTouchListener(this.mOnTouchListener);
    }

    public CardStack(Context context) {
        super(context);
    }

    public void setAdapter(ArrayAdapter<?> adapter) {
        if (this.mAdapter != null) {
            this.mAdapter.unregisterDataSetObserver(this.mOb);
        }
        this.mAdapter = adapter;
        adapter.registerDataSetObserver(this.mOb);
        loadData();
    }

    public ArrayAdapter getAdapter() {
        return this.mAdapter;
    }

    public View getTopView() {
        return ((ViewGroup) this.viewCollection.get(this.viewCollection.size() - 1)).getChildAt(0);
    }

    private void loadData() {
        for (int i = this.mNumVisible - 1; i >= 0; i--) {
            ViewGroup parent = (ViewGroup) this.viewCollection.get(i);
            int index = ((this.mIndex + this.mNumVisible) - 1) - i;
            if (index > this.mAdapter.getCount() - 1) {
                parent.setVisibility(8);
            } else {
                parent.addView(this.mAdapter.getView(index, getContentView(), this));
                parent.setVisibility(0);
            }
        }
    }

    private View getContentView() {
        if (this.mContentResource != 0) {
            return LayoutInflater.from(getContext()).inflate(this.mContentResource, null);
        }
        return null;
    }

    private void loadLast() {
        ViewGroup parent = (ViewGroup) this.viewCollection.get(0);
        int lastIndex = (this.mNumVisible - 1) + this.mIndex;
        if (lastIndex > this.mAdapter.getCount() - 1) {
            parent.setVisibility(8);
            return;
        }
        View child = this.mAdapter.getView(lastIndex, getContentView(), parent);
        parent.removeAllViews();
        parent.addView(child);
    }

    public int getStackSize() {
        return this.mNumVisible;
    }
}
