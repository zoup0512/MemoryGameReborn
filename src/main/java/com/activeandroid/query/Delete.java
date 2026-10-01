package com.activeandroid.query;

import com.activeandroid.Model;

public final class Delete implements Sqlable {
    public From from(Class<? extends Model> table) {
        return new From(table, this);
    }

    public String toSql() {
        return "DELETE ";
    }
}
