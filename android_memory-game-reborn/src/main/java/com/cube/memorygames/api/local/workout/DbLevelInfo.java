package com.cube.memorygames.api.local.workout;

public class DbLevelInfo {
    private String gameId;
    private int level;

    public DbLevelInfo(String gameId, int level) {
        this.gameId = gameId;
        this.level = level;
    }

    public String getGameId() {
        return this.gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
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
        return "DbLevelInfo{gameId='" + this.gameId + '\'' + ", level=" + this.level + '}';
    }
}
