package com.cube.memorygames.api.local.challenge;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;

public class ChallengeJsonGame implements Parcelable {
    public static final Creator<ChallengeJsonGame> CREATOR = new Creator<ChallengeJsonGame>() {
        public ChallengeJsonGame createFromParcel(Parcel in) {
            return new ChallengeJsonGame(in);
        }

        public ChallengeJsonGame[] newArray(int size) {
            return new ChallengeJsonGame[size];
        }
    };
    private int level;
    private int levelNumber;
    private String name;
    private int stars;

    protected ChallengeJsonGame(Parcel in) {
        this.name = in.readString();
        this.level = in.readInt();
        this.levelNumber = in.readInt();
        this.stars = in.readInt();
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.name);
        dest.writeInt(this.level);
        dest.writeInt(this.levelNumber);
        dest.writeInt(this.stars);
    }

    public int describeContents() {
        return 0;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getLevel() {
        return this.level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getLevelNumber() {
        return this.levelNumber;
    }

    public void setLevelNumber(int levelNumber) {
        this.levelNumber = levelNumber;
    }

    public int getStars() {
        return this.stars;
    }

    public void setStars(int stars) {
        this.stars = stars;
    }
}
