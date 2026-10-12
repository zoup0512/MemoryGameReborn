package com.cube.memorygames.api.local.workout;

import java.util.List;

public class DayInfo {
    private String date;
    private List<DbLevelInfo> dayGames;
    private String dayOfWeek;

    public String getDate() {
        return this.date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getDayOfWeek() {
        return this.dayOfWeek;
    }

    public void setDayOfWeek(String dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public List<DbLevelInfo> getDayGames() {
        return this.dayGames;
    }

    public void setDayGames(List<DbLevelInfo> dayGames) {
        this.dayGames = dayGames;
    }

    public int getNumberOfCompletedGames() {
        if (this.dayGames == null) {
            return 0;
        }
        int result = 0;
        for (DbLevelInfo levelInfo : this.dayGames) {
            if (levelInfo.getLevel() > 0) {
                result++;
            }
        }
        return result;
    }

    public String toString() {
        return "DayInfo{date='" + this.date + '\'' + ", dayOfWeek='" + this.dayOfWeek + '\'' + ", dayGames=" + this.dayGames + '}';
    }
}
