package com.activeandroid;

import android.text.TextUtils;
import android.util.Log;
import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Table;
import com.activeandroid.util.ReflectionUtils;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public final class TableInfo {
    private Map<Field, String> mColumnNames = new LinkedHashMap();
    private String mIdName = "Id";
    private String mTableName;
    private Class<? extends Model> mType;

    public TableInfo(Class<? extends Model> type) {
        this.mType = type;
        Table tableAnnotation = (Table) type.getAnnotation(Table.class);
        if (tableAnnotation != null) {
            this.mTableName = tableAnnotation.name();
            this.mIdName = tableAnnotation.id();
        } else {
            this.mTableName = type.getSimpleName();
        }
        this.mColumnNames.put(getIdField(type), this.mIdName);
        List<Field> fields = new LinkedList(ReflectionUtils.getDeclaredColumnFields(type));
        Collections.reverse(fields);
        for (Field field : fields) {
            if (field.isAnnotationPresent(Column.class)) {
                String columnName = ((Column) field.getAnnotation(Column.class)).name();
                if (TextUtils.isEmpty(columnName)) {
                    columnName = field.getName();
                }
                this.mColumnNames.put(field, columnName);
            }
        }
    }

    public Class<? extends Model> getType() {
        return this.mType;
    }

    public String getTableName() {
        return this.mTableName;
    }

    public String getIdName() {
        return this.mIdName;
    }

    public Collection<Field> getFields() {
        return this.mColumnNames.keySet();
    }

    public String getColumnName(Field field) {
        return (String) this.mColumnNames.get(field);
    }

    private Field getIdField(Class<?> type) {
        if (type.equals(Model.class)) {
            try {
                return type.getDeclaredField("mId");
            } catch (NoSuchFieldException e) {
                Log.e("Impossible!", e.toString());
            }
        } else {
            if (type.getSuperclass() != null) {
                return getIdField(type.getSuperclass());
            }
            return null;
        }
        return null;
    }
}
