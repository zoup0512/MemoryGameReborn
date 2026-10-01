package com.cube.memorygames.ui;

import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.OnScrollListener;
import android.view.View;

public class CenterLockListener extends OnScrollListener {
    private boolean mAutoSet = true;
    private int mCenterPivot;

    public CenterLockListener(int center) {
        this.mCenterPivot = center;
    }

    public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
        super.onScrollStateChanged(recyclerView, newState);
        LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (this.mCenterPivot == 0) {
            this.mCenterPivot = lm.getOrientation() == 0 ? recyclerView.getLeft() + recyclerView.getRight() : recyclerView.getTop() + recyclerView.getBottom();
        }
        if (!this.mAutoSet && newState == 0) {
            View view = findCenterView(lm);
            int scrollNeeded = (lm.getOrientation() == 0 ? (view.getLeft() + view.getRight()) / 2 : (view.getTop() + view.getBottom()) / 2) - this.mCenterPivot;
            if (lm.getOrientation() == 0) {
                recyclerView.smoothScrollBy(scrollNeeded, 0);
            } else {
                recyclerView.smoothScrollBy(0, scrollNeeded);
            }
            this.mAutoSet = true;
        }
        if (newState == 1 || newState == 2) {
            this.mAutoSet = false;
        }
    }

    public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
        super.onScrolled(recyclerView, dx, dy);
    }

    private View findCenterView(LinearLayoutManager lm) {
        int minDistance = 0;
        View returnView = null;
        boolean notFound = true;
        int i = lm.findFirstVisibleItemPosition();
        while (i <= lm.findLastVisibleItemPosition() && notFound) {
            View view = lm.findViewByPosition(i);
            int leastDifference = Math.abs(this.mCenterPivot - (lm.getOrientation() == 0 ? (view.getLeft() + view.getRight()) / 2 : (view.getTop() + view.getBottom()) / 2));
            if (leastDifference <= minDistance || i == lm.findFirstVisibleItemPosition()) {
                minDistance = leastDifference;
                returnView = view;
            } else {
                notFound = false;
            }
            i++;
        }
        return returnView;
    }
}
