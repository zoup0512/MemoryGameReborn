package com.cube.memorygames.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import com.cube.memorygames.SoundUtils;
import com.cube.memorygames.SoundUtils.SOUND;
import com.memory.brain.training.games.R;
import com.mopub.mobileads.resource.DrawableConstants.CloseButton;
import java.util.ArrayList;
import java.util.List;

public class Game23Grid extends View implements GameGrid {
    public static final int COLUMN_COUNT = 6;
    private static final float DIVIDER_COEFFICIENT = 0.05f;
    public static final int ROW_COUNT = 8;
    private static final int STATE_GAME = 1;
    private static final int STATE_LOSE = 3;
    private static final int STATE_WIN = 2;
    private List<ElementGame14> elements;
    private boolean enableClick;
    private int failNumber = -1;
    private GridEventsListener gridEventsListener;
    private Paint mainPaint;
    private int nextNumber;
    private Paint numberPaint;
    private List<Pair<Integer, Integer>> positions;
    private boolean showChallengeCells;
    private int state = 0;

    public Game23Grid(Context context) {
        super(context);
        init();
    }

    public Game23Grid(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public Game23Grid(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        this.mainPaint = new Paint();
        this.mainPaint.setStyle(Style.FILL_AND_STROKE);
        this.numberPaint = new Paint();
        this.numberPaint.setColor(-1);
        this.numberPaint.setTextSize(getResources().getDimension(R.dimen.text_size_big));
        this.numberPaint.setTypeface(Typeface.create(Typeface.DEFAULT, 1));
        this.numberPaint.setAntiAlias(true);
        this.enableClick = false;
        this.showChallengeCells = false;
    }

    private void notifySuccessCellClicked(int cellClickedIndex) {
        if (this.gridEventsListener != null) {
            this.gridEventsListener.onSuccessCellClicked(cellClickedIndex);
        }
    }

    private void notifyFailCellClicked() {
        if (this.gridEventsListener != null) {
            this.gridEventsListener.onFailCellClicked();
        }
    }

    public void setGridEventsListener(GridEventsListener gridEventsListener) {
        this.gridEventsListener = gridEventsListener;
        invalidate();
    }

    public void setElements(List<ElementGame14> elements, int nextNumber) {
        this.elements = elements;
        List<Pair<Integer, Integer>> positions = new ArrayList();
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 8; j++) {
                positions.add(new Pair(Integer.valueOf(i), Integer.valueOf(j)));
            }
        }
        this.positions = positions;
        this.nextNumber = nextNumber;
        this.state = 1;
        this.failNumber = -1;
    }

    public void showChallengeCells() {
        this.showChallengeCells = true;
        invalidate();
    }

    public void hideChallengeCells() {
        this.showChallengeCells = false;
        invalidate();
    }

    public void buildGrid() {
    }

    public void clearWrongCells() {
    }

    public void disableAllCells() {
        this.enableClick = false;
        invalidate();
    }

    public void enableAllCells() {
        this.enableClick = true;
        invalidate();
    }

    public int getCurrentSuccessCellsClicked() {
        if (this.state == 2) {
            return 1;
        }
        return 0;
    }

    public int getSuccessCells() {
        return 1;
    }

    public void rotateGrid(int angle, int duration, View thumb, RotationCompletedListener rotationCompletedListener) {
        throw new UnsupportedOperationException();
    }

    public int addSuccessCell() {
        throw new UnsupportedOperationException();
    }

    public void enableSuccessCells() {
        throw new UnsupportedOperationException();
    }

    public void hideAllCells() {
        throw new UnsupportedOperationException();
    }

    public void setCellTypes(int type) {
    }

    public void setDrawableIdsToUse(List<Integer> list) {
        throw new UnsupportedOperationException();
    }

    public void setDrawablesToUse(List<Drawable> list) {
        throw new UnsupportedOperationException();
    }

    public void setUserEachDrawableOnlyOnce(boolean userEachDrawableOnlyOnce) {
        throw new UnsupportedOperationException();
    }

    public void changeSuccessDrawable(int drawableResId) {
        throw new UnsupportedOperationException();
    }

    public boolean onTouchEvent(MotionEvent event) {
        if (!this.enableClick || event.getAction() != 0 || this.state != 1) {
            return false;
        }
        for (int i = 0; i < this.elements.size(); i++) {
            float cellSize = Math.min(((float) getWidth()) / 6.35f, ((float) getHeight()) / 8.45f);
            float cellMargin = cellSize * DIVIDER_COEFFICIENT;
            Pair<Integer, Integer> position = (Pair) this.positions.get(i);
            ElementGame14 elementGame14 = (ElementGame14) this.elements.get(i);
            float left = (((((float) getWidth()) - ((6.0f * cellSize) + (7.0f * cellMargin))) / 2.0f) + cellMargin) + (((float) ((Integer) position.first).intValue()) * (cellSize + cellMargin));
            float top = (((((float) getHeight()) - ((CloseButton.STROKE_WIDTH * cellSize) + (9.0f * cellMargin))) / 2.0f) + cellMargin) + (((float) ((Integer) position.second).intValue()) * (cellSize + cellMargin));
            float right = left + cellSize;
            float bottom = top + cellSize;
            if (event.getX() > left && event.getX() < right && event.getY() > top && event.getY() < bottom) {
                if (elementGame14.number == this.nextNumber) {
                    SoundUtils.playSound(getContext(), SOUND.TAP);
                    this.nextNumber++;
                    this.state = 2;
                    notifySuccessCellClicked(this.nextNumber);
                } else if (elementGame14.number > this.nextNumber) {
                    SoundUtils.playSound(getContext(), SOUND.TAP);
                    this.state = 3;
                    this.failNumber = elementGame14.number;
                    notifyFailCellClicked();
                }
                invalidate();
            }
        }
        return true;
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (this.showChallengeCells || this.enableClick || this.state == 2 || this.state == 3) {
            float cellSize = Math.min(((float) getWidth()) / 6.35f, ((float) getHeight()) / 8.45f);
            this.numberPaint.setTextSize(cellSize / 2.0f);
            float cellMargin = cellSize * DIVIDER_COEFFICIENT;
            float bottomPosition = (((float) getHeight()) - ((CloseButton.STROKE_WIDTH * cellSize) + (9.0f * cellMargin))) / 2.0f;
            float leftPosition = (((float) getWidth()) - ((6.0f * cellSize) + (7.0f * cellMargin))) / 2.0f;
            int loseColor = ContextCompat.getColor(getContext(), R.color.grid_unclicked);
            for (int i = 0; i < this.elements.size(); i++) {
                Pair<Integer, Integer> position = (Pair) this.positions.get(i);
                ElementGame14 elementGame14 = (ElementGame14) this.elements.get(i);
                if (elementGame14.number >= this.nextNumber) {
                    float left = (leftPosition + cellMargin) + (((float) ((Integer) position.first).intValue()) * (cellSize + cellMargin));
                    float top = (bottomPosition + cellMargin) + (((float) ((Integer) position.second).intValue()) * (cellSize + cellMargin));
                    float right = left + cellSize;
                    float bottom = top + cellSize;
                    if (this.state == 3 && this.failNumber == elementGame14.number) {
                        drawFigure(canvas, elementGame14, left, top, right, bottom, loseColor);
                    } else {
                        drawFigure(canvas, elementGame14, left, top, right, bottom);
                    }
                }
            }
        }
    }

    private void drawFigure(Canvas canvas, ElementGame14 elementGame14, float left, float top, float right, float bottom) {
        drawFigure(canvas, elementGame14, left, top, right, bottom, elementGame14.color);
    }

    private void drawFigure(Canvas canvas, ElementGame14 elementGame14, float left, float top, float right, float bottom, int color) {
        float centerX = (left + right) / 2.0f;
        float centerY = (top + bottom) / 2.0f;
        this.mainPaint.setColor(color);
        canvas.drawRect(left, top, right, bottom, this.mainPaint);
        Rect bounds = new Rect();
        String text = "" + elementGame14.number;
        this.numberPaint.getTextBounds(text, 0, text.length(), bounds);
        canvas.drawText("" + elementGame14.number, centerX - ((float) (bounds.width() / 2)), ((float) (bounds.height() / 2)) + centerY, this.numberPaint);
    }

    public void animateCells() {
    }

    public void animateFinishCells() {
    }
}
