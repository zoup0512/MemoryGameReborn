package com.cube.memorygames.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.GridLayout;
import android.widget.GridLayout.LayoutParams;
import android.widget.ToggleButton;
import com.cube.memorygames.logic.GameRandom;
import com.github.florent37.viewanimator.AnimationBuilder;
import com.github.florent37.viewanimator.AnimationListener.Stop;
import com.github.florent37.viewanimator.ViewAnimator;
import com.memory.brain.training.games.R;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

public class CatchRectangularGrid extends GridLayout implements GameGrid {
    private static final float DIVIDER_COEFFICIENT = 0.1f;
    private View[][] buttons;
    private int colCount;
    private int currentSuccessCellsClicked = 0;
    private GridEventsListener gridEventsListener;
    private int height;
    private int rowCount;
    private SuccessCellClickListener successCellClickListener = new SuccessCellClickListener();
    private int successCells;
    private int visibleCells;
    private int width;
    private WrongCellListener wrongCellListener = new WrongCellListener();

    private class SuccessCellClickListener implements OnCheckedChangeListener {
        private SuccessCellClickListener() {
        }

        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            buttonView.setEnabled(false);
            CatchRectangularGrid.this.currentSuccessCellsClicked = CatchRectangularGrid.this.currentSuccessCellsClicked + 1;
            if (CatchRectangularGrid.this.currentSuccessCellsClicked >= CatchRectangularGrid.this.successCells) {
                buttonView.setBackgroundResource(R.drawable.drawable_state_cell_success_last);
            }
            CatchRectangularGrid.this.notifySuccessCellClicked(CatchRectangularGrid.this.indexOfChild(buttonView));
        }
    }

    private class WrongCellListener implements OnCheckedChangeListener {
        private WrongCellListener() {
        }

        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            for (int i = 0; i < CatchRectangularGrid.this.getChildCount(); i++) {
                ToggleButton button = (ToggleButton) CatchRectangularGrid.this.getChildAt(i);
                if (!(button.getTag() == null || !button.getTag().equals("Success") || button.isChecked())) {
                    button.setBackgroundResource(R.drawable.drawable_state_cell_unclicked);
                }
            }
            buttonView.setEnabled(false);
            CatchRectangularGrid.this.notifyFailCellClicked();
        }
    }

    public CatchRectangularGrid(Context context, int gridSize, int visibleCells, int successCells, int width, int height) {
        super(context);
        this.colCount = gridSize;
        this.rowCount = gridSize;
        this.successCells = successCells;
        this.visibleCells = visibleCells;
        this.width = width;
        this.height = height;
    }

    public void showChallengeCells() {
        for (int i = 0; i < getChildCount(); i++) {
            ToggleButton button = (ToggleButton) getChildAt(i);
            button.setOnCheckedChangeListener(null);
            if (button.getTag() != null && button.getTag().equals("Success")) {
                button.setChecked(true);
                button.setVisibility(0);
            }
            button.setOnCheckedChangeListener(this.successCellClickListener);
        }
    }

    public void hideAllCells() {
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).setVisibility(4);
        }
    }

    public void setDrawableIdsToUse(List<Integer> list) {
        throw new UnsupportedOperationException();
    }

    public void setDrawablesToUse(List<Drawable> list) {
        throw new UnsupportedOperationException();
    }

    private void notifySuccessCellClicked(int lastAddedCellClicked) {
        if (this.gridEventsListener != null) {
            this.gridEventsListener.onSuccessCellClicked(lastAddedCellClicked);
        }
    }

    private void notifyFailCellClicked() {
        if (this.gridEventsListener != null) {
            this.gridEventsListener.onFailCellClicked();
        }
    }

    public void setGridEventsListener(GridEventsListener gridEventsListener) {
        this.gridEventsListener = gridEventsListener;
    }

    public void disableAllCells() {
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).setEnabled(false);
        }
    }

    public void hideChallengeCells() {
        for (int i = 0; i < getChildCount(); i++) {
            ToggleButton button = (ToggleButton) getChildAt(i);
            button.setOnCheckedChangeListener(null);
            button.setChecked(false);
        }
    }

    public void enableAllCells() {
        for (int i = 0; i < getChildCount(); i++) {
            ToggleButton button = (ToggleButton) getChildAt(i);
            button.setOnCheckedChangeListener(null);
            button.setEnabled(true);
            if (button.getTag() == null || !button.getTag().equals("Success")) {
                button.setOnCheckedChangeListener(this.wrongCellListener);
            } else {
                button.setOnCheckedChangeListener(this.successCellClickListener);
            }
        }
    }

    public void enableSuccessCells() {
        for (int i = 0; i < getChildCount(); i++) {
            ToggleButton button = (ToggleButton) getChildAt(i);
            button.setOnCheckedChangeListener(null);
            if (button.getTag() != null && button.getTag().equals("Success")) {
                button.setOnCheckedChangeListener(this.successCellClickListener);
                button.setEnabled(true);
            }
        }
    }

    public void shuffle(int duration) {
        int i;
        List<View> prevCellsList = new ArrayList();
        List<View> nextCellsList = new ArrayList();
        for (i = 0; i < getChildCount(); i++) {
            prevCellsList.add(getChildAt(i));
            nextCellsList.add(getChildAt(i));
        }
        GameRandom.shuffle(nextCellsList);
        AnimationBuilder builder = ViewAnimator.animate((View) prevCellsList.get(0)).translationX(((View) nextCellsList.get(0)).getX() - ((View) prevCellsList.get(0)).getX()).translationY(((View) nextCellsList.get(0)).getY() - ((View) prevCellsList.get(0)).getY()).duration((long) duration);
        for (i = 1; i < prevCellsList.size(); i++) {
            builder.andAnimate((View) prevCellsList.get(i)).translationX(((View) nextCellsList.get(i)).getX() - ((View) prevCellsList.get(i)).getX()).translationY(((View) nextCellsList.get(i)).getY() - ((View) prevCellsList.get(i)).getY()).duration((long) duration);
        }
        builder.start();
    }

    public void buildGrid() {
        setColumnCount(this.colCount);
        setRowCount(this.rowCount);
        float cellSizeFloat = ((float) this.width) / (((float) this.colCount) + (DIVIDER_COEFFICIENT * ((float) (this.colCount + 1))));
        int cellMargin = (int) (DIVIDER_COEFFICIENT * cellSizeFloat);
        int cellSize = (int) cellSizeFloat;
        int mainMargins = (this.width - ((this.colCount * cellSize) + ((this.colCount * 2) * (cellMargin / 2)))) / 2;
        setPadding(mainMargins, mainMargins, mainMargins, mainMargins);
        List<ToggleButton> cellsList = new ArrayList();
        this.buttons = (View[][]) Array.newInstance(View.class, new int[]{this.colCount, this.rowCount});
        for (int i = 0; i < this.colCount; i++) {
            for (int j = 0; j < this.rowCount; j++) {
                LayoutParams cellParams = new LayoutParams();
                cellParams.columnSpec = GridLayout.spec(i);
                cellParams.rowSpec = GridLayout.spec(j);
                cellParams.height = cellSize;
                cellParams.width = cellSize;
                cellParams.setMargins(cellMargin / 2, cellMargin / 2, cellMargin / 2, cellMargin / 2);
                ToggleButton button = new ToggleButton(getContext(), null, 16843563);
                button.setText("");
                button.setTextOff("");
                button.setTextOn("");
                button.setHeight(cellSize);
                button.setWidth(cellSize);
                button.setSoundEffectsEnabled(false);
                button.setBackgroundResource(R.drawable.drawable_cell_wrong);
                button.setOnCheckedChangeListener(this.wrongCellListener);
                button.setVisibility(4);
                button.setEnabled(false);
                addView(button, cellParams);
                button.setAlpha(0.0f);
                cellsList.add(button);
                this.buttons[i][j] = button;
            }
        }
        GameRandom.shuffle(cellsList);
        cellsList = cellsList.subList(0, this.visibleCells);
        for (ToggleButton toggleButton : cellsList) {
            toggleButton.setTag("visible");
            toggleButton.setVisibility(0);
            toggleButton.setOnCheckedChangeListener(this.wrongCellListener);
        }
        for (ToggleButton toggleButton2 : cellsList.subList(0, this.successCells)) {
            toggleButton2.setTag("Success");
            toggleButton2.setBackgroundResource(R.drawable.drawable_cell_default);
            toggleButton2.setOnCheckedChangeListener(this.successCellClickListener);
        }
    }

    public void clearWrongCells() {
    }

    public int addSuccessCell() {
        throw new UnsupportedOperationException();
    }

    public int getSuccessCells() {
        return this.successCells;
    }

    public int getCurrentSuccessCellsClicked() {
        return this.currentSuccessCellsClicked;
    }

    public void rotateGrid(final int angle, int duration, View thumb, final RotationCompletedListener rotationCompletedListener) {
        ViewAnimator.animate(thumb).rotation((float) angle).duration((long) duration).onStop(new Stop() {
            public void onStop() {
                CatchRectangularGrid.this.setRotation((float) angle);
                rotationCompletedListener.onRotationCompleted();
            }
        }).start();
    }

    public void setUserEachDrawableOnlyOnce(boolean userEachDrawableOnlyOnce) {
        throw new UnsupportedOperationException();
    }

    public void changeSuccessDrawable(int drawableResId) {
        throw new UnsupportedOperationException();
    }

    public void setCellTypes(int cellTypes) {
    }

    public void animateCells() {
        double cellDelay = 400.0d / (1.0d * ((double) (this.colCount * this.colCount)));
        for (int i = 0; i < this.colCount; i++) {
            for (int j = 0; j < this.rowCount; j++) {
                View view = this.buttons[j][i];
                View[] viewArr = new View[]{view};
                ViewAnimator.animate(viewArr).fadeIn().duration(300).startDelay((long) ((int) (((double) ((this.rowCount * i) + j)) * cellDelay))).start();
            }
        }
    }

    public void animateFinishCells() {
        double cellDelay = 300.0d / (1.0d * ((double) (this.colCount * this.colCount)));
        for (int i = 0; i < this.colCount; i++) {
            for (int j = 0; j < this.rowCount; j++) {
                View view = this.buttons[j][i];
                View[] viewArr = new View[]{view};
                ViewAnimator.animate(viewArr).fadeOut().duration(200).startDelay((long) ((int) (((double) ((this.rowCount * i) + j)) * cellDelay))).start();
            }
        }
    }
}
