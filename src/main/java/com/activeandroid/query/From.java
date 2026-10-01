package com.activeandroid.query;

import android.text.TextUtils;
import com.activeandroid.Cache;
import com.activeandroid.Model;
import com.activeandroid.content.ContentProvider;
import com.activeandroid.util.Log;
import com.activeandroid.util.SQLiteUtils;
import java.util.ArrayList;
import java.util.List;

public final class From implements Sqlable {
    private String mAlias;
    private List<Object> mArguments;
    private String mGroupBy;
    private String mHaving;
    private List<Join> mJoins;
    private String mLimit;
    private String mOffset;
    private String mOrderBy;
    private Sqlable mQueryBase;
    private Class<? extends Model> mType;
    private final StringBuilder mWhere = new StringBuilder();

    public From(Class<? extends Model> table, Sqlable queryBase) {
        this.mType = table;
        this.mJoins = new ArrayList();
        this.mQueryBase = queryBase;
        this.mJoins = new ArrayList();
        this.mArguments = new ArrayList();
    }

    public From as(String alias) {
        this.mAlias = alias;
        return this;
    }

    public Join join(Class<? extends Model> table) {
        Join join = new Join(this, table, null);
        this.mJoins.add(join);
        return join;
    }

    public Join leftJoin(Class<? extends Model> table) {
        Join join = new Join(this, table, Join.JoinType.LEFT);
        this.mJoins.add(join);
        return join;
    }

    public Join outerJoin(Class<? extends Model> table) {
        Join join = new Join(this, table, Join.JoinType.OUTER);
        this.mJoins.add(join);
        return join;
    }

    public Join innerJoin(Class<? extends Model> table) {
        Join join = new Join(this, table, Join.JoinType.INNER);
        this.mJoins.add(join);
        return join;
    }

    public Join crossJoin(Class<? extends Model> table) {
        Join join = new Join(this, table, Join.JoinType.CROSS);
        this.mJoins.add(join);
        return join;
    }

    public From where(String clause) {
        if (this.mWhere.length() > 0) {
            this.mWhere.append(" AND ");
        }
        this.mWhere.append(clause);
        return this;
    }

    public From where(String clause, Object... args) {
        where(clause).addArguments(args);
        return this;
    }

    public From and(String clause) {
        return where(clause);
    }

    public From and(String clause, Object... args) {
        return where(clause, args);
    }

    public From or(String clause) {
        if (this.mWhere.length() > 0) {
            this.mWhere.append(" OR ");
        }
        this.mWhere.append(clause);
        return this;
    }

    public From or(String clause, Object... args) {
        or(clause).addArguments(args);
        return this;
    }

    public From groupBy(String groupBy) {
        this.mGroupBy = groupBy;
        return this;
    }

    public From having(String having) {
        this.mHaving = having;
        return this;
    }

    public From orderBy(String orderBy) {
        this.mOrderBy = orderBy;
        return this;
    }

    public From limit(int limit) {
        return limit(String.valueOf(limit));
    }

    public From limit(String limit) {
        this.mLimit = limit;
        return this;
    }

    public From offset(int offset) {
        return offset(String.valueOf(offset));
    }

    public From offset(String offset) {
        this.mOffset = offset;
        return this;
    }

    void addArguments(Object[] args) {
        for (Object arg : args) {
            Object arg2 = arg;
            if (arg2.getClass() == Boolean.TYPE || arg2.getClass() == Boolean.class) {
                int i;
                if (arg2.equals(Boolean.valueOf(true))) {
                    i = 1;
                } else {
                    i = 0;
                }
                arg2 = Integer.valueOf(i);
            }
            this.mArguments.add(arg2);
        }
    }

    private void addFrom(StringBuilder sql) {
        sql.append("FROM ");
        sql.append(Cache.getTableName(this.mType)).append(" ");
        if (this.mAlias != null) {
            sql.append("AS ");
            sql.append(this.mAlias);
            sql.append(" ");
        }
    }

    private void addJoins(StringBuilder sql) {
        for (Join join : this.mJoins) {
            sql.append(join.toSql());
        }
    }

    private void addWhere(StringBuilder sql) {
        if (this.mWhere.length() > 0) {
            sql.append("WHERE ");
            sql.append(this.mWhere);
            sql.append(" ");
        }
    }

    private void addGroupBy(StringBuilder sql) {
        if (this.mGroupBy != null) {
            sql.append("GROUP BY ");
            sql.append(this.mGroupBy);
            sql.append(" ");
        }
    }

    private void addHaving(StringBuilder sql) {
        if (this.mHaving != null) {
            sql.append("HAVING ");
            sql.append(this.mHaving);
            sql.append(" ");
        }
    }

    private void addOrderBy(StringBuilder sql) {
        if (this.mOrderBy != null) {
            sql.append("ORDER BY ");
            sql.append(this.mOrderBy);
            sql.append(" ");
        }
    }

    private void addLimit(StringBuilder sql) {
        if (this.mLimit != null) {
            sql.append("LIMIT ");
            sql.append(this.mLimit);
            sql.append(" ");
        }
    }

    private void addOffset(StringBuilder sql) {
        if (this.mOffset != null) {
            sql.append("OFFSET ");
            sql.append(this.mOffset);
            sql.append(" ");
        }
    }

    private String sqlString(StringBuilder sql) {
        String sqlString = sql.toString().trim();
        if (Log.isEnabled()) {
            Log.v(sqlString + " " + TextUtils.join(",", getArguments()));
        }
        return sqlString;
    }

    public String toSql() {
        StringBuilder sql = new StringBuilder();
        sql.append(this.mQueryBase.toSql());
        addFrom(sql);
        addJoins(sql);
        addWhere(sql);
        addGroupBy(sql);
        addHaving(sql);
        addOrderBy(sql);
        addLimit(sql);
        addOffset(sql);
        return sqlString(sql);
    }

    public String toExistsSql() {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT EXISTS(SELECT 1 ");
        addFrom(sql);
        addJoins(sql);
        addWhere(sql);
        addGroupBy(sql);
        addHaving(sql);
        addLimit(sql);
        addOffset(sql);
        sql.append(")");
        return sqlString(sql);
    }

    public String toCountSql() {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) ");
        addFrom(sql);
        addJoins(sql);
        addWhere(sql);
        addGroupBy(sql);
        addHaving(sql);
        addLimit(sql);
        addOffset(sql);
        return sqlString(sql);
    }

    public <T extends Model> List<T> execute() {
        if (this.mQueryBase instanceof Select) {
            return SQLiteUtils.rawQuery(this.mType, toSql(), getArguments());
        }
        SQLiteUtils.execSql(toSql(), getArguments());
        Cache.getContext().getContentResolver().notifyChange(ContentProvider.createUri(this.mType, null), null);
        return null;
    }

    public <T extends Model> T executeSingle() {
        if (this.mQueryBase instanceof Select) {
            limit(1);
            return SQLiteUtils.rawQuerySingle(this.mType, toSql(), getArguments());
        }
        limit(1);
        SQLiteUtils.rawQuerySingle(this.mType, toSql(), getArguments()).delete();
        return null;
    }

    public boolean exists() {
        return SQLiteUtils.intQuery(toExistsSql(), getArguments()) != 0;
    }

    public int count() {
        return SQLiteUtils.intQuery(toCountSql(), getArguments());
    }

    public String[] getArguments() {
        int size = this.mArguments.size();
        String[] args = new String[size];
        for (int i = 0; i < size; i++) {
            args[i] = this.mArguments.get(i).toString();
        }
        return args;
    }
}
