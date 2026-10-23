package com.cube.memorygames.ui.grids;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Join;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.os.Build.VERSION;
import androidx.core.view.GestureDetectorCompat;
import android.util.Log;
import android.view.GestureDetector.OnGestureListener;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.GridLayout.LayoutParams;
import android.widget.ToggleButton;
import com.cube.memorygames.ui.GameGrid;
import com.cube.memorygames.ui.GridEventsListener;
import com.cube.memorygames.ui.RotationCompletedListener;
import com.github.florent37.viewanimator.AnimationListener.Stop;
import com.github.florent37.viewanimator.ViewAnimator;
import com.memory.brain.training.games.R;
import com.mopub.volley.DefaultRetryPolicy;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Stack;

public class TwoFoldGrid extends GridLayout implements GameGrid, OnGestureListener {
    private static final float DIVIDER_COEFFICIENT = 0.1f;
    private static final int[] cellDrawables = new int[]{R.drawable.background_cell_twofold1, R.drawable.background_cell_twofold2, R.drawable.background_cell_twofold3, R.drawable.background_cell_twofold4};
    private static final int[] cellColors = new int[cellDrawables.length];
    private OnClickListener arrowDownClickListener = new OnClickListener() {
        public void onClick(View view) {
            TwoFoldGrid.this.moveColumnDown(((Integer) view.getTag()).intValue());
        }
    };
    private OnClickListener arrowLeftClickListener = new OnClickListener() {
        public void onClick(View view) {
            TwoFoldGrid.this.moveRowLeft(((Integer) view.getTag()).intValue());
        }
    };
    private OnClickListener arrowRightClickListener = new OnClickListener() {
        public void onClick(View view) {
            TwoFoldGrid.this.moveRowRight(((Integer) view.getTag()).intValue());
        }
    };
    private OnClickListener arrowUpClickListener = new OnClickListener() {
        public void onClick(View view) {
            TwoFoldGrid.this.moveColumnUp(((Integer) view.getTag()).intValue());
        }
    };
    private View[][] buttons;
    private GridEventsListener gridEventsListener;
    private final int height;
    private GestureDetectorCompat mDetector;
    private SimpleOnGestureListener mGestureListener = new SimpleOnGestureListener() {
        public boolean onDown(MotionEvent e) {
            return true;
        }

        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            View button2 = TwoFoldGrid.this.getChildAt(e2);
            View button1 = TwoFoldGrid.this.getChildAt(e1);
            if (button2 != null) {
                TwoFoldGrid.this.addCellToSequence(button2);
                TwoFoldGrid.this.repaintGrid();
            }
            return true;
        }

