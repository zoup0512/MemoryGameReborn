package com.cube.memorygames.ui;

import android.content.Context;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build.VERSION;
import androidx.core.content.ContextCompat;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.GridLayout;
import android.widget.GridLayout.LayoutParams;
import android.widget.ToggleButton;
import com.cube.memorygames.logic.GameRandom;
import com.github.florent37.viewanimator.AnimationListener.Stop;
import com.github.florent37.viewanimator.ViewAnimator;
import com.memory.brain.training.games.R;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

public class SymmetryGrid extends GridLayout implements GameGrid {
    private static final float DIVIDER_COEFFICIENT = 0.1f;
    private static final String TAG_SUCCESS = "tagSuccess";
    private static final String TAG_SUCCESS_HINT = "tagSuccessHint";
    private View[][] buttons;
    private int cellTypes;
    private int colCount;
    private int currentSuccessCellsClicked = 0;
    private GridEventsListener gridEventsListener;
    private int height;
    private int lastAddedSuccessCellIndex = -1;
    private int rotatedAngle = 0;
    private int rowCount;
    private boolean showAnimation;
    private SuccessCellClickListener successCellClickListener = new SuccessCellClickListener();
    private int successCells;
    private int width;
    private WrongCellListener wrongCellListener = new WrongCellListener();

