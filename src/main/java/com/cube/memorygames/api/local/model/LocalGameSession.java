package com.cube.memorygames.api.local.model;

import com.activeandroid.Model;
import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Table;
import java.util.Date;

@Table(name = "GameSession")
public class LocalGameSession extends Model {
    public LocalGameSession() {
    }

    @Column(index = true, name = "createdAt")
    public Date createdAt;
    @Column(name = "endLevel")
    public int endLevel;
    @Column(index = true, name = "game")
    public String game;
    @Column(name = "moneyEarned")
    public int moneyEarned;
    @Column(name = "moneyPaid")
    public int moneyPaid;
    @Column(name = "replaysUsed")
    public int replaysUsed;
    @Column(name = "startLevel")
    public int startLevel;
    @Column(name = "updatedAt")
    public Date updatedAt;
    @Column(name = "uploaded")
    public boolean uploaded;

    public LocalGameSession(Date createdAt, Date updatedAt, String game, int startLevel, int endLevel, int moneyPaid, int moneyEarned, int replaysUsed, boolean uploaded) {
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.game = game;
        this.startLevel = startLevel;
        this.endLevel = endLevel;
        this.moneyPaid = moneyPaid;
        this.moneyEarned = moneyEarned;
        this.replaysUsed = replaysUsed;
        this.uploaded = uploaded;
    }
}
