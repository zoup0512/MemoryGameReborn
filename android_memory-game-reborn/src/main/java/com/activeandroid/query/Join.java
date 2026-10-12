package com.activeandroid.query;

import android.text.TextUtils;
import com.activeandroid.Cache;
import com.activeandroid.Model;

public final class Join implements Sqlable {
    private String mAlias;
    private From mFrom;
    private JoinType mJoinType;
    private String mOn;
    private Class<? extends Model> mType;
    private String[] mUsing;

    enum JoinType {
        LEFT,
        OUTER,
        INNER,
        CROSS
    }

    Join(From from, Class<? extends Model> table, JoinType joinType) {
        this.mFrom = from;
        this.mType = table;
        this.mJoinType = joinType;
    }

    public Join as(String alias) {
        this.mAlias = alias;
        return this;
    }

    public From on(String on) {
        this.mOn = on;
        return this.mFrom;
    }

    public From on(String on, Object... args) {
        this.mOn = on;
        this.mFrom.addArguments(args);
        return this.mFrom;
    }

    public From using(String... columns) {
        this.mUsing = columns;
        return this.mFrom;
    }

    public String toSql() {
        StringBuilder sql = new StringBuilder();
        if (this.mJoinType != null) {
            sql.append(this.mJoinType.toString()).append(" ");
        }
        sql.append("JOIN ");
        sql.append(Cache.getTableName(this.mType));
        sql.append(" ");
        if (this.mAlias != null) {
            sql.append("AS ");
            sql.append(this.mAlias);
            sql.append(" ");
        }
        if (this.mOn != null) {
            sql.append("ON ");
            sql.append(this.mOn);
            sql.append(" ");
        } else if (this.mUsing != null) {
            sql.append("USING (");
            sql.append(TextUtils.join(", ", this.mUsing));
            sql.append(") ");
        }
        return sql.toString();
    }
}
