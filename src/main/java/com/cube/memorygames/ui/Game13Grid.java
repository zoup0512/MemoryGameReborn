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
import com.cube.memorygames.SoundUtils;
import com.cube.memorygames.SoundUtils.SOUND;
import com.cube.memorygames.logic.GameRandom;
import com.memory.brain.training.games.R;
import com.mopub.mobileads.resource.DrawableConstants.CloseButton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Game13Grid extends View implements GameGrid {
    private static final int COLUMN_COUNT = 6;
    private static final float DIVIDER_COEFFICIENT = 0.01f;
    private static final int ROW_COUNT = 8;
    private static final int STATE_GAME = 1;
    private static final int STATE_WIN = 2;
    private Pair<Element, Element> elementTypes;
    private List<Element> elements;
    private boolean enableClick;
    private GridEventsListener gridEventsListener;
    private Paint mainPaint;
    private List<Pair<Integer, Integer>> positions;
    private Bitmap rightAnswer;
    private float shadowOffset;
    private float shadowRadius;
    private boolean showChallengeCells;
    private int state = 0;
    private Pair<Integer, Integer> winPosition;

    public Game13Grid(Context context) {
        super(context);
        init();
    }

    public Game13Grid(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public Game13Grid(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        this.mainPaint = new Paint();
        this.mainPaint.setStyle(Style.FILL_AND_STROKE);
        this.mainPaint.setAntiAlias(true);
        setLayerType(1, this.mainPaint);
        this.shadowRadius = getResources().getDimension(R.dimen.game13_shadow_radius);
        this.shadowOffset = getResources().getDimension(R.dimen.game13_shadow_offset);
        this.mainPaint.setShadowLayer(this.shadowRadius, this.shadowOffset, this.shadowOffset, 1996488704);
        this.enableClick = false;
        this.showChallengeCells = false;
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

    public void setElements(Pair<Element, Element> elementTypes, List<Element> elements) {
        this.elements = elements;
        this.elementTypes = elementTypes;
        List<Pair<Integer, Integer>> positions = new ArrayList();
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 8; j++) {
                positions.add(new Pair(Integer.valueOf(i), Integer.valueOf(j)));
            }
        }
        GameRandom.shuffle(positions);
        this.positions = positions;
        this.state = 1;
        this.winPosition = null;
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
            float left = (((((float) getWidth()) - ((6.0f * cellSize) + (7.0f * cellMargin))) / 2.0f) + cellMargin) + (((float) ((Integer) position.first).intValue()) * (cellSize + cellMargin));
            float top = (((((float) getHeight()) - ((CloseButton.STROKE_WIDTH * cellSize) + (9.0f * cellMargin))) / 2.0f) + cellMargin) + (((float) ((Integer) position.second).intValue()) * (cellSize + cellMargin));
            float right = left + cellSize;
            float bottom = top + cellSize;
            if (event.getX() > left && event.getX() < right && event.getY() > top && event.getY() < bottom) {
                changeElement(i);
                if (this.state == 2) {
                    this.winPosition = position;
                    invalidate();
                }
            }
        }
        return true;
    }

    private void changeElement(int position) {
        Element element1 = this.elementTypes.first;
        Element element2 = this.elementTypes.second;
        Element element = (Element) this.elements.get(position);
        if (element.getColor() == element1.getColor() && element.getFigure() == element1.getFigure()) {
            this.elements.set(position, element2);
            SoundUtils.playSound(getContext(), SOUND.TAP);
            invalidate();
            checkIfWin();
        } else if (element.getColor() == element2.getColor() && element.getFigure() == element2.getFigure()) {
            this.elements.set(position, element1);
            SoundUtils.playSound(getContext(), SOUND.TAP);
            invalidate();
            checkIfWin();
        }
    }

    private void checkIfWin() {
        Set<Element> displayedElements = new HashSet();
        displayedElements.addAll(this.elements);
        if (displayedElements.size() == 1) {
            this.state = 2;
            notifySuccessCellClicked(0);
        }
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (this.showChallengeCells || this.enableClick || this.state == 2) {
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
                if (this.state == 2 && this.winPosition == position) {
                    drawBitmap(canvas, this.rightAnswer, left, top, right, bottom);
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
                Path trianglePath = new Path();
                trianglePath.moveTo((float) (((double) centerX) + (((double) radius) * Math.cos(Math.toRadians(-90.0d)))), (float) (((double) centerY) + (((double) radius) * Math.sin(Math.toRadians(-90.0d)))));
                trianglePath.lineTo((float) (((double) centerX) + (((double) radius) * Math.cos(Math.toRadians(30.0d)))), (float) (((double) centerY) + (((double) radius) * Math.sin(Math.toRadians(30.0d)))));
                trianglePath.lineTo((float) (((double) centerX) + (((double) radius) * Math.cos(Math.toRadians(150.0d)))), (float) (((double) centerY) + (((double) radius) * Math.sin(Math.toRadians(150.0d)))));
                trianglePath.lineTo((float) (((double) centerX) + (((double) radius) * Math.cos(Math.toRadians(-90.0d)))), (float) (((double) centerY) + (((double) radius) * Math.sin(Math.toRadians(-90.0d)))));
                trianglePath.close();
                canvas.drawPath(trianglePath, this.mainPaint);
                break;
            case 1:
                Path pentagonPath = new Path();
                pentagonPath.moveTo((float) (((double) centerX) + (((double) radius) * Math.cos(Math.toRadians(-18.0d)))), (float) (((double) centerY) + (((double) radius) * Math.sin(Math.toRadians(-18.0d)))));
                pentagonPath.lineTo((float) (((double) centerX) + (((double) radius) * Math.cos(Math.toRadians(54.0d)))), (float) (((double) centerY) + (((double) radius) * Math.sin(Math.toRadians(54.0d)))));
                pentagonPath.lineTo((float) (((double) centerX) + (((double) radius) * Math.cos(Math.toRadians(126.0d)))), (float) (((double) centerY) + (((double) radius) * Math.sin(Math.toRadians(126.0d)))));
                pentagonPath.lineTo((float) (((double) centerX) + (((double) radius) * Math.cos(Math.toRadians(198.0d)))), (float) (((double) centerY) + (((double) radius) * Math.sin(Math.toRadians(198.0d)))));
                pentagonPath.lineTo((float) (((double) centerX) + (((double) radius) * Math.cos(Math.toRadians(270.0d)))), (float) (((double) centerY) + (((double) radius) * Math.sin(Math.toRadians(270.0d)))));
                pentagonPath.lineTo((float) (((double) centerX) + (((double) radius) * Math.cos(Math.toRadians(-18.0d)))), (float) (((double) centerY) + (((double) radius) * Math.sin(Math.toRadians(-18.0d)))));
                pentagonPath.close();
                canvas.drawPath(pentagonPath, this.mainPaint);
                break;
            case 2:
                Path hexagonPath = new Path();
                hexagonPath.moveTo((float) (((double) centerX) + (((double) radius) * Math.cos(Math.toRadians(0.0d)))), (float) (((double) centerY) + (((double) radius) * Math.sin(Math.toRadians(0.0d)))));
                hexagonPath.lineTo((float) (((double) centerX) + (((double) radius) * Math.cos(Math.toRadians(60.0d)))), (float) (((double) centerY) + (((double) radius) * Math.sin(Math.toRadians(60.0d)))));
                hexagonPath.lineTo((float) (((double) centerX) + (((double) radius) * Math.cos(Math.toRadians(120.0d)))), (float) (((double) centerY) + (((double) radius) * Math.sin(Math.toRadians(120.0d)))));
                hexagonPath.lineTo((float) (((double) centerX) + (((double) radius) * Math.cos(Math.toRadians(180.0d)))), (float) (((double) centerY) + (((double) radius) * Math.sin(Math.toRadians(180.0d)))));
                hexagonPath.lineTo((float) (((double) centerX) + (((double) radius) * Math.cos(Math.toRadians(240.0d)))), (float) (((double) centerY) + (((double) radius) * Math.sin(Math.toRadians(240.0d)))));
                hexagonPath.lineTo((float) (((double) centerX) + (((double) radius) * Math.cos(Math.toRadians(300.0d)))), (float) (((double) centerY) + (((double) radius) * Math.sin(Math.toRadians(300.0d)))));
                hexagonPath.lineTo((float) (((double) centerX) + (((double) radius) * Math.cos(Math.toRadians(0.0d)))), (float) (((double) centerY) + (((double) radius) * Math.sin(Math.toRadians(0.0d)))));
                hexagonPath.close();
                canvas.drawPath(hexagonPath, this.mainPaint);
                break;
            case 3:
                float margin = radius * 0.1f;
                canvas.drawOval(new RectF(left + margin, top + margin, right - margin, bottom - margin), this.mainPaint);
                break;
            case 4:
                Path path1 = new Path();
                path1.moveTo(left, top + radius);
                path1.lineTo(left + radius, top);
                path1.lineTo(right, top + radius);
                path1.lineTo(left + radius, bottom);
                path1.lineTo(left, top + radius);
                path1.close();
                canvas.drawPath(path1, this.mainPaint);
                break;
            case 5:
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
            case 6:
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
                canvas.drawPath(path4, this.mainPaint);
                this.mainPaint.clearShadowLayer();
                canvas.drawPath(path3, this.mainPaint);
                this.mainPaint.setShadowLayer(this.shadowRadius, this.shadowOffset, this.shadowOffset, 1996488704);
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
