package com.cube.memorygames.logic;

import com.cube.memorygames.Progression;

public class GameProgression4Challenge implements Progression {
    private static final int START_GRID_SIZE = 5;
    private static final int START_WIN_CELLS = 1;
    private int currentGridSize = 5;
    private int currentWinCells = 1;
    private int levelNumber = 0;

    public void startGame() {
        this.currentGridSize = 5;
        this.levelNumber = 1;
        this.currentWinCells = this.levelNumber;
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
        this.currentGridSize = ((this.levelNumber - 1) / 6) + 5;
        this.currentWinCells = (this.levelNumber + 1) / 2;
    }
}