    private class SuccessCellClickListener implements OnCheckedChangeListener {
        private SuccessCellClickListener() {
        }

        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            buttonView.setEnabled(false);
            SymmetryGrid.this.currentSuccessCellsClicked = SymmetryGrid.this.currentSuccessCellsClicked + 1;
            int indexOfChild = SymmetryGrid.this.indexOfChild(buttonView);
            if (SymmetryGrid.this.currentSuccessCellsClicked >= SymmetryGrid.this.successCells) {
                buttonView.setBackgroundResource(R.drawable.drawable_state_cell_success_last);
            }
            if (SymmetryGrid.this.cellTypes == 0) {
                if (SymmetryGrid.this.lastAddedSuccessCellIndex != -1) {
                    if (SymmetryGrid.this.lastAddedSuccessCellIndex == indexOfChild) {
                        buttonView.setBackgroundResource(R.drawable.drawable_state_cell_success_last);
                        buttonView.setTag("ChangedBackgroud");
                    } else {
                        buttonView.setBackgroundResource(R.drawable.drawable_state_cell_wrong);
                        SymmetryGrid.this.getChildAt(SymmetryGrid.this.lastAddedSuccessCellIndex).setBackgroundResource(R.drawable.drawable_state_cell_unclicked);
                    }
                }
            } else if (SymmetryGrid.this.cellTypes == 1) {
                buttonView.setTag("SuccessClicked");
            }
            SymmetryGrid.this.notifySuccessCellClicked(indexOfChild);
        }
    }

    private class WrongCellListener implements OnCheckedChangeListener {
        private WrongCellListener() {
        }

        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            for (int i = 0; i < SymmetryGrid.this.getChildCount(); i++) {
                ToggleButton button = (ToggleButton) SymmetryGrid.this.getChildAt(i);
                if (!(button.getTag() == null || !button.getTag().equals(SymmetryGrid.TAG_SUCCESS) || button.isChecked())) {
                    button.setBackgroundResource(R.drawable.drawable_state_cell_unclicked);
                }
            }
            buttonView.setEnabled(false);
            SymmetryGrid.this.notifyFailCellClicked();
        }
    }

    public SymmetryGrid(Context context, int colCount, int rowCount, int successCells, int width, int height) {
        super(context);
        this.colCount = colCount;
        this.rowCount = rowCount;
        this.successCells = successCells;
        this.width = width;
        this.height = height;
        this.cellTypes = 0;
    }

    public void buildGrid() {
        int i;
        int j;
        setColumnCount(this.colCount);
        setRowCount(this.rowCount);
        float cellSizeFloat = ((float) this.width) / (((float) this.colCount) + (DIVIDER_COEFFICIENT * ((float) (this.colCount + 1))));
        int cellMargin = (int) (DIVIDER_COEFFICIENT * cellSizeFloat);
        int cellSize = (int) cellSizeFloat;
        int mainMargins = (this.width - ((this.colCount * cellSize) + ((this.colCount * 2) * (cellMargin / 2)))) / 2;
        setPadding(mainMargins, mainMargins, mainMargins, mainMargins);
        this.buttons = (View[][]) Array.newInstance(View.class, new int[]{this.colCount, this.rowCount});
        for (i = 0; i < this.colCount; i++) {
            for (j = 0; j < this.rowCount; j++) {
                LayoutParams cellParams = new LayoutParams();
                cellParams.columnSpec = GridLayout.spec(i);
                cellParams.rowSpec = GridLayout.spec(j);
                cellParams.height = cellSize;
                cellParams.width = cellSize;
                cellParams.setMargins(cellMargin / 2, cellMargin / 2, cellMargin / 2, cellMargin / 2);
                ToggleButton button = new ToggleButton(getContext());
                button.setText("");
                button.setTextOff("");
                button.setTextOn("");
                button.setHeight(cellSize);
                button.setWidth(cellSize);
                button.setSoundEffectsEnabled(false);
                button.setBackgroundResource(R.drawable.drawable_cell_wrong);
                button.setOnCheckedChangeListener(this.wrongCellListener);
                if (VERSION.SDK_INT >= 21) {
                    button.setStateListAnimator(null);
                }
                button.setEnabled(true);
                if (this.showAnimation) {
                    button.setAlpha(0.0f);
                }
                addView(button, cellParams);
                this.buttons[i][j] = button;
            }
        }
        List<Point> coordinates = new ArrayList();
        for (i = 0; i < this.colCount / 2; i++) {
            for (j = 0; j < this.rowCount; j++) {
                this.buttons[i][j].setEnabled(false);
                GradientDrawable drawable = (GradientDrawable) getResources().getDrawable(R.drawable.drawable_state_cell_dark_dot);
                drawable.setStroke((int) (((double) cellSize) * 0.35d), ContextCompat.getColor(getContext(), R.color.symmetry_background_color));
                this.buttons[i][j].setBackgroundDrawable(drawable);
                coordinates.add(new Point(i, j));
            }
        }
        GameRandom.shuffle(coordinates);
        for (Point point : coordinates.subList(0, this.successCells)) {
            ToggleButton winButton = (ToggleButton) this.buttons[point.x][point.y];
            winButton.setTag(TAG_SUCCESS_HINT);
            winButton.setBackgroundResource(R.drawable.drawable_state_cell_dark);
            winButton.setOnCheckedChangeListener(null);
            winButton.setChecked(true);
            winButton.setEnabled(false);
            winButton = (ToggleButton) this.buttons[(this.colCount - 1) - point.x][point.y];
            winButton.setTag(TAG_SUCCESS);
            winButton.setBackgroundResource(R.drawable.drawable_cell_default);
            winButton.setOnCheckedChangeListener(this.successCellClickListener);
            winButton.setChecked(false);
            winButton.setEnabled(true);
        }
    }

    public void showChallengeCells() {
    }

    public void hideAllCells() {
    }

    public void setShowAnimation(boolean showAnimation) {
        this.showAnimation = showAnimation;
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
    }

    public void enableAllCells() {
    }

    public void enableSuccessCells() {
    }

    public void animateCells() {
        double cellDelay = 400.0d / (1.0d * ((double) (this.colCount * this.colCount)));
        int position = 0;
        for (int i = 0; i < this.rowCount; i++) {
            for (int j = 0; j < this.colCount; j++) {
                View view = this.buttons[j][i];
                int delay = (int) (((double) position) * cellDelay);
                position++;
                ViewAnimator.animate(view).fadeIn().duration(300).startDelay((long) delay).start();
            }
        }
    }

    public void animateFinishCells() {
        double cellDelay = 300.0d / (1.0d * ((double) (this.colCount * this.colCount)));
        int position = 0;
        for (int i = 0; i < this.rowCount; i++) {
            for (int j = 0; j < this.colCount; j++) {
                View view = this.buttons[j][i];
                int delay = (int) (((double) position) * cellDelay);
                position++;
                ViewAnimator.animate(view).fadeOut().duration(200).startDelay((long) delay).start();
            }
        }
    }

    public int addSuccessCell() {
        return 0;
    }

    public int getSuccessCells() {
        return this.successCells;
    }

    public int getCurrentSuccessCellsClicked() {
        return this.currentSuccessCellsClicked;
    }

    public void rotateGrid(final int angle, int duration, View thumb, final RotationCompletedListener rotationCompletedListener) {
        this.rotatedAngle = angle;
        ViewAnimator.animate(thumb).rotation((float) angle).duration((long) duration).onStop(new Stop() {
            public void onStop() {
                SymmetryGrid.this.setRotation((float) angle);
                for (int i = 0; i < SymmetryGrid.this.getChildCount(); i++) {
                    View view = SymmetryGrid.this.getChildAt(i);
                    if (SymmetryGrid.this.rotatedAngle != 0) {
                        view.setRotation((float) (360 - SymmetryGrid.this.rotatedAngle));
                    }
                }
                rotationCompletedListener.onRotationCompleted();
            }
        }).start();
    }

    public void setDrawableIdsToUse(List<Integer> list) {
    }

    public void setDrawablesToUse(List<Drawable> list) {
    }

    public void setUserEachDrawableOnlyOnce(boolean userEachDrawableOnlyOnce) {
    }

    public void changeSuccessDrawable(int drawableResId) {
        for (int i = 0; i < getChildCount(); i++) {
            View view = getChildAt(i);
            if (view.getTag() != null && view.getTag().equals(TAG_SUCCESS)) {
                view.setBackgroundResource(drawableResId);
            }
        }
    }

    public void clearWrongCells() {
        for (int i = 0; i < getChildCount(); i++) {
            ToggleButton button = (ToggleButton) getChildAt(i);
            if (button.getTag() != null && button.getTag().equals(TAG_SUCCESS)) {
                button.setBackgroundResource(R.drawable.drawable_cell_default);
            }
        }
        this.successCells--;
        ToggleButton last = (ToggleButton) getChildAt(this.lastAddedSuccessCellIndex);
        last.setOnCheckedChangeListener(null);
        last.setChecked(false);
        last.setBackgroundResource(R.drawable.drawable_cell_wrong);
        last.setTag(null);
    }

    public void setCellTypes(int cellTypes) {
        this.cellTypes = cellTypes;
    }
}
