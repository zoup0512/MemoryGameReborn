package com.cube.memorygames.api.local.model;

import com.activeandroid.Model;
import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Table;
import java.util.Date;

@Table(name = "CoinsTransaction")
public class LocalCoinsTransaction extends Model {
    public LocalCoinsTransaction() {
    }

    @Column(name = "amount")
    public int amount;
    @Column(name = "createdAt")
    public Date createdAt;
    @Column(name = "item")
    public String item;
    @Column(name = "updatedAt")
    public Date updatedAt;
    @Column(name = "uploaded")
    public boolean uploaded;

    public LocalCoinsTransaction(Date createdAt, Date updatedAt, int amount, String item, boolean uploaded) {
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.amount = amount;
        this.item = item;
        this.uploaded = uploaded;
    }
}
