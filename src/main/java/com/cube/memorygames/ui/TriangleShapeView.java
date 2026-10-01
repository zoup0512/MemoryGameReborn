package com.cube.memorygames.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.View;
import com.memory.brain.training.games.R;

public class TriangleShapeView extends View {
    Paint paint;

    public TriangleShapeView(Context context) {
        super(context);
        init();
    }

    public TriangleShapeView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public TriangleShapeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        this.paint = new Paint();
        this.paint.setColor(ContextCompat.getColor(getContext(), R.color.tab2));
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        Path path = new Path();
        path.moveTo((float) w, 0.0f);
        path.lineTo(0.0f, 0.0f);
        path.lineTo((float) w, (float) w);
        path.lineTo((float) w, 0.0f);
        path.close();
        canvas.drawPath(path, this.paint);
    }
}
