package com.cube.memorygames.api.local.challenge;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;

public class ChallengeJsonLevel implements Parcelable {
    public static final Creator<ChallengeJsonLevel> CREATOR = new Creator<ChallengeJsonLevel>() {
        public ChallengeJsonLevel createFromParcel(Parcel in) {
            return new ChallengeJsonLevel(in);
        }

        public ChallengeJsonLevel[] newArray(int size) {
            return new ChallengeJsonLevel[size];
        }
    };
    private int games;
    private String name;

    protected ChallengeJsonLevel(Parcel in) {
        this.name = in.readString();
        this.games = in.readInt();
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.name);
        dest.writeInt(this.games);
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

    public int getGames() {
        return this.games;
    }

    public void setGames(int games) {
        this.games = games;
    }
}
