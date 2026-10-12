package com.cube.memorygames.logic;

import com.cube.memorygames.Progression;

public class GameProgression4 implements Progression {
    private static final int START_GRID_SIZE = 10;
    private static final int START_WIN_CELLS = 1;
    private int currentGridSize = 10;
    private int currentWinCells = 1;
    private int levelNumber = 0;

    public void startGame() {
        this.currentGridSize = 10;
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
    }
}