        public void onLongPress(MotionEvent e) {
            View button = TwoFoldGrid.this.getChildAt(e);
            if (button != null) {
                button.setBackgroundColor(-16711936);
            }
        }
    };
    private OnSequenceListener onSequenceListener;
    private final Random random;
    private Stack<View> sequence = new Stack();
    private Paint sequencePaint;
    private final int width;

    public interface OnSequenceListener {
        void onSequenceFinished();

        void onSequenceSizeChanged(int i);
    }

    public TwoFoldGrid(Context context, int width, int height) {
        super(context);
        this.width = width;
        this.height = height;
        this.random = new Random(System.currentTimeMillis());
        this.mDetector = new GestureDetectorCompat(getContext(), this.mGestureListener);
        this.mDetector.setIsLongpressEnabled(false);
        setOnSequenceListener(new OnSequenceListener() {
            public void onSequenceSizeChanged(int size) {
            }

            public void onSequenceFinished() {
                Log.e("SNAKE", "onSequenceFinished()");
                if (TwoFoldGrid.this.sequence.size() > 1) {
                    TwoFoldGrid.this.clearSequenceAndGenerateCells();
                } else {
                    if (TwoFoldGrid.this.sequence.size() == 1) {
                        ((View) TwoFoldGrid.this.sequence.get(0)).setBackgroundResource(((Integer) ((View) TwoFoldGrid.this.sequence.get(0)).getTag(R.id.tag_cell_drawable)).intValue());
                    }
                    TwoFoldGrid.this.sequence.clear();
                }
                TwoFoldGrid.this.repaintGrid();
            }
        });
        this.sequencePaint = new Paint();
        this.sequencePaint.setColor(Color.parseColor("#5C6BC0"));
        this.sequencePaint.setStyle(Style.STROKE);
        this.sequencePaint.setStrokeWidth((float) getResources().getDimensionPixelSize(R.dimen.game_248_sequence_line_thickness));
        this.sequencePaint.setStrokeJoin(Join.ROUND);
        initColors();
    }

    private void initColors() {
        cellColors[0] = getResources().getColor(R.color.game_248_color_1);
        cellColors[1] = getResources().getColor(R.color.game_248_color_2);
        cellColors[2] = getResources().getColor(R.color.game_248_color_3);
        cellColors[3] = getResources().getColor(R.color.game_248_color_4);
    }

    private void clearSequenceAndGenerateCells() {
        List<View> copyList = new ArrayList(this.sequence);
        final View[] viewArray = new View[copyList.size()];
        this.sequence.clear();
        ViewAnimator.animate((View[]) copyList.toArray(viewArray)).fadeOut().duration(300).onStop(new Stop() {
            public void onStop() {
                for (View cell : viewArray) {
                    TwoFoldGrid.this.generateAndSetupCell(cell);
                    cell.setAlpha(DefaultRetryPolicy.DEFAULT_BACKOFF_MULT);
                }
            }
        }).start();
    }

    private void generateAndSetupCell(View cell) {
        setupCell(cell, this.random.nextInt(cellDrawables.length));
    }

    private void setupCell(View cell, int cellValue) {
        cell.setBackgroundResource(cellDrawables[cellValue]);
        cell.setTag(R.id.tag_cell_drawable, Integer.valueOf(cellDrawables[cellValue]));
        cell.setTag(R.id.tag_cell_color, Integer.valueOf(cellColors[cellValue]));
        cell.setTag(R.id.tag_cell_value, Integer.valueOf(cellValue));
    }

    public void setOnSequenceListener(OnSequenceListener onSequenceListener) {
        this.onSequenceListener = onSequenceListener;
    }

    public boolean onTouchEvent(MotionEvent event) {
        boolean detectedUp = event.getAction() == 1;
        if (!this.mDetector.onTouchEvent(event) && detectedUp) {
            notifySequenceFinished();
        }
        return true;
    }

    public void hideChallengeCells() {
    }

    public void showChallengeCells() {
    }

    public void disableAllCells() {
    }

    public void enableAllCells() {
    }

    public int getCurrentSuccessCellsClicked() {
        return 0;
    }

    public int getSuccessCells() {
        return this.sequence.size();
    }

    public void buildGrid() {
        setColumnCount(8);
        setRowCount(8);
        float cellSizeFloat = ((float) this.width) / 8.9f;
        int cellMargin = (int) (DIVIDER_COEFFICIENT * cellSizeFloat);
        int cellSize = (int) cellSizeFloat;
        int mainMargins = (this.width - ((cellSize * 8) + ((cellMargin / 2) * 16))) / 2;
        setPadding(mainMargins, mainMargins, mainMargins, mainMargins);
        this.buttons = (View[][]) Array.newInstance(View.class, new int[]{8, 8});
        int i = 0;
        while (i < 8) {
            int j = 0;
            while (j < 8) {
                View button;
                LayoutParams cellParams = new LayoutParams();
                cellParams.columnSpec = GridLayout.spec(i);
                cellParams.rowSpec = GridLayout.spec(j);
                cellParams.height = cellSize;
                cellParams.width = cellSize;
                cellParams.setMargins(cellMargin / 2, cellMargin / 2, cellMargin / 2, cellMargin / 2);
                if (i == 0 || j == 0 || i == 7 || j == 7) {
                    button = createArrowButton(cellSize, i, j);
                } else {
                    button = createCell(cellSize, i, j);
                }
                addView(button, cellParams);
                this.buttons[i][j] = button;
                j++;
            }
            i++;
        }
    }

    private View createArrowButton(int cellSize, int i, int j) {
        Button button = new Button(getContext());
        button.setHeight(cellSize);
        button.setWidth(cellSize);
        button.setText("");
        button.setBackgroundResource(0);
        if (!((i == 0 && j == 0) || ((i == 0 && j == 7) || ((i == 7 && j == 0) || (i == 7 && j == 7))))) {
            if (i == 0) {
                button.setBackgroundResource(R.drawable.left2);
                button.setOnClickListener(this.arrowLeftClickListener);
                button.setTag(Integer.valueOf(j));
            }
            if (j == 0) {
                button.setBackgroundResource(R.drawable.up2);
                button.setOnClickListener(this.arrowUpClickListener);
                button.setTag(Integer.valueOf(i));
            }
            if (i == 7) {
                button.setBackgroundResource(R.drawable.right2);
                button.setOnClickListener(this.arrowRightClickListener);
                button.setTag(Integer.valueOf(j));
            }
            if (j == 7) {
                button.setBackgroundResource(R.drawable.down2);
                button.setOnClickListener(this.arrowDownClickListener);
                button.setTag(Integer.valueOf(i));
            }
        }
        return button;
    }

    protected Button createCell(int cellSize, int x, int y) {
        ToggleButton button = new ToggleButton(getContext());
        button.setText("");
        button.setTextOff("");
        button.setTextOn("");
        button.setHeight(cellSize);
        button.setWidth(cellSize);
        button.setSoundEffectsEnabled(false);
        generateAndSetupCell(button);
        button.setStateListAnimator(null);
        button.setTag(R.id.tag_cell_x, Integer.valueOf(x));
        button.setTag(R.id.tag_cell_y, Integer.valueOf(y));
        return button;
    }

    public void clearWrongCells() {
    }

    public void rotateGrid(int angle, int duration, View thumb, RotationCompletedListener rotationCompletedListener) {
    }

    public int addSuccessCell() {
        return 0;
    }

    public void enableSuccessCells() {
    }

    public void hideAllCells() {
    }

    public void setDrawableIdsToUse(List<Integer> list) {
    }

    public void setDrawablesToUse(List<Drawable> list) {
    }

    public void setUserEachDrawableOnlyOnce(boolean userEachDrawableOnlyOnce) {
    }

    public void changeSuccessDrawable(int drawableResId) {
    }

    public void setCellTypes(int type) {
    }

    public void animateCells() {
    }

    private void replaceCells(View targetCell, View sourceCell) {
        targetCell.setBackgroundDrawable(sourceCell.getBackground());
        targetCell.setTag(R.id.tag_cell_drawable, sourceCell.getTag(R.id.tag_cell_drawable));
        targetCell.setTag(R.id.tag_cell_color, sourceCell.getTag(R.id.tag_cell_color));
        targetCell.setTag(R.id.tag_cell_value, sourceCell.getTag(R.id.tag_cell_value));
    }

    private void moveRowRight(int rowNumber) {
        int oldCellValue = ((Integer) this.buttons[6][rowNumber].getTag(R.id.tag_cell_value)).intValue();
        for (int i = 6; i > 0; i--) {
            View button = this.buttons[i][rowNumber];
            if (i == 1) {
                setupCell(button, oldCellValue);
            } else {
                replaceCells(button, this.buttons[i - 1][rowNumber]);
            }
        }
    }

    private void moveRowLeft(int rowNumber) {
        int oldCellValue = ((Integer) this.buttons[1][rowNumber].getTag(R.id.tag_cell_value)).intValue();
        for (int i = 1; i < 7; i++) {
            View button = this.buttons[i][rowNumber];
            if (i == 6) {
                setupCell(button, oldCellValue);
            } else {
                replaceCells(button, this.buttons[i + 1][rowNumber]);
            }
        }
    }

    private void moveColumnUp(int columnNumber) {
        int oldCellValue = ((Integer) this.buttons[columnNumber][1].getTag(R.id.tag_cell_value)).intValue();
        for (int j = 1; j < 7; j++) {
            View button = this.buttons[columnNumber][j];
            if (j == 6) {
                setupCell(button, oldCellValue);
            } else {
                replaceCells(button, this.buttons[columnNumber][j + 1]);
            }
        }
    }

    private void moveColumnDown(int columnNumber) {
        int oldCellValue = ((Integer) this.buttons[columnNumber][6].getTag(R.id.tag_cell_value)).intValue();
        for (int j = 6; j > 0; j--) {
            View button = this.buttons[columnNumber][j];
            if (j == 1) {
                setupCell(button, oldCellValue);
            } else {
                replaceCells(button, this.buttons[columnNumber][j - 1]);
            }
        }
    }

    public boolean onDown(MotionEvent motionEvent) {
        return true;
    }

    public void onShowPress(MotionEvent motionEvent) {
    }

    public boolean onSingleTapUp(MotionEvent motionEvent) {
        return false;
    }

    public boolean onScroll(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1) {
        getChildAt(motionEvent1).setBackgroundColor(Color.RED);
        return true;
    }

    public void onLongPress(MotionEvent motionEvent) {
    }

    public boolean onFling(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1) {
        return false;
    }

    private View getChildAt(MotionEvent event) {
        if (event == null) {
            return null;
        }
        float x = event.getX();
        float y = event.getY();
        int cc = getChildCount();
        for (int i = 0; i < cc; i++) {
            View c = getChildAt(i);
            if (x > ((float) c.getLeft()) && x < ((float) c.getRight()) && y < ((float) c.getBottom()) && y > ((float) c.getTop()) && (c instanceof ToggleButton)) {
                return c;
            }
        }
        return null;
    }

    public boolean onInterceptTouchEvent(MotionEvent ev) {
        boolean detectedUp = false;
        View button = getChildAt(ev);
        if (button == null || button.getTag() != null) {
            return false;
        }
        if (ev.getAction() == 1) {
            detectedUp = true;
        }
        if (!this.mDetector.onTouchEvent(ev) && detectedUp) {
            notifySequenceFinished();
        }
        return true;
    }

    private void addCellToSequence(View cell) {
        if (this.sequence.isEmpty()) {
            this.sequence.push(cell);
            notifySequenceSizeChanged();
            return;
        }
        View previousCell = (View) this.sequence.peek();
        if (cell != previousCell && previousCell.getTag(R.id.tag_cell_drawable).equals(cell.getTag(R.id.tag_cell_drawable))) {
            if (this.sequence.contains(cell)) {
                while (this.sequence.peek() != cell) {
                    View pop = (View) this.sequence.pop();
                    notifySequenceSizeChanged();
                }
                return;
            }
            int previousCellX = ((Integer) previousCell.getTag(R.id.tag_cell_x)).intValue();
            int previousCellY = ((Integer) previousCell.getTag(R.id.tag_cell_y)).intValue();
            int cellX = ((Integer) cell.getTag(R.id.tag_cell_x)).intValue();
            int cellY = ((Integer) cell.getTag(R.id.tag_cell_y)).intValue();
            if ((cellX == previousCellX || cellY == previousCellY) && Math.abs(cellX - previousCellX) <= 1 && Math.abs(cellY - previousCellY) <= 1) {
                this.sequence.push(cell);
                notifySequenceSizeChanged();
            }
        }
    }

    private void notifySequenceSizeChanged() {
        if (this.onSequenceListener != null) {
            this.onSequenceListener.onSequenceSizeChanged(this.sequence.size());
        }
        notifySuccessCellClicked(this.sequence.size());
    }

    private void notifySequenceFinished() {
        if (this.onSequenceListener != null) {
            this.onSequenceListener.onSequenceFinished();
        }
        notifyFailCellClicked();
    }

    private void repaintGrid() {
        for (int i = 1; i < 7; i++) {
            for (int j = 1; j < 7; j++) {
                this.buttons[i][j].setBackgroundResource(((Integer) this.buttons[i][j].getTag(R.id.tag_cell_drawable)).intValue());
            }
        }
        Iterator it = this.sequence.iterator();
        while (it.hasNext()) {
            ((View) it.next()).setBackgroundResource(0);
        }
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
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (int i = 0; i < this.sequence.size() - 1; i++) {
            View cell = (View) this.sequence.get(i);
            View nextCell = (View) this.sequence.get(i + 1);
            canvas.drawLine(cell.getX(), cell.getY(), nextCell.getX(), nextCell.getY(), this.sequencePaint);
        }
    }

    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (this.sequence.size() > 0) {
            Path path = new Path();
            this.sequencePaint.setColor(((Integer) ((View) this.sequence.get(0)).getTag(R.id.tag_cell_color)).intValue());
            for (int i = 0; i < this.sequence.size(); i++) {
                View cell = (View) this.sequence.get(i);
                if (i == 0) {
                    path.moveTo(cell.getX() + ((float) (cell.getWidth() / 2)), cell.getY() + ((float) (cell.getHeight() / 2)));
                } else {
                    path.lineTo(cell.getX() + ((float) (cell.getWidth() / 2)), cell.getY() + ((float) (cell.getHeight() / 2)));
                }
            }
            canvas.drawPath(path, this.sequencePaint);
        }
    }

    public void animateFinishCells() {
    }
}
