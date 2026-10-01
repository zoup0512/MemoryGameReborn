package com.activeandroid.query;

import com.activeandroid.util.SQLiteUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Set implements Sqlable {
    private String mSet;
    private List<Object> mSetArguments = new ArrayList();
    private Update mUpdate;
    private String mWhere;
    private List<Object> mWhereArguments = new ArrayList();

    public Set(Update queryBase, String set) {
        this.mUpdate = queryBase;
        this.mSet = set;
    }

    public Set(Update queryBase, String set, Object... args) {
        this.mUpdate = queryBase;
        this.mSet = set;
        this.mSetArguments.addAll(Arrays.asList(args));
    }

    public Set where(String where) {
        this.mWhere = where;
        this.mWhereArguments.clear();
        return this;
    }

    public Set where(String where, Object... args) {
        this.mWhere = where;
        this.mWhereArguments.clear();
        this.mWhereArguments.addAll(Arrays.asList(args));
        return this;
    }

    public String toSql() {
        StringBuilder sql = new StringBuilder();
        sql.append(this.mUpdate.toSql());
        sql.append("SET ");
        sql.append(this.mSet);
        sql.append(" ");
        if (this.mWhere != null) {
            sql.append("WHERE ");
            sql.append(this.mWhere);
            sql.append(" ");
        }
        return sql.toString();
    }

    public void execute() {
        SQLiteUtils.execSql(toSql(), getArguments());
    }

    public String[] getArguments() {
        int i;
        int setSize = this.mSetArguments.size();
        int whereSize = this.mWhereArguments.size();
        String[] args = new String[(setSize + whereSize)];
        for (i = 0; i < setSize; i++) {
            args[i] = this.mSetArguments.get(i).toString();
        }
        for (i = 0; i < whereSize; i++) {
            args[i + setSize] = this.mWhereArguments.get(i).toString();
        }
        return args;
    }
}
