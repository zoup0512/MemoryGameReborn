package com.activeandroid;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.activeandroid.content.ContentProvider;
import com.activeandroid.query.Delete;
import com.activeandroid.query.Select;
import com.activeandroid.serializer.TypeSerializer;
import com.activeandroid.util.Log;
import com.activeandroid.util.ReflectionUtils;
import com.yalantis.ucrop.util.FileUtils;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class Model {
    public Model() {
    }

    private static final int HASH_PRIME = 739;
    private final TableInfo mTableInfo = Cache.getTableInfo(getClass());
    private final String idName = this.mTableInfo.getIdName();
    private Long mId = null;

    public final Long getId() {
        return this.mId;
    }

    public final void delete() {
        Cache.openDatabase().delete(this.mTableInfo.getTableName(), this.idName + "=?", new String[]{getId().toString()});
        Cache.removeEntity(this);
        Cache.getContext().getContentResolver().notifyChange(ContentProvider.createUri(this.mTableInfo.getType(), this.mId), null);
    }

    public final Long save() {
        SQLiteDatabase db = Cache.openDatabase();
        onBeforePersist();
        ContentValues values = new ContentValues();
        for (Field field : this.mTableInfo.getFields()) {
            String fieldName = this.mTableInfo.getColumnName(field);
            Class<?> fieldType = field.getType();
            field.setAccessible(true);
            try {
                Object value = field.get(this);
                if (value != null) {
                    TypeSerializer typeSerializer = Cache.getParserForType(fieldType);
                    if (typeSerializer != null) {
                        value = typeSerializer.serialize(value);
                        if (value != null) {
                            fieldType = value.getClass();
                            if (!fieldType.equals(typeSerializer.getSerializedType())) {
                                Log.w(String.format("TypeSerializer returned wrong type: expected a %s but got a %s", new Object[]{typeSerializer.getSerializedType(), fieldType}));
                            }
                        }
                    }
                }
                if (value == null) {
                    values.putNull(fieldName);
                } else if (fieldType.equals(Byte.class) || fieldType.equals(Byte.TYPE)) {
                    values.put(fieldName, (Byte) value);
                } else if (fieldType.equals(Short.class) || fieldType.equals(Short.TYPE)) {
                    values.put(fieldName, (Short) value);
                } else if (fieldType.equals(Integer.class) || fieldType.equals(Integer.TYPE)) {
                    values.put(fieldName, (Integer) value);
                } else if (fieldType.equals(Long.class) || fieldType.equals(Long.TYPE)) {
                    values.put(fieldName, (Long) value);
                } else if (fieldType.equals(Float.class) || fieldType.equals(Float.TYPE)) {
                    values.put(fieldName, (Float) value);
                } else if (fieldType.equals(Double.class) || fieldType.equals(Double.TYPE)) {
                    values.put(fieldName, (Double) value);
                } else if (fieldType.equals(Boolean.class) || fieldType.equals(Boolean.TYPE)) {
                    values.put(fieldName, (Boolean) value);
                } else if (fieldType.equals(Character.class) || fieldType.equals(Character.TYPE)) {
                    values.put(fieldName, value.toString());
                } else if (fieldType.equals(String.class)) {
                    values.put(fieldName, value.toString());
                } else if (fieldType.equals(Byte[].class) || fieldType.equals(byte[].class)) {
                    values.put(fieldName, (byte[]) value);
                } else if (ReflectionUtils.isModel(fieldType)) {
                    values.put(fieldName, ((Model) value).getId());
                } else if (ReflectionUtils.isSubclassOf(fieldType, Enum.class)) {
                    values.put(fieldName, ((Enum) value).name());
                }
            } catch (Throwable e) {
                Log.e(e.getClass().getName(), e);
            }
        }
        if (this.mId == null) {
            this.mId = Long.valueOf(db.insert(this.mTableInfo.getTableName(), null, values));
        } else {
            db.update(this.mTableInfo.getTableName(), values, this.idName + "=" + this.mId, null);
        }
        Cache.getContext().getContentResolver().notifyChange(ContentProvider.createUri(this.mTableInfo.getType(), this.mId), null);
        return this.mId;
    }

    public static void delete(Class<? extends Model> type, long id) {
        TableInfo tableInfo = Cache.getTableInfo(type);
        new Delete().from(type).where(tableInfo.getIdName() + "=?", Long.valueOf(id)).execute();
    }

    public static <T extends Model> T load(Class<T> type, long id) {
        TableInfo tableInfo = Cache.getTableInfo(type);
        return new Select().from(type).where(tableInfo.getIdName() + "=?", Long.valueOf(id)).executeSingle();
    }

    public final void loadFromCursor(Cursor cursor) {
        List<String> columnsOrdered = new ArrayList(Arrays.asList(cursor.getColumnNames()));
        for (Field field : this.mTableInfo.getFields()) {
            String fieldName = this.mTableInfo.getColumnName(field);
            Class<?> fieldType = field.getType();
            int columnIndex = columnsOrdered.indexOf(fieldName);
            if (columnIndex >= 0) {
                field.setAccessible(true);
                try {
                    boolean columnIsNull = cursor.isNull(columnIndex);
                    TypeSerializer typeSerializer = Cache.getParserForType(fieldType);
                    Object value = null;
                    if (typeSerializer != null) {
                        fieldType = typeSerializer.getSerializedType();
                    }
                    if (columnIsNull) {
                    } else if (fieldType.equals(Byte.class) || fieldType.equals(Byte.TYPE)) {
                        value = Integer.valueOf(cursor.getInt(columnIndex));
                    } else if (fieldType.equals(Short.class) || fieldType.equals(Short.TYPE)) {
                        value = Integer.valueOf(cursor.getInt(columnIndex));
                    } else if (fieldType.equals(Integer.class) || fieldType.equals(Integer.TYPE)) {
                        value = Integer.valueOf(cursor.getInt(columnIndex));
                    } else if (fieldType.equals(Long.class) || fieldType.equals(Long.TYPE)) {
                        value = Long.valueOf(cursor.getLong(columnIndex));
                    } else if (fieldType.equals(Float.class) || fieldType.equals(Float.TYPE)) {
                        value = Float.valueOf(cursor.getFloat(columnIndex));
                    } else if (fieldType.equals(Double.class) || fieldType.equals(Double.TYPE)) {
                        value = Double.valueOf(cursor.getDouble(columnIndex));
                    } else if (fieldType.equals(Boolean.class) || fieldType.equals(Boolean.TYPE)) {
                        value = Boolean.valueOf(cursor.getInt(columnIndex) != 0);
                    } else if (fieldType.equals(Character.class) || fieldType.equals(Character.TYPE)) {
                        value = Character.valueOf(cursor.getString(columnIndex).charAt(0));
                    } else if (fieldType.equals(String.class)) {
                        value = cursor.getString(columnIndex);
                    } else if (fieldType.equals(Byte[].class) || fieldType.equals(byte[].class)) {
                        value = cursor.getBlob(columnIndex);
                    } else if (ReflectionUtils.isModel(fieldType)) {
                        Class<? extends Model> entityType = (Class<? extends Model>) (Class) fieldType;
                        Model entity = Cache.getEntity(entityType, cursor.getLong(columnIndex));
                        if (entity == null) {
                            entity = new Select().from(entityType).where(this.idName + "=?", Long.valueOf(getId() == null ? 0L : getId().longValue())).executeSingle();
                        }
                        Model value2 = entity;
                    } else if (ReflectionUtils.isSubclassOf(fieldType, Enum.class)) {
                        value = Enum.valueOf((Class<? extends Enum>) (Class) fieldType, cursor.getString(columnIndex));
                    }
                    if (!(typeSerializer == null || columnIsNull)) {
                        value = typeSerializer.deserialize(value);
                    }
                    if (value != null) {
                        field.set(this, value);
                    }
                } catch (Throwable e) {
                    Log.e(e.getClass().getName(), e);
                }
            }
        }
        onAfterLoad();
        if (this.mId != null) {
            Cache.addEntity(this);
        }
    }

    /**
     * Persist hooks (hardening design §4.3): values are about to be read from
     * the fields, so models can derive dependent columns here; after a load,
     * models can verify integrity columns. Both default to no-op.
     */
    protected void onBeforePersist() {
    }

    protected void onAfterLoad() {
    }

    protected final <T extends Model> List<T> getMany(Class<T> type, String foreignKey) {
        return new Select().from(type).where(Cache.getTableName(type) + FileUtils.HIDDEN_PREFIX + foreignKey + "=?", getId()).execute();
    }

    public String toString() {
        return this.mTableInfo.getTableName() + "@" + getId();
    }

    public boolean equals(Object obj) {
        if ((obj instanceof Model) && this.mId != null) {
            Model other = (Model) obj;
            if (this.mId.equals(other.mId) && this.mTableInfo.getTableName().equals(other.mTableInfo.getTableName())) {
                return true;
            }
            return false;
        } else if (this != obj) {
            return false;
        } else {
            return true;
        }
    }

    public int hashCode() {
        return (HASH_PRIME + ((this.mId == null ? super.hashCode() : this.mId.hashCode()) * HASH_PRIME)) + (this.mTableInfo.getTableName().hashCode() * HASH_PRIME);
    }
}
