package com.cube.memorygames.model;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;

public class GameInfo implements Parcelable {
    public static final Creator<GameInfo> CREATOR = new Creator<GameInfo>() {
        public GameInfo createFromParcel(Parcel in) {
            return new GameInfo(in);
        }

        public GameInfo[] newArray(int size) {
            return new GameInfo[size];
        }
    };
    private String analyticsName;
    private int gameImageRes;
    private int gameNameRes;
    private boolean hasLock;
    private String id;
    private boolean isSecretGame;
    private int onlineLevelTime;
    private int starsToUnlock;

    public GameInfo(int gameNameRes, String analyticsName, int gameImageRes, int onlineLevelTime, String id) {
        this.gameNameRes = gameNameRes;
        this.analyticsName = analyticsName;
        this.gameImageRes = gameImageRes;
        this.id = id;
        this.hasLock = false;
        this.onlineLevelTime = onlineLevelTime;
    }

    public GameInfo(int gameNameRes, String analyticsName, int gameImageRes, int onlineLevelTime, String id, int starsToUnlock) {
        this.gameNameRes = gameNameRes;
        this.analyticsName = analyticsName;
        this.gameImageRes = gameImageRes;
        this.id = id;
        this.hasLock = true;
        this.starsToUnlock = starsToUnlock;
        this.onlineLevelTime = onlineLevelTime;
    }

    public GameInfo(int gameNameRes, String analyticsName, int gameImageRes, int onlineLevelTime, String id, int starsToUnlock, boolean isSecretGame) {
        this.gameNameRes = gameNameRes;
        this.analyticsName = analyticsName;
        this.gameImageRes = gameImageRes;
        this.id = id;
        this.hasLock = true;
        this.starsToUnlock = starsToUnlock;
        this.onlineLevelTime = onlineLevelTime;
        this.isSecretGame = isSecretGame;
    }

    protected GameInfo(Parcel in) {
        boolean z;
        boolean z2 = true;
        this.gameNameRes = in.readInt();
        this.analyticsName = in.readString();
        this.gameImageRes = in.readInt();
        this.id = in.readString();
        if (in.readByte() != (byte) 0) {
            z = true;
        } else {
            z = false;
        }
        this.hasLock = z;
        if (in.readByte() == (byte) 0) {
            z2 = false;
        }
        this.isSecretGame = z2;
        this.starsToUnlock = in.readInt();
        this.onlineLevelTime = in.readInt();
    }

    public void writeToParcel(Parcel dest, int flags) {
        int i;
        int i2 = 1;
        dest.writeInt(this.gameNameRes);
        dest.writeString(this.analyticsName);
        dest.writeInt(this.gameImageRes);
        dest.writeString(this.id);
        if (this.hasLock) {
            i = 1;
        } else {
            i = 0;
        }
        dest.writeByte((byte) i);
        if (!this.isSecretGame) {
            i2 = 0;
        }
        dest.writeByte((byte) i2);
        dest.writeInt(this.starsToUnlock);
        dest.writeInt(this.onlineLevelTime);
    }

    public int getGameNameRes() {
        return this.gameNameRes;
    }

    public int getGameImageRes() {
        return this.gameImageRes;
    }

    public String getId() {
        return this.id;
    }

    public boolean hasLock() {
        return this.hasLock;
    }

    public int getStarsToUnlock() {
        return this.starsToUnlock;
    }

    public String getAnalyticsName() {
        return this.analyticsName;
    }

    public int getOnlineLevelTime() {
        return this.onlineLevelTime;
    }

    public boolean isSecretGame() {
        return this.isSecretGame;
    }

    public int describeContents() {
        return 0;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return this.id.equals(((GameInfo) o).id);
    }

    public int hashCode() {
        return this.id.hashCode();
    }
}
