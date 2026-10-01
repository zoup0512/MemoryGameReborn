package com.cube.memorygames.logic;

import com.cube.memorygames.Progression;

public class GameProgression21 implements Progression {
    private int levelNumber = 0;

    public void startGame() {
        this.levelNumber = 1;
    }

    public int getCurrentGridSize() {
        return 0;
    }

    public int getCurrentWinCells() {
        switch (this.levelNumber) {
            case 1:
                return 2;
            case 2:
                return 3;
            case 3:
                return 4;
            case 4:
            case 5:
                return 5;
            case 6:
                return 6;
            case 7:
                return 7;
            case 8:
                return 8;
            case 9:
                return 8;
            case 10:
                return 9;
            case 11:
                return 9;
            case 12:
                return 10;
            case 13:
                return 11;
            case 14:
                return 11;
            case 15:
                return 12;
            default:
                return 13;
        }
    }

    public int getLevelNumber() {
        return this.levelNumber;
    }

    public void nextLevel() {
        this.levelNumber++;
    }
}
