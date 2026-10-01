package com.cube.memorygames.api.local.challenge;

import com.activeandroid.Model;
import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Table;
import java.util.Date;

@Table(name = "ChallengeDbGame")
public class ChallengeDbGame extends Model {
    @Column(name = "createdAt")
    public Date createdAt;
    @Column(name = "gameId")
    public String gameId;
    @Column(name = "goal")
    public int goal;
    @Column(name = "levelNumber")
    public int levelNumber;
    @Column(name = "score")
    public int score;
    @Column(name = "updatedAt")
    public Date updatedAt;
}
