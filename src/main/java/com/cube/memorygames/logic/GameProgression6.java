package com.cube.memorygames.logic;

import com.cube.memorygames.Progression;

public class GameProgression6 implements Progression {
    private static final int START_GRID_SIZE = 3;
    private static final int START_WIN_CELLS = 2;
    private int currentGridSize = 3;
    private int currentWinCells = 2;
    private int levelNumber = 0;

    public void startGame() {
        this.currentGridSize = 3;
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
        if (this.levelNumber >= 6 && this.levelNumber % 2 == 1) {
            return;
        }
        if (((double) (((float) (this.currentWinCells + 1)) / ((float) (this.currentGridSize * this.currentGridSize)))) >= 0.35d) {
            this.currentGridSize++;
        } else {
            this.currentWinCells++;
        }
    }
}
