package com.cube.memorygames.logic;

import com.cube.memorygames.Progression;

public class GameProgression7 implements Progression {
    private int currentGridSize = 6;
    private int currentWinCells = 1;
    private int levelNumber = 0;

    public void startGame() {
        this.currentGridSize = 6;
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
        this.currentWinCells++;
    }
}
