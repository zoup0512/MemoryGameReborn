package com.cube.memorygames.logic;

import com.cube.memorygames.Progression;

public class GameProgression2 implements Progression {
    private static final int START_GRID_SIZE = 2;
    private static final int START_WIN_CELLS = 1;
    private int currentGridSize = 2;
    private int currentWinCells = 1;
    private int levelNumber = 0;

    public void startGame() {
        this.currentGridSize = 2;
        this.currentWinCells = 1;
        this.levelNumber = 1;
    }

    public int getCurrentGridSize() {
        return this.currentGridSize;
    }

    public int getCurrentWinCells() {
        return this.currentWinCells;
    }

    public int getLevelNumber() {
        return this.levelNumber;
    }

    public void nextLevel() {
        this.levelNumber++;
        switch (this.levelNumber) {
            case 1:
                this.currentGridSize = 2;
                this.currentWinCells = 1;
                return;
            case 2:
                this.currentGridSize = 2;
                this.currentWinCells = 2;
                return;
            case 3:
                this.currentGridSize = 3;
                this.currentWinCells = 1;
                return;
            case 4:
                this.currentGridSize = 3;
                this.currentWinCells = 2;
                return;
            default:
                if (this.levelNumber >= 8 && this.levelNumber % 2 == 1) {
                    return;
                }
                if (((double) (((float) (this.currentWinCells + 1)) / ((float) (this.currentGridSize * this.currentGridSize)))) >= 0.35d) {
                    this.currentGridSize++;
                    return;
                } else {
                    this.currentWinCells++;
                    return;
                }
        }
    }
}
