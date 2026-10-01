package com.cube.memorygames.ui.hex;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import com.cube.memorygames.logic.GameRandom;
import com.cube.memorygames.ui.GameGrid;
import com.cube.memorygames.ui.GridEventsListener;
import com.cube.memorygames.ui.RotationCompletedListener;
import com.cube.memorygames.ui.hex.polygon.Point;
import com.cube.memorygames.ui.hex.polygon.Polygon;
import com.cube.memorygames.ui.hex.polygon.Polygon.Builder;
import com.github.florent37.viewanimator.ViewAnimator;
import com.memory.brain.training.games.R;
import com.mopub.volley.DefaultRetryPolicy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HexView extends View implements GameGrid {
    private static final float BITMAP_COEFFICIENT = 0.8f;
    private static final float DIVIDER_COEFFICIENT = 0.1f;
    private Set<Integer> clickedCells;
    private Set<Integer> correctCells;
    private Paint correctPaint;
    private Paint emptyPaint;
    private boolean enableClick;
    private GridEventsListener gridEventsListener;
    private int lastAdded = -1;
    private int lines;
    private Bitmap rightAnswer;
    private boolean showChallengeCells;
    private Paint strokePaint;
    private int successCells;
    private Paint unclickedPaint;
    private Bitmap wrongAnswer;
    private Paint wrongPaint;

    public HexView(Context context) {
        super(context);
        init();
    }

    public HexView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public HexView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        this.lines = 3;
        this.correctPaint = new Paint();
        this.wrongPaint = new Paint();
        this.emptyPaint = new Paint();
        this.unclickedPaint = new Paint();
        this.strokePaint = new Paint();
        this.correctPaint.setStyle(Style.FILL_AND_STROKE);
        this.wrongPaint.setStyle(Style.FILL_AND_STROKE);
        this.emptyPaint.setStyle(Style.FILL_AND_STROKE);
        this.unclickedPaint.setStyle(Style.FILL_AND_STROKE);
        this.strokePaint.setStyle(Style.STROKE);
        this.strokePaint.setStrokeWidth(getResources().getDimension(R.dimen.cell_stroke_width));
        this.enableClick = false;
        this.showChallengeCells = false;
        this.wrongAnswer = BitmapFactory.decodeResource(getResources(), R.drawable.wronganswer);
        this.rightAnswer = BitmapFactory.decodeResource(getResources(), R.drawable.rightanswer);
    }

    public void setCorrectColor(int correctColor) {
        this.correctPaint.setColor(correctColor);
        invalidate();
    }

    public void setWrongColor(int wrongColor) {
        this.wrongPaint.setColor(wrongColor);
        invalidate();
    }

    public void setEmptyColor(int emptyColor) {
        this.emptyPaint.setColor(emptyColor);
        invalidate();
    }

    public void setUnclickedColor(int unclickedColor) {
        this.unclickedPaint.setColor(unclickedColor);
        invalidate();
    }

    public void setStrokeColor(int strokeColor) {
        this.strokePaint.setColor(strokeColor);
        invalidate();
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

    public void setGameParams(int lines, int successCells) {
        int i;
        this.lines = lines;
        this.successCells = successCells;
        int totalCount = 0;
        for (i = 0; i < lines; i++) {
            totalCount += lines;
            if (i % 2 == 1) {
                totalCount++;
            }
        }
        List<Integer> cells = new ArrayList();
        for (i = 0; i < totalCount; i++) {
            cells.add(Integer.valueOf(i));
        }
        GameRandom.shuffle(cells);
        this.correctCells = new HashSet(cells.subList(0, successCells));
        this.clickedCells = new HashSet();
        invalidate();
    }

    public int getSuccessCells() {
        return this.successCells;
    }

    public int getCurrentSuccessCellsClicked() {
        return this.clickedCells.size();
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
        if (!this.enableClick || event.getAction() != 0) {
            return false;
        }
        Point p = new Point(event.getX(), event.getY());
        List<Polygon> polygons = getPolygons();
        for (int i = 0; i < polygons.size(); i++) {
            if (((Polygon) polygons.get(i)).contains(p)) {
                this.clickedCells.add(Integer.valueOf(i));
                this.lastAdded = i;
                invalidate();
                if (this.correctCells.contains(Integer.valueOf(i))) {
                    notifySuccessCellClicked(i);
                } else {
                    notifyFailCellClicked();
                }
                return true;
            }
        }
        return true;
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        List<Path> hexagons = getPaths();
        List<PointF> centerPoints = getPoints();
        float bitmapSize = getHexSize(DIVIDER_COEFFICIENT) * BITMAP_COEFFICIENT;
        for (int i = 0; i < hexagons.size(); i++) {
            Path path = (Path) hexagons.get(i);
            if (this.showChallengeCells) {
                if (this.correctCells.contains(Integer.valueOf(i))) {
                    if (this.clickedCells.contains(Integer.valueOf(i))) {
                        canvas.drawPath(path, this.correctPaint);
                        canvas.drawPath(path, this.strokePaint);
                    } else {
                        System.err.println("clickedCells.size() = " + this.clickedCells.size());
                        System.err.println("successCells = " + this.successCells);
                        if (this.clickedCells.isEmpty()) {
                            canvas.drawPath(path, this.correctPaint);
                            canvas.drawPath(path, this.strokePaint);
                        } else {
                            canvas.drawPath(path, this.unclickedPaint);
                            canvas.drawPath(path, this.strokePaint);
                        }
                    }
                } else if (this.clickedCells.contains(Integer.valueOf(i))) {
                    canvas.drawPath(path, this.wrongPaint);
                    canvas.drawPath(path, this.strokePaint);
                    drawBitmap(canvas, this.wrongAnswer, (PointF) centerPoints.get(i), bitmapSize);
                } else {
                    canvas.drawPath(path, this.emptyPaint);
                    canvas.drawPath(path, this.strokePaint);
                }
            } else if (!this.clickedCells.contains(Integer.valueOf(i))) {
                canvas.drawPath(path, this.emptyPaint);
                canvas.drawPath(path, this.strokePaint);
            } else if (this.correctCells.contains(Integer.valueOf(i))) {
                canvas.drawPath(path, this.correctPaint);
                canvas.drawPath(path, this.strokePaint);
                if (i == this.lastAdded && this.clickedCells.size() == this.correctCells.size()) {
                    drawBitmap(canvas, this.rightAnswer, (PointF) centerPoints.get(i), bitmapSize);
                }
            } else {
                canvas.drawPath(path, this.wrongPaint);
                canvas.drawPath(path, this.strokePaint);
                drawBitmap(canvas, this.wrongAnswer, (PointF) centerPoints.get(i), bitmapSize);
            }
        }
    }

    private void drawBitmap(Canvas canvas, Bitmap bitmap, PointF pointF, float size) {
        canvas.drawBitmap(bitmap, null, new RectF(pointF.x - (size / 2.0f), pointF.y - (size / 2.0f), pointF.x + (size / 2.0f), pointF.y + (size / 2.0f)), this.correctPaint);
    }

    private List<PointF> getPoints() {
        List<PointF> result = new ArrayList();
        if (getWidth() > 0) {
            int maxRowCount = this.lines + 1;
            float hexSize = getHexSize(DIVIDER_COEFFICIENT);
            float dividerSize = hexSize * DIVIDER_COEFFICIENT;
            float deltaY = (float) ((Math.sqrt(3.0d) / 2.0d) * ((double) (dividerSize + hexSize)));
            float starY = ((float) (getHeight() / 2)) - ((((float) (this.lines - 1)) / 2.0f) * deltaY);
            for (int i = 0; i < this.lines; i++) {
                float y = starY + (((float) i) * deltaY);
                int j;
                if (i % 2 == 1) {
                    for (j = 0; j < maxRowCount; j++) {
                        result.add(new PointF(((((float) j) + DefaultRetryPolicy.DEFAULT_BACKOFF_MULT) * dividerSize) + ((((float) j) + 0.5f) * hexSize), y));
                    }
                } else {
                    for (j = 0; j < maxRowCount - 1; j++) {
                        result.add(new PointF(((((float) j) + 1.5f) * dividerSize) + ((((float) j) + DefaultRetryPolicy.DEFAULT_BACKOFF_MULT) * hexSize), y));
                    }
                }
            }
        }
        return result;
    }

    private float getHexSize(float divider_coefficient) {
        int maxRowCount = this.lines + 1;
        return ((float) getWidth()) / ((((float) maxRowCount) + (((float) maxRowCount) * divider_coefficient)) + divider_coefficient);
    }

    private List<Polygon> getPolygons() {
        List<PointF> points = getPoints();
        List<Polygon> result = new ArrayList();
        float hexSize = getHexSize(0.0f);
        for (int i = 0; i < points.size(); i++) {
            PointF pointF = (PointF) points.get(i);
            result.add(getPolygon(pointF.x, pointF.y, hexSize / 2.0f));
        }
        return result;
    }

    private Polygon getPolygon(float x, float y, float height) {
        float radius = (float) (((double) height) / (Math.sqrt(3.0d) / 2.0d));
        Builder builder = Polygon.Builder();
        builder.addVertex(new Point(x, y - radius));
        builder.addVertex(new Point(x + height, y - (radius / 2.0f)));
        builder.addVertex(new Point(x + height, (radius / 2.0f) + y));
        builder.addVertex(new Point(x, y + radius));
        builder.addVertex(new Point(x - height, (radius / 2.0f) + y));
        builder.addVertex(new Point(x - height, y - (radius / 2.0f)));
        return builder.build();
    }

    private List<Path> getPaths() {
        List<PointF> points = getPoints();
        List<Path> result = new ArrayList();
        float hexSize = getHexSize(DIVIDER_COEFFICIENT);
        for (int i = 0; i < points.size(); i++) {
            PointF pointF = (PointF) points.get(i);
            result.add(getPath(pointF.x, pointF.y, hexSize / 2.0f));
        }
        return result;
    }

    private Path getPath(float x, float y, float height) {
        float radius = (float) (((double) height) / (Math.sqrt(3.0d) / 2.0d));
        Path hexagon = new Path();
        hexagon.moveTo(x, y - radius);
        hexagon.lineTo(x + height, y - (radius / 2.0f));
        hexagon.lineTo(x + height, (radius / 2.0f) + y);
        hexagon.lineTo(x, y + radius);
        hexagon.lineTo(x - height, (radius / 2.0f) + y);
        hexagon.lineTo(x - height, y - (radius / 2.0f));
        hexagon.lineTo(x, y - radius);
        return hexagon;
    }

    public void animateCells() {
        ViewAnimator.animate(this).fadeIn().duration(400).start();
    }

    public void animateFinishCells() {
        ViewAnimator.animate(this).fadeOut().duration(400).start();
    }
}
