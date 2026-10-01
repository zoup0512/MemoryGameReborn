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
import com.memory.brain.training.games.R;
import com.mopub.mobileads.resource.DrawableConstants.CloseButton;
import com.mopub.volley.DefaultRetryPolicy;
import java.util.List;

public class Game19Grid extends View implements GameGrid {
    private static final int MAX_COLUMN_COUNT = 5;
    private static final int MAX_ROW_COUNT = 7;
    private static final int STATE_GAME = 1;
    private static final int STATE_LOSE = 3;
    private static final int STATE_WIN = 2;
    private List<Pair<Integer, Integer>> colorSets;
    private int columnCount;
    private List<Element> elements;
    private boolean enableClick;
    private Pair<Integer, Integer> failPosition;
    private GridEventsListener gridEventsListener;
    private Paint mainPaint;
    private int rowCount;
    private boolean showChallengeCells;
    private int state = 0;
    private Bitmap wrongAnswer;

    public static class Element {
        public int colorCenter;
        public int colorSide;
        boolean isFinished;
        public int x;
        public int y;

        public Element(int colorCenter, int colorSide, int x, int y) {
            this.colorCenter = colorCenter;
            this.colorSide = colorSide;
            this.x = x;
            this.y = y;
        }
    }

    public Game19Grid(Context context) {
        super(context);
        init();
    }

    public Game19Grid(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public Game19Grid(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setLayerType(1, this.mainPaint);
        this.mainPaint = new Paint();
        this.mainPaint.setStyle(Style.FILL_AND_STROKE);
        this.mainPaint.setAntiAlias(true);
        float shadowRadius = getResources().getDimension(R.dimen.game18_shadow_radius);
        float shadowOffset = getResources().getDimension(R.dimen.game18_shadow_offset);
        this.mainPaint.setShadowLayer(shadowRadius, shadowOffset, shadowOffset, -1442840576);
        this.enableClick = false;
        this.showChallengeCells = false;
        this.wrongAnswer = BitmapFactory.decodeResource(getResources(), R.drawable.wronganswer);
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

    public void setElements(List<Pair<Integer, Integer>> colorSets, List<Element> elements, int columnCount, int rowCount) {
        this.elements = elements;
        this.colorSets = colorSets;
        this.columnCount = columnCount;
        this.rowCount = rowCount;
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
        float topPadding = ((float) getHeight()) / CloseButton.STROKE_WIDTH;
        float cellSize = Math.min(((float) getWidth()) / 5.0f, (((float) getHeight()) - topPadding) / 7.0f);
        float bottomPosition = topPadding + (((((float) getHeight()) - topPadding) - (((float) this.rowCount) * cellSize)) / 2.0f);
        float leftPosition = (((float) getWidth()) - (((float) this.columnCount) * cellSize)) / 2.0f;
        for (int i = 0; i < this.elements.size(); i++) {
            Element element = (Element) this.elements.get(i);
            float left = leftPosition + (((float) element.x) * cellSize);
            float top = bottomPosition + (((float) element.y) * cellSize);
            float right = left + cellSize;
            float bottom = top + cellSize;
            if (event.getX() > left && event.getX() < right && event.getY() > top && event.getY() < bottom) {
                if (isCorrect(element)) {
                    element.isFinished = true;
                    SoundUtils.playSound(getContext(), SOUND.TAP);
                    if (isAllElementsFinished()) {
                        this.state = 2;
                        notifySuccessCellClicked(i);
                    }
                } else {
                    this.state = 3;
                    this.failPosition = new Pair(Integer.valueOf(element.x), Integer.valueOf(element.y));
                    notifyFailCellClicked();
                }
                invalidate();
            }
        }
        return true;
    }

    private boolean isCorrect(Element element) {
        for (Pair<Integer, Integer> colorSet : this.colorSets) {
            if (element.colorCenter == ((Integer) colorSet.first).intValue() && element.colorSide == ((Integer) colorSet.second).intValue()) {
                return true;
            }
        }
        return false;
    }

    private boolean isAllElementsFinished() {
        for (Pair<Integer, Integer> colorSet : this.colorSets) {
            for (Element element : this.elements) {
                if (element.colorCenter == ((Integer) colorSet.first).intValue() && element.colorSide == ((Integer) colorSet.second).intValue() && !element.isFinished) {
                    return false;
                }
            }
        }
        return true;
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (this.showChallengeCells || this.enableClick || this.state == 2 || this.state == 3) {
            int i;
            float topPadding = ((float) getHeight()) / CloseButton.STROKE_WIDTH;
            for (i = 0; i < this.colorSets.size(); i++) {
                Pair<Integer, Integer> colorSet = (Pair) this.colorSets.get(i);
                float radius = topPadding * 0.5f;
                this.mainPaint.setColor(((Integer) colorSet.second).intValue());
                drawHexagon(canvas, ((float) getWidth()) * ((((float) i) + DefaultRetryPolicy.DEFAULT_BACKOFF_MULT) / ((float) (this.colorSets.size() + 1))), radius, 0.95f * radius);
                this.mainPaint.setColor(((Integer) colorSet.first).intValue());
                drawHexagon(canvas, ((float) getWidth()) * ((((float) i) + DefaultRetryPolicy.DEFAULT_BACKOFF_MULT) / ((float) (this.colorSets.size() + 1))), radius, 0.7f * radius);
            }
            float cellSize = Math.min(((float) getWidth()) / 5.0f, (((float) getHeight()) - topPadding) / 7.0f);
            float bottomPosition = topPadding + (((((float) getHeight()) - topPadding) - (((float) this.rowCount) * cellSize)) / 2.0f);
            float leftPosition = (((float) getWidth()) - (((float) this.columnCount) * cellSize)) / 2.0f;
            for (i = 0; i < this.elements.size(); i++) {
                Element element = (Element) this.elements.get(i);
                float left = leftPosition + (((float) element.x) * cellSize);
                float top = bottomPosition + (((float) element.y) * cellSize);
                float right = left + cellSize;
                float bottom = top + cellSize;
                drawFigure(canvas, element, left, top, right, bottom);
                if (this.state == 3 && ((Integer) this.failPosition.first).equals(Integer.valueOf(element.x)) && ((Integer) this.failPosition.second).equals(Integer.valueOf(element.y))) {
                    drawBitmap(canvas, this.wrongAnswer, left, top, right, bottom);
                }
            }
        }
    }

    private void drawFigure(Canvas canvas, Element element, float left, float top, float right, float bottom) {
        if (!element.isFinished) {
            float centerX = (left + right) / 2.0f;
            float centerY = (top + bottom) / 2.0f;
            float radius = (right - left) / 2.0f;
            this.mainPaint.setColor(element.colorSide);
            drawHexagon(canvas, centerX, centerY, 0.95f * radius);
            this.mainPaint.setColor(element.colorCenter);
            drawHexagon(canvas, centerX, centerY, 0.7f * radius);
        }
    }

    private void drawHexagon(Canvas canvas, float centerX, float centerY, float radius) {
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
