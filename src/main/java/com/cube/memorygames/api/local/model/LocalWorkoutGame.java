package com.cube.memorygames.api.local.model;

import com.activeandroid.Model;
import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Table;

@Table(name = "LocalWorkoutGame")
public class LocalWorkoutGame extends Model {
    @Column(index = true, name = "date")
    public String date;
    @Column(name = "games")
    public String games;

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass() || !super.equals(o)) {
            return false;
        }
        return this.date.equals(((LocalWorkoutGame) o).date);
    }

    public int hashCode() {
        return (super.hashCode() * 31) + this.date.hashCode();
    }
}
