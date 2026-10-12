package com.cube.memorygames.api.local.workout;

import com.cube.memorygames.model.GameInfo;

public class LevelInfo {
    private GameInfo gameInfo;
    private int level;

    public LevelInfo(GameInfo gameInfo, int level) {
        this.gameInfo = gameInfo;
        this.level = level;
    }

    public GameInfo getGameInfo() {
        return this.gameInfo;
    }

    public void setGameInfo(GameInfo gameInfo) {
        this.gameInfo = gameInfo;
    }

    public int getLevel() {
        return this.level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public boolean isFinished() {
        return this.level > 0;
    }

    public String toString() {
        return "LevelInfo{gameInfo=" + this.gameInfo + ", level=" + this.level + '}';
    }
}
