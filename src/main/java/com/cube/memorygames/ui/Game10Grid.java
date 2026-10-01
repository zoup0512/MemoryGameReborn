package com.cube.memorygames.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import com.cube.memorygames.logic.GameRandom;
import com.memory.brain.training.games.R;
import com.mopub.mobileads.resource.DrawableConstants.CloseButton;
import java.util.ArrayList;
import java.util.List;

public class Game10Grid extends View implements GameGrid {
    private static final float BITMAP_COEFFICIENT = 0.8f;
    private static final int COLUMN_COUNT = 6;
    private static final float DIVIDER_COEFFICIENT = 0.01f;
    private static final int ROW_COUNT = 8;
    private static final int STATE_GAME = 1;
    private static final int STATE_LOSE = 3;
    private static final int STATE_WIN = 2;
    private List<Element> elements;
    private boolean enableClick;
    private Pair<Integer, Integer> failPosition;
    private GridEventsListener gridEventsListener;
    private Paint mainPaint;
    private List<Pair<Integer, Integer>> positions;
    private Bitmap rightAnswer;
    private boolean showChallengeCells;
    private int state = 0;
    private Bitmap wrongAnswer;

    public Game10Grid(Context context) {
        super(context);
        init();
    }

    public Game10Grid(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public Game10Grid(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        this.mainPaint = new Paint();
        this.mainPaint.setStyle(Style.FILL_AND_STROKE);
        this.enableClick = false;
        this.showChallengeCells = false;
        this.wrongAnswer = BitmapFactory.decodeResource(getResources(), R.drawable.wronganswer);
        this.rightAnswer = BitmapFactory.decodeResource(getResources(), R.drawable.rightanswer);
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

    public void setElements(List<Element> elements) {
        this.elements = elements;
        List<Pair<Integer, Integer>> positions = new ArrayList();
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 8; j++) {
                positions.add(new Pair(Integer.valueOf(i), Integer.valueOf(j)));
            }
        }
        GameRandom.shuffle(positions);
        this.positions = positions;
        this.state = 1;
        this.failPosition = null;
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
            float cellSize = Math.min(((float) getWidth()) / 6.07f, ((float) getHeight()) / 8.09f);
            float cellMargin = cellSize * DIVIDER_COEFFICIENT;
            Pair<Integer, Integer> position = (Pair) this.positions.get(i);
            Element element = (Element) this.elements.get(i);
            float left = (((((float) getWidth()) - ((6.0f * cellSize) + (7.0f * cellMargin))) / 2.0f) + cellMargin) + (((float) ((Integer) position.first).intValue()) * (cellSize + cellMargin));
            float top = (((((float) getHeight()) - ((CloseButton.STROKE_WIDTH * cellSize) + (9.0f * cellMargin))) / 2.0f) + cellMargin) + (((float) ((Integer) position.second).intValue()) * (cellSize + cellMargin));
            float right = left + cellSize;
            float bottom = top + cellSize;
            if (event.getX() > left && event.getX() < right && event.getY() > top && event.getY() < bottom) {
                if (element.isWin()) {
                    this.state = 2;
                    notifySuccessCellClicked(i);
                } else {
                    this.state = 3;
                    this.failPosition = position;
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
            float cellSize = Math.min(((float) getWidth()) / 6.07f, ((float) getHeight()) / 8.09f);
            float cellMargin = cellSize * DIVIDER_COEFFICIENT;
            float bottomPosition = (((float) getHeight()) - ((CloseButton.STROKE_WIDTH * cellSize) + (9.0f * cellMargin))) / 2.0f;
            float leftPosition = (((float) getWidth()) - ((6.0f * cellSize) + (7.0f * cellMargin))) / 2.0f;
            for (int i = 0; i < this.elements.size(); i++) {
                Pair<Integer, Integer> position = (Pair) this.positions.get(i);
                float left = (leftPosition + cellMargin) + (((float) ((Integer) position.first).intValue()) * (cellSize + cellMargin));
                float top = (bottomPosition + cellMargin) + (((float) ((Integer) position.second).intValue()) * (cellSize + cellMargin));
                float right = left + cellSize;
                float bottom = top + cellSize;
                drawFigure(canvas, (Element) this.elements.get(i), left, top, right, bottom);
                if (this.state == 3 && this.failPosition == position) {
                    drawBitmap(canvas, this.wrongAnswer, left, top, right, bottom);
                }
            }
        }
    }

    private void drawFigure(Canvas canvas, Element element, float left, float top, float right, float bottom) {
        float centerX = (left + right) / 2.0f;
        float centerY = (top + bottom) / 2.0f;
        float radius = (right - left) / 2.0f;
        this.mainPaint.setColor(element.getColor());
        switch (element.getFigure()) {
            case 0:
                float margin = radius * 0.1f;
                canvas.drawOval(new RectF(left + margin, top + margin, right - margin, bottom - margin), this.mainPaint);
                break;
            case 1:
                Path path1 = new Path();
                path1.moveTo(left, top + radius);
                path1.lineTo(left + radius, top);
                path1.lineTo(right, top + radius);
                path1.lineTo(left + radius, bottom);
                path1.lineTo(left, top + radius);
                path1.close();
                canvas.drawPath(path1, this.mainPaint);
                break;
            case 2:
                float side = (float) Math.sqrt((double) ((radius * radius) / 2.0f));
                Path path2 = new Path();
                path2.moveTo(centerX - side, centerY - side);
                path2.lineTo(centerX + side, centerY - side);
                path2.lineTo(centerX + side, centerY + side);
                path2.lineTo(centerX - side, centerY + side);
                path2.lineTo(centerX - side, centerY - side);
                path2.close();
                canvas.drawPath(path2, this.mainPaint);
                break;
            case 3:
                float side2 = (float) Math.sqrt((double) ((radius * radius) / 2.0f));
                Path path3 = new Path();
                path3.moveTo(left, top + radius);
                path3.lineTo(left + radius, top);
                path3.lineTo(right, top + radius);
                path3.lineTo(left + radius, bottom);
                path3.lineTo(left, top + radius);
                path3.close();
                Path path4 = new Path();
                path4.moveTo(centerX - side2, centerY - side2);
                path4.lineTo(centerX + side2, centerY - side2);
                path4.lineTo(centerX + side2, centerY + side2);
                path4.lineTo(centerX - side2, centerY + side2);
                path4.lineTo(centerX - side2, centerY - side2);
                path4.close();
                canvas.drawPath(path3, this.mainPaint);
                canvas.drawPath(path4, this.mainPaint);
                break;
        }
        if (element.isWin() && this.state == 2) {
            drawBitmap(canvas, this.rightAnswer, left, top, right, bottom);
        }
    }

    private void drawBitmap(Canvas canvas, Bitmap bitmap, float left, float top, float right, float bottom) {
        float margin = ((right - left) / 2.0f) * 0.3f;
        canvas.drawBitmap(bitmap, null, new RectF(left + margin, top + margin, right - margin, bottom - margin), null);
    }

    public void animateCells() {
    }

    public void animateFinishCells() {
    }
}
