package com.cube.memorygames.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import com.memory.brain.training.games.R;
import com.mopub.mobileads.resource.DrawableConstants.CloseButton;
import com.mopub.volley.DefaultRetryPolicy;
import java.util.ArrayList;
import java.util.List;

public class Game18Grid extends View implements GameGrid {
    private static final int STATE_GAME = 1;
    private static final int STATE_LOSE = 3;
    private static final int STATE_WIN = 2;
    private List<List<Integer>> colors;
    private int correctCircle;
    private boolean enableClick;
    private int failCircle = -1;
    private GridEventsListener gridEventsListener;
    private Paint mainPaint;
    private List<Pair<Float, Float>> positions;
    private Float radius;
    private Bitmap rightAnswer;
    private float shadowOffset;
    private float shadowRadius;
    private boolean showChallengeCells;
    private List<Float> sizes;
    private int state = 0;
    private Paint textPaint;
    private Bitmap wrongAnswer;

    public Game18Grid(Context context) {
        super(context);
        init();
    }

    public Game18Grid(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public Game18Grid(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        this.mainPaint = new Paint();
        this.mainPaint.setStyle(Style.FILL_AND_STROKE);
        this.mainPaint.setAntiAlias(true);
        setLayerType(1, this.mainPaint);
        this.textPaint = new Paint();
        this.textPaint.setStyle(Style.FILL_AND_STROKE);
        this.textPaint.setAntiAlias(true);
        this.textPaint.setColor(-16777216);
        this.textPaint.setTypeface(Typeface.createFromAsset(getContext().getAssets(), "Roboto-Light.ttf"));
        this.textPaint.setTextAlign(Align.CENTER);
        this.textPaint.setTextSize(getResources().getDimension(R.dimen.text_big));
        this.enableClick = false;
        this.showChallengeCells = false;
        this.wrongAnswer = BitmapFactory.decodeResource(getResources(), R.drawable.wronganswer);
        this.rightAnswer = BitmapFactory.decodeResource(getResources(), R.drawable.rightanswer);
        this.shadowRadius = getResources().getDimension(R.dimen.game18_shadow_radius);
        this.shadowOffset = getResources().getDimension(R.dimen.game18_shadow_offset);
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

    public void setElements(List<List<Integer>> colors, List<Float> sizes, int correctCircle) {
        this.colors = colors;
        this.sizes = sizes;
        this.correctCircle = correctCircle;
        this.state = 1;
        this.failCircle = -1;
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
        if (this.positions == null || !this.enableClick || event.getAction() != 0 || this.state != 1) {
            return false;
        }
        for (int i = 0; i < this.positions.size(); i++) {
            Pair<Float, Float> pair = (Pair) this.positions.get(i);
            float side1 = ((Float) pair.first).floatValue() - event.getX();
            float side2 = ((Float) pair.second).floatValue() - event.getY();
            if (((float) Math.sqrt((double) ((side1 * side1) + (side2 * side2)))) <= this.radius.floatValue()) {
                if (this.correctCircle == i) {
                    this.state = 2;
                    notifySuccessCellClicked(i);
                } else {
                    this.state = 3;
                    this.failCircle = i;
                    notifyFailCellClicked();
                }
                invalidate();
            }
        }
        return true;
    }

    @SuppressLint({"DrawAllocation"})
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (this.showChallengeCells || this.enableClick || this.state == 2 || this.state == 3 || width <= 0 || height <= 0) {
            float padding = (float) getResources().getDimensionPixelSize(R.dimen.padding_larger);
            Canvas canvas2 = canvas;
            drawPyramid(canvas2, (List) this.colors.get(this.correctCircle), this.sizes, padding, padding / 2.0f, ((float) width) - (2.0f * padding), ((float) height) * 0.2f);
            canvas.drawText(getResources().getString(R.string.choose_answer), (float) (width / 2), (((float) height) * 0.2f) + (1.5f * padding), this.textPaint);
            this.radius = Float.valueOf(((float) width) * 0.175f);
            this.positions = new ArrayList();
            this.positions.add(new Pair(Float.valueOf(((float) width) * 0.25f), Float.valueOf(((float) height) * 0.5f)));
            this.positions.add(new Pair(Float.valueOf(((float) width) * 0.75f), Float.valueOf(((float) height) * 0.5f)));
            this.positions.add(new Pair(Float.valueOf(((float) width) * 0.5f), Float.valueOf(((float) height) * 0.75f)));
            int i = 0;
            while (i < this.positions.size()) {
                Pair<Float, Float> pair = (Pair) this.positions.get(i);
                drawCircle(canvas, (List) this.colors.get(i), this.sizes, ((Float) pair.first).floatValue(), ((Float) pair.second).floatValue(), this.radius.floatValue());
                float left;
                float top;
                float right;
                float bottom;
                if (this.state == 3 && this.failCircle == i) {
                    left = ((Float) pair.first).floatValue() - ((float) (this.wrongAnswer.getWidth() / 2));
                    top = ((Float) pair.second).floatValue() - ((float) (this.wrongAnswer.getHeight() / 2));
                    right = ((Float) pair.first).floatValue() + ((float) (this.wrongAnswer.getWidth() / 2));
                    bottom = ((Float) pair.second).floatValue() + ((float) (this.wrongAnswer.getHeight() / 2));
                    canvas.drawBitmap(this.wrongAnswer, null, new RectF(left, top, right, bottom), null);
                } else if (this.correctCircle == i && this.state == 2) {
                    left = ((Float) pair.first).floatValue() - ((float) (this.wrongAnswer.getWidth() / 2));
                    top = ((Float) pair.second).floatValue() - ((float) (this.wrongAnswer.getHeight() / 2));
                    right = ((Float) pair.first).floatValue() + ((float) (this.wrongAnswer.getWidth() / 2));
                    bottom = ((Float) pair.second).floatValue() + ((float) (this.wrongAnswer.getHeight() / 2));
                    canvas.drawBitmap(this.rightAnswer, null, new RectF(left, top, right, bottom), null);
                }
                i++;
            }
        }
    }

    private void drawCircle(Canvas canvas, List<Integer> colors, List<Float> sizes, float centerX, float centerY, float radius) {
        this.mainPaint.setShadowLayer(this.shadowRadius, this.shadowOffset, this.shadowOffset, -1442840576);
        for (int i = 0; i < colors.size(); i++) {
            this.mainPaint.setColor(((Integer) colors.get(i)).intValue());
            canvas.drawCircle(centerX, centerY, ((Float) sizes.get(i)).floatValue() * radius, this.mainPaint);
        }
    }

    private void drawPyramid(Canvas canvas, List<Integer> colors, List<Float> sizes, float left, float top, float width, float height) {
        float stepHeight = height / CloseButton.STROKE_WIDTH;
        float stepHeightCorner = stepHeight * 0.2f;
        top += (height - (((float) sizes.size()) * stepHeight)) / 2.0f;
        this.mainPaint.clearShadowLayer();
        for (int i = 0; i < colors.size(); i++) {
            this.mainPaint.setColor(((Integer) colors.get(i)).intValue());
            canvas.drawRoundRect(new RectF(left + (((DefaultRetryPolicy.DEFAULT_BACKOFF_MULT - ((Float) sizes.get(i)).floatValue()) * width) / 2.0f), top + (((float) ((sizes.size() - i) - 1)) * stepHeight), (((Float) sizes.get(i)).floatValue() * width) + (left + (((DefaultRetryPolicy.DEFAULT_BACKOFF_MULT - ((Float) sizes.get(i)).floatValue()) * width) / 2.0f)), (((float) (sizes.size() - i)) * stepHeight) + top), stepHeightCorner, stepHeightCorner, this.mainPaint);
        }
    }

    public void animateCells() {
    }

    public void animateFinishCells() {
    }
}
