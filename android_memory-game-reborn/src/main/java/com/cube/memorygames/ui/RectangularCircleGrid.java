package com.cube.memorygames.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build.VERSION;
import android.support.v4.internal.view.SupportMenu;
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

public class RectangularCircleGrid extends GridLayout implements GameGrid {
    private static final float DIVIDER_COEFFICIENT = 0.1f;
    private static final float SHADOW_COEFFICIENT = 0.04f;
    private View[][] buttons;
    private List<Drawable> cellDrawables;
    private int cellTypes;
    private int colCount;
    private int currentSuccessCellsClicked = 0;
    private GridEventsListener gridEventsListener;
    private int height;
    private int lastAddedSuccessCellIndex = -1;
    private int rotatedAngle = 0;
    private int rowCount;
    private SuccessCellClickListener successCellClickListener = new SuccessCellClickListener();
    private int successCells;
    private boolean useDrawablesAsCells = false;
    private boolean userEachDrawableOnlyOnce = false;
    private int visibleCells;
    private int width;
    private WrongCellListener wrongCellListener = new WrongCellListener();

    private class SuccessCellClickListener implements OnCheckedChangeListener {
        private SuccessCellClickListener() {
        }

        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            buttonView.setEnabled(false);
            RectangularCircleGrid.this.currentSuccessCellsClicked = RectangularCircleGrid.this.currentSuccessCellsClicked + 1;
            int indexOfChild = RectangularCircleGrid.this.indexOfChild(buttonView);
            if (RectangularCircleGrid.this.currentSuccessCellsClicked >= RectangularCircleGrid.this.successCells) {
                buttonView.setBackgroundResource(R.drawable.drawable_state_circle_success_last);
            }
            if (RectangularCircleGrid.this.cellTypes == 0) {
                if (RectangularCircleGrid.this.lastAddedSuccessCellIndex != -1) {
                    if (RectangularCircleGrid.this.lastAddedSuccessCellIndex == indexOfChild) {
                        buttonView.setBackgroundResource(R.drawable.drawable_state_circle_success_last);
                        buttonView.setTag("ChangedBackgroud");
                    } else {
                        buttonView.setBackgroundResource(R.drawable.drawable_state_circle_wrong);
                        RectangularCircleGrid.this.getChildAt(RectangularCircleGrid.this.lastAddedSuccessCellIndex).setBackgroundResource(R.drawable.drawable_state_circle_unclicked);
                    }
                }
            } else if (RectangularCircleGrid.this.cellTypes == 1) {
                buttonView.setTag("SuccessClicked");
            }
            RectangularCircleGrid.this.notifySuccessCellClicked(indexOfChild);
        }
    }

    private class WrongCellListener implements OnCheckedChangeListener {
        private WrongCellListener() {
        }

        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            for (int i = 0; i < RectangularCircleGrid.this.getChildCount(); i++) {
                ToggleButton button = (ToggleButton) RectangularCircleGrid.this.getChildAt(i);
                if (!(button.getTag() == null || !button.getTag().equals("Success") || button.isChecked())) {
                    button.setBackgroundResource(R.drawable.drawable_state_circle_unclicked);
                }
            }
            buttonView.setEnabled(false);
            RectangularCircleGrid.this.notifyFailCellClicked();
        }
    }

    public RectangularCircleGrid(Context context, int colCount, int rowCount, int successCells, int width, int height) {
        super(context);
        this.colCount = colCount;
        this.rowCount = rowCount;
        this.successCells = successCells;
        this.width = width;
        this.height = height;
        this.cellTypes = 0;
    }

    public void showChallengeCells() {
        for (int i = 0; i < getChildCount(); i++) {
            ToggleButton button = (ToggleButton) getChildAt(i);
            button.setOnCheckedChangeListener(null);
            if (button.getTag() != null && button.getTag().equals("Success")) {
                button.setChecked(true);
                button.setVisibility(0);
            }
            if (button.getTag() != null && button.getTag().equals("ChangedBackgroud")) {
                button.setTag("Success");
                button.setBackgroundResource(R.drawable.drawable_circle_default);
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

    public void buildGrid() {
        setColumnCount(this.colCount);
        setRowCount(this.rowCount);
        float cellSizeFloat = ((float) this.width) / (((float) this.colCount) + (DIVIDER_COEFFICIENT * ((float) (this.colCount + 1))));
        int cellMargin = (int) (DIVIDER_COEFFICIENT * cellSizeFloat);
        int cellSize = (int) cellSizeFloat;
        int mainMargins = (this.width - ((this.colCount * cellSize) + ((this.colCount * 2) * (cellMargin / 2)))) / 2;
        setPadding(mainMargins, mainMargins, mainMargins, mainMargins);
        int successCellsPlaced = 0;
        this.buttons = (View[][]) Array.newInstance(View.class, new int[]{this.colCount, this.rowCount});
        for (int i = 0; i < this.colCount; i++) {
            for (int j = 0; j < this.rowCount; j++) {
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
                button.setBackgroundResource(R.drawable.drawable_circle_wrong);
                button.setOnCheckedChangeListener(this.wrongCellListener);
                if (VERSION.SDK_INT >= 21) {
                    button.setStateListAnimator(null);
                }
                button.setEnabled(false);
                button.setAlpha(0.0f);
                addView(button, cellParams);
                this.buttons[i][j] = button;
            }
        }
        int currentSelectedDrawableIndex = 0;
        while (successCellsPlaced < this.successCells) {
            ToggleButton winButton = (ToggleButton) getChildAt(GameRandom.nextInt(getChildCount() - 1));
            if (winButton.getTag() == null) {
                winButton.setTag("Success");
                if (!this.useDrawablesAsCells) {
                    winButton.setBackgroundResource(R.drawable.drawable_circle_default);
                } else if (this.cellDrawables.size() == 1) {
                    winButton.setBackgroundDrawable((Drawable) this.cellDrawables.get(0));
                } else if (this.userEachDrawableOnlyOnce || currentSelectedDrawableIndex < this.cellDrawables.size()) {
                    winButton.setBackgroundDrawable((Drawable) this.cellDrawables.get(currentSelectedDrawableIndex));
                    currentSelectedDrawableIndex++;
                } else {
                    winButton.setBackgroundDrawable((Drawable) this.cellDrawables.get(GameRandom.nextInt(this.cellDrawables.size() - 1)));
                }
                winButton.setOnCheckedChangeListener(this.successCellClickListener);
                successCellsPlaced++;
            }
        }
    }

    public int addSuccessCell() {
        boolean properPlaceFound = false;
        int x = -1;
        while (!properPlaceFound) {
            x = GameRandom.nextInt(getChildCount() - 1);
            ToggleButton winButton = (ToggleButton) getChildAt(x);
            if (winButton.getTag() == null) {
                winButton.setTag("Success");
                winButton.setBackgroundResource(R.drawable.drawable_circle_default);
                winButton.setOnCheckedChangeListener(this.successCellClickListener);
                properPlaceFound = true;
            }
        }
        this.successCells++;
        this.lastAddedSuccessCellIndex = x;
        return x;
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
                RectangularCircleGrid.this.setRotation((float) angle);
                for (int i = 0; i < RectangularCircleGrid.this.getChildCount(); i++) {
                    View view = RectangularCircleGrid.this.getChildAt(i);
                    if (RectangularCircleGrid.this.rotatedAngle != 0) {
                        view.setRotation((float) (360 - RectangularCircleGrid.this.rotatedAngle));
                    }
                }
                rotationCompletedListener.onRotationCompleted();
            }
        }).start();
    }

    public void setDrawableIdsToUse(List<Integer> drawableIdsToUse) {
        List<Drawable> drawablesToUse = new ArrayList();
        for (Integer resIs : drawableIdsToUse) {
            drawablesToUse.add(getContext().getResources().getDrawable(resIs.intValue()));
        }
        this.cellDrawables = drawablesToUse;
        this.useDrawablesAsCells = true;
    }

    public void setDrawablesToUse(List<Drawable> drawablesToUse) {
        this.cellDrawables = drawablesToUse;
        this.useDrawablesAsCells = true;
    }

    public void setUserEachDrawableOnlyOnce(boolean userEachDrawableOnlyOnce) {
        this.userEachDrawableOnlyOnce = userEachDrawableOnlyOnce;
    }

    public void changeSuccessDrawable(int drawableResId) {
        for (int i = 0; i < getChildCount(); i++) {
            View view = getChildAt(i);
            if (view.getTag() != null && view.getTag().equals("Success")) {
                view.setBackgroundResource(drawableResId);
            }
        }
    }

    public void fillFinishCells(int indexOfChild, Drawable nextCellImage, boolean success) {
        for (int i = 0; i < getChildCount(); i++) {
            ToggleButton toggleButton = (ToggleButton) getChildAt(i);
            if (toggleButton.getTag() != null && toggleButton.getTag().toString().equals("Success")) {
                if (success) {
                    if (i == indexOfChild) {
                        applyTint(toggleButton, -16711936, R.drawable.rightanswer);
                    } else {
                        applyTint(toggleButton, -16777216, 0);
                    }
                } else if (i == indexOfChild) {
                    applyTint(toggleButton, SupportMenu.CATEGORY_MASK, R.drawable.wronganswer);
                } else if (nextCellImage == toggleButton.getBackground()) {
                    applyTint(toggleButton, -16777216, 0);
                }
            }
        }
    }

    private void applyTint(View view, int color, int drawableResId) {
        color = adjustAlpha(color, 0.4f);
        Bitmap bitmap = drawableToBitmap(view.getBackground());
        Canvas c = new Canvas(bitmap);
        Paint p = new Paint();
        p.setColor(color);
        c.drawCircle((float) (c.getWidth() / 2), (float) (c.getHeight() / 2), (float) (c.getHeight() / 2), p);
        if (drawableResId != 0) {
            int paddingX = (int) (((double) c.getWidth()) * 0.1d);
            int paddingY = (int) (((double) c.getHeight()) * 0.1d);
            c.drawBitmap(BitmapFactory.decodeResource(getResources(), drawableResId), null, new RectF((float) paddingX, (float) paddingY, (float) (c.getWidth() - paddingX), (float) (c.getHeight() - paddingY)), new Paint());
        }
        view.setBackgroundDrawable(new BitmapDrawable(bitmap));
    }

    public static Bitmap drawableToBitmap(Drawable drawable) {
        Bitmap bitmap;
        if (drawable instanceof BitmapDrawable) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
            if (bitmapDrawable.getBitmap() != null) {
                return bitmapDrawable.getBitmap().copy(Config.ARGB_8888, true);
            }
        }
        if (drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            bitmap = Bitmap.createBitmap(1, 1, Config.ARGB_8888);
        } else {
            bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Config.ARGB_8888);
        }
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap.copy(Config.ARGB_8888, true);
    }

    private int adjustAlpha(int color, float factor) {
        return Color.argb(Math.round(((float) Color.alpha(color)) * factor), Color.red(color), Color.green(color), Color.blue(color));
    }

    public void setWrongCell(int index) {
        getChildAt(index).setBackgroundResource(R.drawable.drawable_state_circle_wrong);
        for (int i = 0; i < getChildCount(); i++) {
            if (i != index) {
                ToggleButton view = (ToggleButton) getChildAt(i);
                if (view.getTag() != null && view.getTag().toString().equals("Success")) {
                    view.setBackgroundResource(R.drawable.drawable_state_circle_unclicked);
                }
            }
        }
    }

    public void clearWrongCells() {
        for (int i = 0; i < getChildCount(); i++) {
            ToggleButton button = (ToggleButton) getChildAt(i);
            if (button.getTag() != null && button.getTag().equals("Success")) {
                button.setBackgroundResource(R.drawable.drawable_circle_default);
            }
        }
    }

    public void setCellTypes(int cellTypes) {
        this.cellTypes = cellTypes;
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
