package com.cube.memorygames.ui;

import android.graphics.Rect;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ItemDecoration;
import android.support.v7.widget.RecyclerView.State;
import android.view.View;

public class GridSpaceItemDecorator extends ItemDecoration {
    private int space;

    public GridSpaceItemDecorator(int space) {
        this.space = space;
    }

    public int getSpace() {
        return this.space;
    }

    public void setSpace(int space) {
        this.space = space;
    }

    public void getItemOffsets(Rect outRect, View view, RecyclerView parent, State state) {
        outRect.left = 0;
        outRect.right = 0;
        outRect.top = 0;
        outRect.bottom = this.space;
        boolean isTwoColumns = false;
        if (parent.getLayoutManager() instanceof GridLayoutManager) {
            isTwoColumns = true;
        }
        if (!isTwoColumns) {
            return;
        }
        if (parent.getChildLayoutPosition(view) % 2 == 0) {
            outRect.right = this.space / 2;
        } else {
            outRect.left = this.space / 2;
        }
    }
}
