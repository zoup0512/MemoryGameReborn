package com.cube.memorygames.ui.hex.polygon;

public class Point {
    public float x;
    public float y;

    public Point(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public String toString() {
        return String.format("(%.2f,%.2f)", new Object[]{Float.valueOf(this.x), Float.valueOf(this.y)});
    }
}
