package com.cube.memorygames.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import com.cube.memorygames.logic.GameRandom;
import com.memory.brain.training.games.R;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Game15Grid extends View implements GameGrid {
    private static final int DEFAULT_SPEED = 2000;
    private Bitmap cloudBitmap;
    private int cloudSize;
    private List<Pair<Integer, Integer>> clouds;
    private List<Pair<Integer, Integer>> coordinates;
    private boolean enableClick;
    private Bitmap figure;
    private int figureSize;
    private GameType gameType;
    private GestureDetector gestureDetector;
    private GridEventsListener gridEventsListener;
    private PointF hintPosition;
    private Bitmap movingBitmap;
    private Side movingClouds;
    private Side movingSide;
    private Bitmap orientationBitmap;
    private Side orientationSide;
    private List<Integer> speeds;
    private long startedTime;
    private long stoppedTime;
    private Runnable updateView;
    private Handler viewHandler;

    public enum GameType {
        TYPE_MOVING,
        TYPE_ORIENTATION;
        


        public static GameType randomGameType() {
            return (GameType) values()[GameRandom.nextInt(values().length)];
        }
    }

    public enum Side {
        LEFT,
        RIGHT,
        UP,
        DOWN;
        


        public static Side randomSide() {
            return (Side) values()[GameRandom.nextInt(values().length)];
        }
    }

    public Game15Grid(Context context) {
        super(context);
        init();
    }

    public Game15Grid(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public Game15Grid(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        this.enableClick = false;
        this.movingBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.paperplane_cyan);
        this.orientationBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.paperplane_orange);
        this.cloudBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.rain_cloud);
        this.viewHandler = new Handler();
        this.updateView = new Runnable() {
            public void run() {
                Game15Grid.this.invalidate();
                Game15Grid.this.viewHandler.postDelayed(Game15Grid.this.updateView, 20);
            }
        };
        this.gestureDetector = new GestureDetector(getContext(), new GestureListener(getContext()) {
            public void onSwipe(Side side, MotionEvent e) {
                Game15Grid.this.viewHandler.removeCallbacks(Game15Grid.this.updateView);
                Game15Grid.this.stoppedTime = System.currentTimeMillis();
                if (Game15Grid.this.gameType == GameType.TYPE_MOVING) {
                    if (side == Game15Grid.this.movingSide) {
                        Game15Grid.this.notifySuccessCellClicked(e);
                    } else {
                        Game15Grid.this.notifyFailCellClicked();
                    }
                } else if (side == Game15Grid.this.orientationSide) {
                    Game15Grid.this.notifySuccessCellClicked(e);
                } else {
                    Game15Grid.this.notifyFailCellClicked();
                }
            }
        });
    }

    public void setGameParams(GameType gameType, Side movingSide, Side orientationSide, Side movingClouds, boolean differentSpeed) {
        Bitmap bitmap;
        this.gameType = gameType;
        this.movingSide = movingSide;
        this.orientationSide = orientationSide;
        this.movingClouds = movingClouds;
        this.stoppedTime = 0;
        if (gameType == GameType.TYPE_MOVING) {
            bitmap = this.movingBitmap;
        } else {
            bitmap = this.orientationBitmap;
        }
        Matrix matrix = new Matrix();
        switch (orientationSide) {
            case DOWN:
                matrix.postRotate(180.0f);
                break;
            case RIGHT:
                matrix.postRotate(90.0f);
                break;
            case LEFT:
                matrix.postRotate(270.0f);
                break;
        }
        this.figure = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        this.figureSize = Math.max(this.figure.getWidth(), this.figure.getHeight());
        this.cloudSize = Math.max(this.cloudBitmap.getWidth(), this.cloudBitmap.getHeight());
        this.speeds = new ArrayList();
        for (int i = 0; i < 6; i++) {
            if (differentSpeed) {
                this.speeds.add(Integer.valueOf(GameRandom.nextInt(DEFAULT_SPEED) + 1000));
            } else {
                this.speeds.add(Integer.valueOf(DEFAULT_SPEED));
            }
        }
        this.viewHandler.post(this.updateView);
        this.hintPosition = null;
        this.startedTime = System.currentTimeMillis();
    }

    protected void onDraw(Canvas canvas) {
        long diff;
        int i;
        super.onDraw(canvas);
        if (this.stoppedTime == 0) {
            diff = System.currentTimeMillis() - this.startedTime;
        } else {
            diff = this.stoppedTime - this.startedTime;
        }
        float width = (float) getWidth();
        float height = (float) getHeight();
        float left;
        float top;
        List<Pair<Integer, Integer>> clouds = buildAndGetClouds();
        for (i = 0; i < clouds.size(); i++) {
            Pair<Integer, Integer> cloud = (Pair) clouds.get(i);
            left = (float) ((Integer) cloud.first).intValue();
            top = (float) ((Integer) cloud.second).intValue();
            if (this.movingClouds != null) {
                switch (this.movingClouds) {
                    case RIGHT:
                        left += (((float) (diff % 4000)) * width) / 4000.0f;
                        if (left < width) {
                            if (left < width - ((float) this.cloudSize)) {
                                break;
                            }
                            canvas.drawBitmap(this.cloudBitmap, left, top, null);
                            left -= width;
                            break;
                        }
                        left -= width;
                        break;
                    case LEFT:
                        left -= (((float) (diff % 4000)) * width) / 4000.0f;
                        if (left > ((float) (-this.cloudSize))) {
                            if (left > 0.0f) {
                                break;
                            }
                            canvas.drawBitmap(this.cloudBitmap, left, top, null);
                            left += width;
                            break;
                        }
                        left += width;
                        break;
                    default:
                        break;
                }
            }
            canvas.drawBitmap(this.cloudBitmap, left, top, null);
        }
        List<Pair<Integer, Integer>> coordinates = buildAndGetFigures();
        for (i = 0; i < coordinates.size(); i++) {
            Pair<Integer, Integer> coordinate = (Pair) coordinates.get(i);
            int horizontalScreenDuration = ((Integer) this.speeds.get(i)).intValue();
            int verticalScreenDuration = (int) ((((float) horizontalScreenDuration) * height) / width);
            left = (float) ((Integer) coordinate.first).intValue();
            top = (float) ((Integer) coordinate.second).intValue();
            switch (this.movingSide) {
                case DOWN:
                    top += (((float) (diff % ((long) verticalScreenDuration))) * height) / ((float) verticalScreenDuration);
                    if (top < height) {
                        if (top < height - ((float) this.figureSize)) {
                            break;
                        }
                        canvas.drawBitmap(this.figure, left, top, null);
                        top -= height;
                        break;
                    }
                    top -= height;
                    break;
                case RIGHT:
                    left += (((float) (diff % ((long) horizontalScreenDuration))) * width) / ((float) horizontalScreenDuration);
                    if (left < width) {
                        if (left < width - ((float) this.figureSize)) {
                            break;
                        }
                        canvas.drawBitmap(this.figure, left, top, null);
                        left -= width;
                        break;
                    }
                    left -= width;
                    break;
                case LEFT:
                    left -= (((float) (diff % ((long) horizontalScreenDuration))) * width) / ((float) horizontalScreenDuration);
                    if (left > ((float) (-this.figureSize))) {
                        if (left > 0.0f) {
                            break;
                        }
                        canvas.drawBitmap(this.figure, left, top, null);
                        left += width;
                        break;
                    }
                    left += width;
                    break;
                case UP:
                    top -= (((float) (diff % ((long) verticalScreenDuration))) * height) / ((float) verticalScreenDuration);
                    if (top > ((float) (-this.figureSize))) {
                        if (top > 0.0f) {
                            break;
                        }
                        canvas.drawBitmap(this.figure, left, top, null);
                        top += height;
                        break;
                    }
                    top += height;
                    break;
                default:
                    break;
            }
            canvas.drawBitmap(this.figure, left, top, null);
        }
    }

    private List<Pair<Integer, Integer>> buildAndGetFigures() {
        int i = 1;
        if (this.coordinates != null) {
            return this.coordinates;
        }
        int i2;
        int width = getWidth();
        int height = getHeight();
        if (width <= 0) {
            i2 = 1;
        } else {
            i2 = 0;
        }
        if (width > 0) {
            i = 0;
        }
        if ((i | i2) != 0) {
            return new ArrayList();
        }
        int maxX = width - this.figureSize;
        int maxY = height - this.figureSize;
        List<Pair<Integer, Integer>> coordinates = new ArrayList();
        int retryCount = 0;
        while (coordinates.size() < 6 && retryCount < 100) {
            retryCount++;
            int x = GameRandom.nextInt(maxX);
            int y = GameRandom.nextInt(maxY);
            if (coordinates.isEmpty()) {
                coordinates.add(new Pair(Integer.valueOf(x), Integer.valueOf(y)));
                retryCount = 0;
            } else {
                boolean isIntercept = false;
                for (Pair<Integer, Integer> coordinate : coordinates) {
                    if (Math.abs(x - ((Integer) coordinate.first).intValue()) <= this.figureSize && Math.abs(y - ((Integer) coordinate.second).intValue()) <= this.figureSize) {
                        isIntercept = true;
                        break;
                    }
                }
                if (!isIntercept) {
                    coordinates.add(new Pair(Integer.valueOf(x), Integer.valueOf(y)));
                    retryCount = 0;
                }
            }
        }
        this.coordinates = coordinates;
        return coordinates;
    }

    private List<Pair<Integer, Integer>> buildAndGetClouds() {
        int i = 1;
        if (this.clouds != null) {
            return this.clouds;
        }
        int i2;
        int width = getWidth();
        int height = getHeight();
        if (width <= 0) {
            i2 = 1;
        } else {
            i2 = 0;
        }
        if (width > 0) {
            i = 0;
        }
        if ((i | i2) != 0) {
            return new ArrayList();
        }
        int maxX = width - this.cloudSize;
        int maxY = height - this.cloudSize;
        List<Pair<Integer, Integer>> clouds = new ArrayList();
        int retryCount = 0;
        while (clouds.size() < 4 && retryCount < 100) {
            retryCount++;
            int x = GameRandom.nextInt(maxX);
            int y = GameRandom.nextInt(maxY);
            if (clouds.isEmpty()) {
                clouds.add(new Pair(Integer.valueOf(x), Integer.valueOf(y)));
                retryCount = 0;
            } else {
                boolean isIntercept = false;
                for (Pair<Integer, Integer> cloud : clouds) {
                    if (Math.abs(x - ((Integer) cloud.first).intValue()) <= this.cloudSize && Math.abs(y - ((Integer) cloud.second).intValue()) <= this.cloudSize) {
                        isIntercept = true;
                        break;
                    }
                }
                if (!isIntercept) {
                    clouds.add(new Pair(Integer.valueOf(x), Integer.valueOf(y)));
                    retryCount = 0;
                }
            }
        }
        this.clouds = clouds;
        return clouds;
    }

    public boolean onTouchEvent(MotionEvent event) {
        if (this.enableClick) {
            return this.gestureDetector.onTouchEvent(event);
        }
        return super.onTouchEvent(event);
    }

    private void notifySuccessCellClicked(MotionEvent e) {
        this.hintPosition = new PointF(e.getX(), e.getY());
        if (this.gridEventsListener != null) {
            this.gridEventsListener.onSuccessCellClicked(0);
        }
    }

    public PointF getHintPosition() {
        return this.hintPosition;
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

    public void showChallengeCells() {
        invalidate();
    }

    public void hideChallengeCells() {
        invalidate();
    }

    public void buildGrid() {
    }

    public void clearWrongCells() {
    }

    public void disableAllCells() {
        this.enableClick = false;
        this.viewHandler.removeCallbacks(this.updateView);
        this.stoppedTime = System.currentTimeMillis();
        invalidate();
    }

    public void enableAllCells() {
        this.enableClick = true;
        invalidate();
    }

    public int getCurrentSuccessCellsClicked() {
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

    public void animateCells() {
    }

    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        this.viewHandler.removeCallbacks(this.updateView);
    }

    public void animateFinishCells() {
    }
}
