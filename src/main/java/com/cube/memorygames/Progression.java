package com.cube.memorygames;

public interface Progression {
    int getCurrentGridSize();

    int getCurrentWinCells();

    int getLevelNumber();

    void nextLevel();

    void startGame();
}
