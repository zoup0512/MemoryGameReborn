package com.activeandroid.query;

import android.text.TextUtils;
import com.activeandroid.Model;

public final class Select implements Sqlable {
    private boolean mAll = false;
    private String[] mColumns;
    private boolean mDistinct = false;

    public static class Column {
        String alias;
        String name;

        public Column(String name, String alias) {
            this.name = name;
            this.alias = alias;
        }
    }

    public Select() {
    }

    public Select(String... columns) {
        this.mColumns = columns;
    }

    public Select(Column... columns) {
        int size = columns.length;
        this.mColumns = new String[size];
        for (int i = 0; i < size; i++) {
            this.mColumns[i] = columns[i].name + " AS " + columns[i].alias;
        }
    }

    public Select distinct() {
        this.mDistinct = true;
        this.mAll = false;
        return this;
    }

    public Select all() {
        this.mDistinct = false;
        this.mAll = true;
        return this;
    }

    public <T extends Model> From from(Class<T> table) {
        return new From(table, this);
    }

    public String toSql() {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ");
        if (this.mDistinct) {
            sql.append("DISTINCT ");
        } else if (this.mAll) {
            sql.append("ALL ");
        }
        if (this.mColumns == null || this.mColumns.length <= 0) {
            sql.append("* ");
        } else {
            sql.append(TextUtils.join(", ", this.mColumns) + " ");
        }
        return sql.toString();
    }
}
