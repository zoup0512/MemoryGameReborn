package com.cube.memorygames.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import androidx.core.content.ContextCompat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import com.memory.brain.training.games.R;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Game22Grid extends View implements GameGrid {
    private static final int BOTTOM = 4;
    private static final int DEFAULT_SIZE = 64;
    private static final float DIVIDER_COEFFICIENT = 0.1f;
    private static final int LEFT = 1;
    private static final int MILLISECONDS_TILL_UPDATE = 90;
    private static final int NONE = 0;
    private static final int RIGHT = 2;
    private static final int STATE_GAME = 1;
    private static final int STATE_WIN = 2;
    private static final int TOP = 3;
    private List<List<Figure>> cellFigure;
    private List<List<CellType>> cells;
    private int desiredHeight;
    private int desiredWidth;
    private boolean enableClick;
    private PointExt end;
    private List<Figure> figures;
    private Bitmap finishBitmap;
    private Bitmap finishBitmap2;
    private GridEventsListener gridEventsListener;
    private int height;
    private List<PointExt> laser;
    private Paint laserPaintBorder;
    private Paint laserPaintCenter;
    private Path laserPath1;
    private Path laserPath2;
    private float laserSize;
    private List<CellType> mirrors;
    private Figure movingMirror;
    private PointF movingMirrorPadding;
    private boolean showChallengeCells;
    private PointExt start;
    private Bitmap[] startBitmap;
    private int state = 0;
    private Runnable updateView;
    private Handler viewHandler;
    private int width;

    public enum CellType {
        NONE,
        EMPTY,
        BUSY,
        LASER,
        EMPTY_MIRROR1,
        EMPTY_MIRROR2,
        SELECTED_MIRROR1,
        SELECTED_MIRROR2,
        MIRROR1,
        MIRROR2,
        BEGIN,
        END
    }

    private class Figure {
        private Bitmap[] bitmap;
        private float bottom;
        private CellType cell;
        private float cellSize;
        private float left;
        private float padding;
        private Paint paint1;
        private Paint paint2;
        private Path path1;
        private Path path2;
        private RectF rectF;
        private float right;
        private float top;

        Figure(Game22Grid game22Grid, Context context, CellType cell, float left, float top, float right, float bottom, float cellSize, Bitmap... bitmap) {
            this(context, cell, left, top, right, bottom, cellSize);
            this.bitmap = bitmap;
        }

        Figure(Context context, CellType cell, float left, float top, float right, float bottom, float cellSize) {
            this.cell = cell;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.cellSize = cellSize;
            this.padding = Game22Grid.DIVIDER_COEFFICIENT * cellSize;
            float margin = ((right - left) / 2.0f) * 0.0f;
            this.rectF = new RectF(left + margin, top + margin, right - margin, bottom - margin);
            setCell(context, cell);
        }

        void setCell(Context context, CellType cell) {
            this.cell = cell;
            this.paint1 = new Paint();
            this.paint1.setStyle(Style.FILL_AND_STROKE);
            this.paint2 = new Paint();
            this.paint2.setStyle(Style.FILL_AND_STROKE);
            if (CellType.BEGIN.equals(cell)) {
                this.paint1.setColor(-16776961);
            } else if (CellType.END.equals(cell)) {
                this.paint1.setColor(Color.RED);
            } else if (CellType.EMPTY.equals(cell) || CellType.EMPTY_MIRROR1.equals(cell) || CellType.EMPTY_MIRROR2.equals(cell)) {
                this.paint1.setColor(-1);
                this.paint2.setStyle(Style.STROKE);
                this.paint2.setStrokeWidth((float) Game22Grid.dpToPx(context, 1));
                this.paint2.setColor(ContextCompat.getColor(context, R.color.grid_stroke));
            } else if (CellType.LASER.equals(cell) || CellType.SELECTED_MIRROR1.equals(cell) || CellType.SELECTED_MIRROR2.equals(cell)) {
                this.paint1.setColor(Color.parseColor("#F2F8FC"));
                this.paint2.setStyle(Style.STROKE);
                this.paint2.setStrokeWidth((float) Game22Grid.dpToPx(context, 1));
                this.paint2.setColor(Color.parseColor("#E6EDF1"));
            } else if (CellType.BUSY.equals(cell)) {
                this.paint1.setColor(Color.parseColor("#B8B8B8"));
                this.paint2.setColor(Color.parseColor("#A0A0A0"));
                this.path1 = new Path();
                this.path1.moveTo(this.left, this.top);
                this.path1.lineTo(this.right, this.top);
                this.path1.lineTo(this.right, this.bottom);
                this.path1.lineTo(this.left, this.top);
                this.path1.close();
            } else if (CellType.MIRROR1.equals(cell) || CellType.MIRROR2.equals(cell)) {
                this.paint1.setStyle(Style.FILL);
                this.paint1.setAntiAlias(true);
                this.paint1.setColor(Color.parseColor("#03A9F4"));
                this.paint2.setStyle(Style.FILL);
                this.paint2.setAntiAlias(true);
                this.paint2.setColor(Color.parseColor("#92D5F3"));
                if (CellType.MIRROR1.equals(cell)) {
                    this.path1 = new Path();
                    this.path1.moveTo(this.left + this.padding, this.top + this.padding);
                    this.path1.lineTo(this.left + (this.padding * 2.0f), this.top + this.padding);
                    this.path1.lineTo(this.right - this.padding, this.bottom - (this.padding * 2.0f));
                    this.path1.lineTo(this.right - this.padding, this.bottom - this.padding);
                    this.path1.lineTo(this.left + this.padding, this.top + this.padding);
                    this.path1.close();
                    this.path2 = new Path();
                    this.path2.moveTo(this.left + this.padding, this.top + this.padding);
                    this.path2.lineTo(this.left + this.padding, this.top + (this.padding * 2.0f));
                    this.path2.lineTo(this.right - (this.padding * 2.0f), this.bottom - this.padding);
                    this.path2.lineTo(this.right - this.padding, this.bottom - this.padding);
                    this.path2.lineTo(this.left + this.padding, this.top + this.padding);
                    this.path1.close();
                } else if (CellType.MIRROR2.equals(cell)) {
                    this.path1 = new Path();
                    this.path1.moveTo(this.right - this.padding, this.top + this.padding);
                    this.path1.lineTo(this.right - (this.padding * 2.0f), this.top + this.padding);
                    this.path1.lineTo(this.left + this.padding, this.bottom - (this.padding * 2.0f));
                    this.path1.lineTo(this.left + this.padding, this.bottom - this.padding);
                    this.path1.lineTo(this.right - this.padding, this.top + this.padding);
                    this.path1.close();
                    this.path2 = new Path();
                    this.path2.moveTo(this.right - this.padding, this.top + this.padding);
                    this.path2.lineTo(this.right - this.padding, this.top + (this.padding * 2.0f));
                    this.path2.lineTo(this.left + (this.padding * 2.0f), this.bottom - this.padding);
                    this.path2.lineTo(this.left + this.padding, this.bottom - this.padding);
                    this.path2.lineTo(this.right - this.padding, this.top + this.padding);
                    this.path1.close();
                }
            }
        }

        void draw(Canvas canvas) {
            if (CellType.BEGIN.equals(this.cell)) {
                canvas.drawBitmap(this.bitmap[(int) ((System.currentTimeMillis() % 600) / 100)], null, this.rectF, null);
            } else if (CellType.END.equals(this.cell)) {
                if (Game22Grid.this.state == 2) {
                    canvas.drawBitmap(this.bitmap[1], null, this.rectF, null);
                } else {
                    canvas.drawBitmap(this.bitmap[0], null, this.rectF, null);
                }
            } else if (CellType.EMPTY.equals(this.cell) || CellType.EMPTY_MIRROR1.equals(this.cell) || CellType.EMPTY_MIRROR2.equals(this.cell)) {
                canvas.drawRect(this.left, this.top, this.right, this.bottom, this.paint1);
                canvas.drawRect(this.left, this.top, this.right, this.bottom, this.paint2);
            } else if (CellType.LASER.equals(this.cell) || CellType.SELECTED_MIRROR1.equals(this.cell) || CellType.SELECTED_MIRROR2.equals(this.cell)) {
                canvas.drawRect(this.left, this.top, this.right, this.bottom, this.paint1);
                canvas.drawRect(this.left, this.top, this.right, this.bottom, this.paint2);
            } else if (CellType.BUSY.equals(this.cell)) {
                canvas.drawRect(this.left, this.top, this.right, this.bottom, this.paint1);
                canvas.drawPath(this.path1, this.paint2);
            } else if (CellType.MIRROR1.equals(this.cell)) {
                canvas.drawPath(this.path1, this.paint1);
                canvas.drawPath(this.path2, this.paint2);
            } else if (CellType.MIRROR2.equals(this.cell)) {
                canvas.drawPath(this.path1, this.paint1);
                canvas.drawPath(this.path2, this.paint2);
            }
        }

        double getDistance(float x1, float y1) {
            float x2 = (this.left + this.right) / 2.0f;
            float y2 = (this.top + this.bottom) / 2.0f;
            return Math.sqrt((double) (((x1 - x2) * (x1 - x2)) + ((y1 - y2) * (y1 - y2))));
        }

        void updateCenter(float x, float y) {
            this.left = x;
            this.right = this.cellSize + x;
            this.top = y;
            this.bottom = this.cellSize + y;
            if (CellType.MIRROR1.equals(this.cell)) {
                this.path1 = new Path();
                this.path1.moveTo(this.left + this.padding, this.top + this.padding);
                this.path1.lineTo(this.left + (this.padding * 2.0f), this.top + this.padding);
                this.path1.lineTo(this.right - this.padding, this.bottom - (this.padding * 2.0f));
                this.path1.lineTo(this.right - this.padding, this.bottom - this.padding);
                this.path1.lineTo(this.left + this.padding, this.top + this.padding);
                this.path1.close();
                this.path2 = new Path();
                this.path2.moveTo(this.left + this.padding, this.top + this.padding);
                this.path2.lineTo(this.left + this.padding, this.top + (this.padding * 2.0f));
                this.path2.lineTo(this.right - (this.padding * 2.0f), this.bottom - this.padding);
                this.path2.lineTo(this.right - this.padding, this.bottom - this.padding);
                this.path2.lineTo(this.left + this.padding, this.top + this.padding);
                this.path1.close();
            } else if (CellType.MIRROR2.equals(this.cell)) {
                this.path1 = new Path();
                this.path1.moveTo(this.right - this.padding, this.top + this.padding);
                this.path1.lineTo(this.right - (this.padding * 2.0f), this.top + this.padding);
                this.path1.lineTo(this.left + this.padding, this.bottom - (this.padding * 2.0f));
                this.path1.lineTo(this.left + this.padding, this.bottom - this.padding);
                this.path1.lineTo(this.right - this.padding, this.top + this.padding);
                this.path1.close();
                this.path2 = new Path();
                this.path2.moveTo(this.right - this.padding, this.top + this.padding);
                this.path2.lineTo(this.right - this.padding, this.top + (this.padding * 2.0f));
                this.path2.lineTo(this.left + (this.padding * 2.0f), this.bottom - this.padding);
                this.path2.lineTo(this.left + this.padding, this.bottom - this.padding);
                this.path2.lineTo(this.right - this.padding, this.top + this.padding);
                this.path1.close();
            }
        }
    }

    private static class PointExt extends Point {
        private int direction;

        PointExt(int x, int y, int direction) {
            super(x, y);
            this.direction = direction;
        }

        int getDirection() {
            return this.direction;
        }
    }

    public Game22Grid(Context context) {
        super(context);
        init();
    }

    public Game22Grid(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public Game22Grid(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        this.desiredWidth = dpToPx(getContext(), 64);
        this.desiredHeight = dpToPx(getContext(), 64);
        this.enableClick = false;
        this.laserSize = (float) dpToPx(getContext(), 10);
        this.showChallengeCells = false;
        this.laserPaintCenter = new Paint();
        this.laserPaintCenter.setAntiAlias(true);
        this.laserPaintCenter.setStyle(Style.STROKE);
        this.laserPaintCenter.setColor(Color.parseColor("#F34334"));
        this.laserPaintCenter.setStrokeWidth(this.laserSize * 0.4f);
        this.laserPaintBorder = new Paint();
        this.laserPaintBorder.setAntiAlias(true);
        this.laserPaintBorder.setStyle(Style.STROKE);
        this.laserPaintBorder.setColor(Color.parseColor("#ED8988"));
        this.laserPaintBorder.setStrokeWidth(this.laserSize);
        this.startBitmap = new Bitmap[6];
        this.startBitmap[0] = BitmapFactory.decodeResource(getResources(), R.drawable.laser_1);
        this.startBitmap[1] = BitmapFactory.decodeResource(getResources(), R.drawable.laser_2);
        this.startBitmap[2] = BitmapFactory.decodeResource(getResources(), R.drawable.laser_3);
        this.startBitmap[3] = BitmapFactory.decodeResource(getResources(), R.drawable.laser_4);
        this.startBitmap[4] = BitmapFactory.decodeResource(getResources(), R.drawable.laser_5);
        this.startBitmap[5] = BitmapFactory.decodeResource(getResources(), R.drawable.laser_6);
        this.finishBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.point_gray);
        this.finishBitmap2 = BitmapFactory.decodeResource(getResources(), R.drawable.point_red);
        this.viewHandler = new Handler();
        this.updateView = new Runnable() {
            public void run() {
                Game22Grid.this.invalidate();
                Game22Grid.this.viewHandler.postDelayed(Game22Grid.this.updateView, 90);
            }
        };
    }

    private static int dpToPx(Context context, int dp) {
        return (int) (((double) (((float) dp) * context.getResources().getDisplayMetrics().density)) + 0.5d);
    }

    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(resolveSize(this.desiredWidth, widthMeasureSpec), resolveSize(this.desiredHeight, heightMeasureSpec));
    }

    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        this.width = getWidth();
        this.height = getHeight();
        initializeFigures();
    }

    private void initializeFigures() {
        int i;
        int rowCount = this.cells.size();
        int columnCount = ((List) this.cells.get(0)).size();
        float cellSize = Math.min(((float) this.width) / (((float) columnCount) + (DIVIDER_COEFFICIENT * ((float) (columnCount + 1)))), ((float) this.height) / (((float) rowCount) + (DIVIDER_COEFFICIENT * ((float) (rowCount + 1)))));
        float cellMargin = cellSize * DIVIDER_COEFFICIENT;
        float bottomPosition = ((((float) this.height) - ((((float) rowCount) * cellSize) + (((float) (rowCount + 1)) * cellMargin))) / 2.0f) - cellSize;
        float leftPosition = (((float) this.width) - ((((float) columnCount) * cellSize) + (((float) (columnCount + 1)) * cellMargin))) / 2.0f;
        this.figures = new ArrayList();
        this.cellFigure = new ArrayList();
        for (i = 0; i < this.cells.size(); i++) {
            List<Figure> lineCells = new ArrayList();
            for (int j = 0; j < ((List) this.cells.get(i)).size(); j++) {
                CellType cell = (CellType) ((List) this.cells.get(i)).get(j);
                float left = (leftPosition + cellMargin) + (((float) i) * (cellSize + cellMargin));
                float top = (bottomPosition + cellMargin) + (((float) j) * (cellSize + cellMargin));
                float right = left + cellSize;
                float bottom = top + cellSize;
                Bitmap[] bitmap = null;
                if (cell.equals(CellType.BEGIN)) {
                    bitmap = this.startBitmap;
                } else if (cell.equals(CellType.END)) {
                    bitmap = new Bitmap[]{this.finishBitmap, this.finishBitmap2};
                }
                Figure figure = new Figure(this, getContext(), cell, left, top, right, bottom, cellSize, bitmap);
                this.figures.add(figure);
                lineCells.add(figure);
            }
            this.cellFigure.add(lineCells);
        }
        float mirrorsLeftPosition = (((float) this.width) - ((((float) this.mirrors.size()) * cellSize) + (((float) (this.mirrors.size() + 1)) * cellMargin))) / 2.0f;
        List<Figure> mirrorFigures = new ArrayList();
        float left;
        float top;
        float right;
        float bottom;
        for (i = 0; i < this.mirrors.size(); i++) {
            CellType mirror = (CellType) this.mirrors.get(i);
            left = (mirrorsLeftPosition + cellMargin) + (((float) i) * (cellSize + cellMargin));
            top = ((float) this.height) - ((1.5f * cellSize) + cellMargin);
            right = left + cellSize;
            bottom = top + cellSize;
            if (mirror == CellType.MIRROR1) {
                this.figures.add(new Figure(getContext(), CellType.EMPTY_MIRROR1, left, top, right, bottom, cellSize));
            } else if (mirror == CellType.MIRROR2) {
                this.figures.add(new Figure(getContext(), CellType.EMPTY_MIRROR2, left, top, right, bottom, cellSize));
            }
            mirrorFigures.add(new Figure(getContext(), mirror, left, top, right, bottom, cellSize));
        }
        this.figures.addAll(mirrorFigures);
        updateLaser();
    }

    private void notifySuccessCellClicked(int cellClickedIndex) {
        if (this.gridEventsListener != null) {
            this.gridEventsListener.onSuccessCellClicked(cellClickedIndex);
        }
    }

    public void setGridEventsListener(GridEventsListener gridEventsListener) {
        this.gridEventsListener = gridEventsListener;
        invalidate();
    }

    public void setCells(List<List<CellType>> cells, List<CellType> mirrors, Point start, Point end) {
        this.cells = cells;
        this.mirrors = mirrors;
        this.start = new PointExt(start.x, start.y, 0);
        this.end = new PointExt(end.x, end.y, 0);
        ((List) this.cells.get(start.x)).set(start.y, CellType.BEGIN);
        ((List) this.cells.get(end.x)).set(end.y, CellType.END);
        this.state = 1;
        for (int i = 0; i < this.startBitmap.length; i++) {
            if (start.x == 0) {
                this.startBitmap[i] = rotateBitmap(this.startBitmap[i], 90.0f);
            } else if (start.x == cells.size() - 1) {
                this.startBitmap[i] = rotateBitmap(this.startBitmap[i], 270.0f);
            } else if (start.y == 0) {
                this.startBitmap[i] = rotateBitmap(this.startBitmap[i], 180.0f);
            }
        }
        if (end.x == cells.size() - 1) {
            this.finishBitmap = rotateBitmap(this.finishBitmap, 180.0f);
            this.finishBitmap2 = rotateBitmap(this.finishBitmap2, 180.0f);
        } else if (end.y == 0) {
            this.finishBitmap = rotateBitmap(this.finishBitmap, 90.0f);
            this.finishBitmap2 = rotateBitmap(this.finishBitmap2, 90.0f);
        } else if (end.y == cells.size() - 1) {
            this.finishBitmap = rotateBitmap(this.finishBitmap, 270.0f);
            this.finishBitmap2 = rotateBitmap(this.finishBitmap2, 270.0f);
        }
        this.viewHandler.post(this.updateView);
    }

    static Bitmap rotateBitmap(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
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
        this.viewHandler.removeCallbacks(this.updateView);
        invalidate();
    }

    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        this.viewHandler.removeCallbacks(this.updateView);
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
        if (!this.enableClick || this.state != 1) {
            return false;
        }
        if (event.getAction() == 0) {
            Figure nearestMirror = getNearestFigure(event.getX(), event.getY(), Arrays.asList(new CellType[]{CellType.MIRROR1, CellType.MIRROR2}));
            if (nearestMirror.getDistance(event.getX(), event.getY()) > ((double) nearestMirror.cellSize)) {
                return false;
            }
            getNearestFigure(event.getX(), event.getY(), Arrays.asList(new CellType[]{CellType.EMPTY_MIRROR1, CellType.EMPTY_MIRROR2, CellType.SELECTED_MIRROR1, CellType.SELECTED_MIRROR2})).setCell(getContext(), CellType.EMPTY);
            this.movingMirror = nearestMirror;
            this.movingMirrorPadding = new PointF(event.getX() - this.movingMirror.left, event.getY() - this.movingMirror.top);
            nearestMirror.updateCenter(event.getX() - this.movingMirrorPadding.x, event.getY() - this.movingMirrorPadding.y);
            updateLaser();
            invalidate();
        } else if (event.getAction() == 2) {
            if (!(this.movingMirror == null || this.movingMirrorPadding == null)) {
                this.movingMirror.updateCenter(event.getX() - this.movingMirrorPadding.x, event.getY() - this.movingMirrorPadding.y);
                invalidate();
            }
        } else if (event.getAction() == 1 || event.getAction() == 3) {
            Figure nearestEmptyCell = getNearestFigure((event.getX() - this.movingMirrorPadding.x) + (this.movingMirror.cellSize / 2.0f), (event.getY() - this.movingMirrorPadding.y) + (this.movingMirror.cellSize / 2.0f), Arrays.asList(new CellType[]{CellType.EMPTY, CellType.LASER}));
            if (this.movingMirror.cell == CellType.MIRROR1) {
                nearestEmptyCell.setCell(getContext(), CellType.EMPTY_MIRROR1);
            } else if (this.movingMirror.cell == CellType.MIRROR2) {
                nearestEmptyCell.setCell(getContext(), CellType.EMPTY_MIRROR2);
            }
            this.movingMirror.updateCenter(nearestEmptyCell.left, nearestEmptyCell.top);
            this.movingMirror = null;
            this.movingMirrorPadding = null;
            updateLaser();
            invalidate();
        }
        return true;
    }

    private Figure getNearestFigure(float x1, float y1, List<CellType> cells) {
        Figure nearestMirror = null;
        double nearestDistance = 0.0d;
        for (Figure figure : this.figures) {
            if (cells.contains(figure.cell)) {
                if (nearestMirror == null) {
                    nearestMirror = figure;
                    nearestDistance = figure.getDistance(x1, y1);
                } else {
                    double distance = figure.getDistance(x1, y1);
                    if (distance < nearestDistance) {
                        nearestMirror = figure;
                        nearestDistance = distance;
                    }
                }
            }
        }
        return nearestMirror;
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (this.showChallengeCells || this.enableClick || this.state == 2) {
            for (Figure figure : this.figures) {
                if (!figure.cell.equals(CellType.BEGIN)) {
                    figure.draw(canvas);
                }
            }
            if (this.laser != null) {
                canvas.drawPath(this.laserPath1, this.laserPaintBorder);
                canvas.drawPath(this.laserPath2 == null ? this.laserPath1 : this.laserPath2, this.laserPaintCenter);
                if (((Point) this.laser.get(this.laser.size() - 1)).equals(this.end) && this.state == 1) {
                    this.state = 2;
                    notifySuccessCellClicked(0);
                    invalidate();
                }
            }
            for (Figure figure2 : this.figures) {
                if (figure2.cell.equals(CellType.BEGIN)) {
                    figure2.draw(canvas);
                }
            }
        }
    }

    private void updateLaser() {
        Figure figure;
        this.laser = new ArrayList();
        for (Figure figure2 : this.figures) {
            if (figure2.cell.equals(CellType.LASER)) {
                figure2.setCell(getContext(), CellType.EMPTY);
            }
            if (figure2.cell.equals(CellType.SELECTED_MIRROR1)) {
                figure2.setCell(getContext(), CellType.EMPTY_MIRROR1);
            }
            if (figure2.cell.equals(CellType.SELECTED_MIRROR2)) {
                figure2.setCell(getContext(), CellType.EMPTY_MIRROR2);
            }
        }
        this.laser.add(this.start);
        getPoints(this.laser, 0);
        int rowCount = this.cells.size();
        int columnCount = ((List) this.cells.get(0)).size();
        float cellSize = Math.min(((float) this.width) / (((float) columnCount) + (DIVIDER_COEFFICIENT * ((float) (columnCount + 1)))), ((float) this.height) / (((float) rowCount) + (DIVIDER_COEFFICIENT * ((float) (rowCount + 1)))));
        float cellMargin = cellSize * DIVIDER_COEFFICIENT;
        float bottomPosition = ((((float) this.height) - ((((float) rowCount) * cellSize) + (((float) (rowCount + 1)) * cellMargin))) / 2.0f) - cellSize;
        float leftPosition = (((float) this.width) - ((((float) columnCount) * cellSize) + (((float) (columnCount + 1)) * cellMargin))) / 2.0f;
        this.laserPath1 = new Path();
        this.laserPath2 = null;
        for (int i = 0; i < this.laser.size(); i++) {
            PointExt point = (PointExt) this.laser.get(i);
            float x1 = ((leftPosition + cellMargin) + (((float) point.x) * (cellSize + cellMargin))) + (0.5f * cellSize);
            float y1 = ((bottomPosition + cellMargin) + (((float) point.y) * (cellSize + cellMargin))) + (0.5f * cellSize);
            if (i == 0) {
                this.laserPath1.moveTo(x1, y1);
            }
                    Figure figure2;
figure2 = (Figure) ((List) this.cellFigure.get(point.x)).get(point.y);
            float padding = (this.laserSize / 2.0f) + (DIVIDER_COEFFICIENT * cellSize);
            if (figure2.cell.equals(CellType.EMPTY_MIRROR1) || figure2.cell.equals(CellType.SELECTED_MIRROR1)) {
                switch (point.getDirection()) {
                    case 1:
                        this.laserPath1.lineTo(x1 + padding, y1);
                        this.laserPath1.lineTo(x1, y1 - padding);
                        break;
                    case 2:
                        this.laserPath1.lineTo(x1 - padding, y1);
                        this.laserPath1.lineTo(x1, y1 + padding);
                        break;
                    case 3:
                        this.laserPath1.lineTo(x1, y1 + padding);
                        this.laserPath1.lineTo(x1 - padding, y1);
                        break;
                    case 4:
                        this.laserPath1.lineTo(x1, y1 - padding);
                        this.laserPath1.lineTo(x1 + padding, y1);
                        break;
                    default:
                        break;
                }
            } else if (figure2.cell.equals(CellType.EMPTY_MIRROR2) || figure2.cell.equals(CellType.SELECTED_MIRROR2)) {
                switch (point.getDirection()) {
                    case 1:
                        this.laserPath1.lineTo(x1 + padding, y1);
                        this.laserPath1.lineTo(x1, y1 + padding);
                        break;
                    case 2:
                        this.laserPath1.lineTo(x1 - padding, y1);
                        this.laserPath1.lineTo(x1, y1 - padding);
                        break;
                    case 3:
                        this.laserPath1.lineTo(x1, y1 + padding);
                        this.laserPath1.lineTo(x1 + padding, y1);
                        break;
                    case 4:
                        this.laserPath1.lineTo(x1, y1 - padding);
                        this.laserPath1.lineTo(x1 - padding, y1);
                        break;
                    default:
                        break;
                }
            } else {
                if (i == this.laser.size() - 1) {
                    PointExt point2 = (PointExt) this.laser.get(i - 1);
                    x1 = ((((leftPosition + cellMargin) + (((float) point2.x) * (cellSize + cellMargin))) + (0.5f * cellSize)) + x1) / 2.0f;
                    y1 = ((((bottomPosition + cellMargin) + (((float) point2.y) * (cellSize + cellMargin))) + (0.5f * cellSize)) + y1) / 2.0f;
                    float middleX1 = x1;
                    float middleY1 = y1;
                    this.laserPath2 = new Path(this.laserPath1);
                    if (!figure2.cell.equals(CellType.END)) {
                        switch (point.getDirection()) {
                            case 1:
                                x1 += cellMargin / 2.0f;
                                middleX1 = x1 + (this.laserSize * 0.3f);
                                break;
                            case 2:
                                x1 -= cellMargin / 2.0f;
                                middleX1 = x1 - (this.laserSize * 0.3f);
                                break;
                            case 3:
                                y1 += cellMargin / 2.0f;
                                middleY1 = y1 + (this.laserSize * 0.3f);
                                break;
                            case 4:
                                y1 -= cellMargin / 2.0f;
                                middleY1 = y1 - (this.laserSize * 0.3f);
                                break;
                            default:
                                break;
                        }
                    }
                    switch (point.getDirection()) {
                        case 1:
                            x1 -= 0.2f * cellSize;
                            middleX1 = x1 - this.laserSize;
                            break;
                        case 2:
                            x1 += 0.2f * cellSize;
                            middleX1 = x1 + this.laserSize;
                            break;
                        case 3:
                            y1 -= 0.2f * cellSize;
                            middleY1 = y1 - this.laserSize;
                            break;
                        case 4:
                            y1 += 0.2f * cellSize;
                            middleY1 = y1 + this.laserSize;
                            break;
                    }
                    this.laserPath2.lineTo(middleX1, middleY1);
                }
                this.laserPath1.lineTo(x1, y1);
            }
        }
    }

    private List<PointExt> getPoints(List<PointExt> points, int previousDirection) {
        Point point = (Point) points.get(points.size() - 1);
        int direction = getDirection(point, previousDirection);
        PointExt nextPoint = null;
        switch (direction) {
            case 1:
                nextPoint = new PointExt(point.x - 1, point.y, 1);
                break;
            case 2:
                nextPoint = new PointExt(point.x + 1, point.y, 2);
                break;
            case 3:
                nextPoint = new PointExt(point.x, point.y - 1, 3);
                break;
            case 4:
                nextPoint = new PointExt(point.x, point.y + 1, 4);
                break;
        }
        if (nextPoint == null) {
            return points;
        }
        if (((Figure) ((List) this.cellFigure.get(nextPoint.x)).get(nextPoint.y)).cell.equals(CellType.EMPTY)) {
            ((Figure) ((List) this.cellFigure.get(nextPoint.x)).get(nextPoint.y)).setCell(getContext(), CellType.LASER);
        } else if (((Figure) ((List) this.cellFigure.get(nextPoint.x)).get(nextPoint.y)).cell.equals(CellType.EMPTY_MIRROR1)) {
            ((Figure) ((List) this.cellFigure.get(nextPoint.x)).get(nextPoint.y)).setCell(getContext(), CellType.SELECTED_MIRROR1);
        } else if (((Figure) ((List) this.cellFigure.get(nextPoint.x)).get(nextPoint.y)).cell.equals(CellType.EMPTY_MIRROR2)) {
            ((Figure) ((List) this.cellFigure.get(nextPoint.x)).get(nextPoint.y)).setCell(getContext(), CellType.SELECTED_MIRROR2);
        }
        if (points.contains(nextPoint)) {
            points.add(nextPoint);
            return points;
        }
        points.add(nextPoint);
        return getPoints(points, direction);
    }

    private int getDirection(Point point, int previousDirection) {
        CellType cell = ((Figure) ((List) this.cellFigure.get(point.x)).get(point.y)).cell;
        if (previousDirection == 0) {
            if (point.x == 0) {
                return 2;
            }
            if (point.x == this.cellFigure.size() - 1) {
                return 1;
            }
            if (point.y == 0) {
                return 4;
            }
            if (point.y == this.cellFigure.size() - 1) {
                return 3;
            }
        } else if (CellType.SELECTED_MIRROR1.equals(cell)) {
            switch (previousDirection) {
                case 1:
                    return 3;
                case 2:
                    return 4;
                case 3:
                    return 1;
                case 4:
                    return 2;
            }
        } else if (CellType.SELECTED_MIRROR2.equals(cell)) {
            switch (previousDirection) {
                case 1:
                    return 4;
                case 2:
                    return 3;
                case 3:
                    return 2;
                case 4:
                    return 1;
                default:
                    break;
            }
        } else if (CellType.EMPTY.equals(cell)) {
            return previousDirection;
        } else {
            if (CellType.LASER.equals(cell)) {
                return previousDirection;
            }
        }
        return 0;
    }

    public void animateCells() {
    }

    public void animateFinishCells() {
    }
}
