package com.cube.memorygames.ui.hex.polygon;

public class Line {
    private float _a = Float.NaN;
    private float _b = Float.NaN;
    private final Point _end;
    private final Point _start;
    private boolean _vertical = false;

    public Line(Point start, Point end) {
        this._start = start;
        this._end = end;
        if (this._end.x - this._start.x != 0.0f) {
            this._a = (this._end.y - this._start.y) / (this._end.x - this._start.x);
            this._b = this._start.y - (this._a * this._start.x);
            return;
        }
        this._vertical = true;
    }

    public boolean isInside(Point point) {
        float maxX = this._start.x > this._end.x ? this._start.x : this._end.x;
        float minX = this._start.x < this._end.x ? this._start.x : this._end.x;
        float maxY = this._start.y > this._end.y ? this._start.y : this._end.y;
        float minY = this._start.y < this._end.y ? this._start.y : this._end.y;
        if (point.x < minX || point.x > maxX || point.y < minY || point.y > maxY) {
            return false;
        }
        return true;
    }

    public boolean isVertical() {
        return this._vertical;
    }

    public float getA() {
        return this._a;
    }

    public float getB() {
        return this._b;
    }

    public Point getStart() {
        return this._start;
    }

    public Point getEnd() {
        return this._end;
    }

    public String toString() {
        return String.format("%s-%s", new Object[]{this._start.toString(), this._end.toString()});
    }
}
