package com.activeandroid.query;

import com.activeandroid.Cache;
import com.activeandroid.Model;

public final class Update implements Sqlable {
    private Class<? extends Model> mType;

    public Update(Class<? extends Model> table) {
        this.mType = table;
    }

    public Set set(String set) {
        return new Set(this, set);
    }

    public Set set(String set, Object... args) {
        return new Set(this, set, args);
    }

    Class<? extends Model> getType() {
        return this.mType;
    }

    public String toSql() {
        StringBuilder sql = new StringBuilder();
        sql.append("UPDATE ");
        sql.append(Cache.getTableName(this.mType));
        sql.append(" ");
        return sql.toString();
    }
}
