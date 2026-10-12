package com.cube.memorygames.ui;

public class Element {
    public Element() {
    }

    private int color;
    private int figure;
    private boolean isWin;

    public Element(int color, int figure) {
        this.color = color;
        this.figure = figure;
    }

    public int getColor() {
        return this.color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public int getFigure() {
        return this.figure;
    }

    public void setFigure(int figure) {
        this.figure = figure;
    }

    public String toString() {
        if (this.isWin) {
            return this.color + " - " + this.figure + "(Win)";
        }
        return this.color + " - " + this.figure;
    }

    public boolean isWin() {
        return this.isWin;
    }

    public void setWin(boolean win) {
        this.isWin = win;
    }
}
