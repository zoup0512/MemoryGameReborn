package com.cube.memorygames.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class Game16FigureView extends View {
    private Element element;
    private Paint mainPaint;

    public Game16FigureView(Context context) {
        super(context);
        init();
    }

    public Game16FigureView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public Game16FigureView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        this.mainPaint = new Paint();
        this.mainPaint.setStyle(Style.FILL_AND_STROKE);
    }

    public void setElement(Element element) {
        this.element = element;
        invalidate();
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (this.element != null && width > 0 && height > 0) {
            float centerX = (float) (width / 2);
            float centerY = (float) (height / 2);
            float radius = (float) (Math.min(width, height) / 2);
            float left = centerX - radius;
            float right = centerX + radius;
            float top = centerY - radius;
            float bottom = centerY + radius;
            this.mainPaint.setColor(this.element.getColor());
            switch (this.element.getFigure()) {
                case 0:
                    Path pentagonPath = new Path();
                    pentagonPath.moveTo((float) (((double) centerX) + (((double) radius) * Math.cos(Math.toRadians(-18.0d)))), (float) (((double) centerY) + (((double) radius) * Math.sin(Math.toRadians(-18.0d)))));
                    pentagonPath.lineTo((float) (((double) centerX) + (((double) radius) * Math.cos(Math.toRadians(54.0d)))), (float) (((double) centerY) + (((double) radius) * Math.sin(Math.toRadians(54.0d)))));
                    pentagonPath.lineTo((float) (((double) centerX) + (((double) radius) * Math.cos(Math.toRadians(126.0d)))), (float) (((double) centerY) + (((double) radius) * Math.sin(Math.toRadians(126.0d)))));
                    pentagonPath.lineTo((float) (((double) centerX) + (((double) radius) * Math.cos(Math.toRadians(198.0d)))), (float) (((double) centerY) + (((double) radius) * Math.sin(Math.toRadians(198.0d)))));
                    pentagonPath.lineTo((float) (((double) centerX) + (((double) radius) * Math.cos(Math.toRadians(270.0d)))), (float) (((double) centerY) + (((double) radius) * Math.sin(Math.toRadians(270.0d)))));
                    pentagonPath.lineTo((float) (((double) centerX) + (((double) radius) * Math.cos(Math.toRadians(-18.0d)))), (float) (((double) centerY) + (((double) radius) * Math.sin(Math.toRadians(-18.0d)))));
                    pentagonPath.close();
                    canvas.drawPath(pentagonPath, this.mainPaint);
                    return;
                case 1:
                    Path path1 = new Path();
                    path1.moveTo(left, top + radius);
                    path1.lineTo(left + radius, top);
                    path1.lineTo(right, top + radius);
                    path1.lineTo(left + radius, bottom);
                    path1.lineTo(left, top + radius);
                    path1.close();
                    canvas.drawPath(path1, this.mainPaint);
                    return;
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
                    return;
                case 3:
                    float side = (float) Math.sqrt((double) ((radius * radius) / 2.0f));
                    Path path2 = new Path();
                    path2.moveTo(centerX - side, centerY - side);
                    path2.lineTo(centerX + side, centerY - side);
                    path2.lineTo(centerX + side, centerY + side);
                    path2.lineTo(centerX - side, centerY + side);
                    path2.lineTo(centerX - side, centerY - side);
                    path2.close();
                    canvas.drawPath(path2, this.mainPaint);
                    return;
                case 4:
                    Path trianglePath = new Path();
                    trianglePath.moveTo((float) (((double) centerX) + (((double) radius) * Math.cos(Math.toRadians(-90.0d)))), (float) (((double) centerY) + (((double) radius) * Math.sin(Math.toRadians(-90.0d)))));
                    trianglePath.lineTo((float) (((double) centerX) + (((double) radius) * Math.cos(Math.toRadians(30.0d)))), (float) (((double) centerY) + (((double) radius) * Math.sin(Math.toRadians(30.0d)))));
                    trianglePath.lineTo((float) (((double) centerX) + (((double) radius) * Math.cos(Math.toRadians(150.0d)))), (float) (((double) centerY) + (((double) radius) * Math.sin(Math.toRadians(150.0d)))));
                    trianglePath.lineTo((float) (((double) centerX) + (((double) radius) * Math.cos(Math.toRadians(-90.0d)))), (float) (((double) centerY) + (((double) radius) * Math.sin(Math.toRadians(-90.0d)))));
                    trianglePath.close();
                    canvas.drawPath(trianglePath, this.mainPaint);
                    return;
                case 5:
                    float margin = radius * 0.1f;
                    canvas.drawOval(new RectF(left + margin, top + margin, right - margin, bottom - margin), this.mainPaint);
                    return;
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
                    canvas.drawPath(path3, this.mainPaint);
                    canvas.drawPath(path4, this.mainPaint);
                    return;
                default:
                    return;
            }
        }
    }
}
