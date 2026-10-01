package com.cube.memorygames.logic;

import com.cube.memorygames.Progression;

public class GameProgression9 implements Progression {
    private static final int START_GRID_SIZE = 2;
    private static final int START_WIN_CELLS = 2;
    private int currentGridSize = 2;
    private int currentWinCells = 2;
    private int levelNumber = 0;

    public void startGame() {
        this.currentGridSize = 2;
        this.currentWinCells = 2;
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
        if (this.currentWinCells >= this.currentGridSize * this.currentGridSize) {
            this.currentGridSize++;
        }
        this.currentWinCells++;
    }
}
