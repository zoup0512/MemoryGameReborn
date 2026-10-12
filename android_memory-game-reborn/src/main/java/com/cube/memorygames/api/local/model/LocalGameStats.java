package com.cube.memorygames.api.local.model;

import com.activeandroid.Model;
import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Table;

@Table(name = "GameStats")
public class LocalGameStats extends Model {
    public LocalGameStats() {
    }

    @Column(name = "avgLevel")
    public float avgLevel;
    @Column(index = true, name = "game")
    public String game;
    @Column(name = "maxLevel")
    public int maxLevel;

    public LocalGameStats(String game, int maxLevel, float avgLevel) {
        this.game = game;
        this.maxLevel = maxLevel;
        this.avgLevel = avgLevel;
    }
}
