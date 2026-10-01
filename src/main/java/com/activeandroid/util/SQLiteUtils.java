package com.activeandroid.util;

import android.database.Cursor;
import android.os.Build.VERSION;
import android.text.TextUtils;
import com.activeandroid.Cache;
import com.activeandroid.Model;
import com.activeandroid.TableInfo;
import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Column.ConflictAction;
import com.activeandroid.serializer.TypeSerializer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

public final class SQLiteUtils {
    public static final boolean FOREIGN_KEYS_SUPPORTED = (VERSION.SDK_INT >= 8);
    private static final HashMap<Class<?>, SQLiteType> TYPE_MAP = new HashMap<Class<?>, SQLiteType>() {
        {
            put(Byte.TYPE, SQLiteType.INTEGER);
            put(Short.TYPE, SQLiteType.INTEGER);
            put(Integer.TYPE, SQLiteType.INTEGER);
            put(Long.TYPE, SQLiteType.INTEGER);
            put(Float.TYPE, SQLiteType.REAL);
            put(Double.TYPE, SQLiteType.REAL);
            put(Boolean.TYPE, SQLiteType.INTEGER);
            put(Character.TYPE, SQLiteType.TEXT);
            put(byte[].class, SQLiteType.BLOB);
            put(Byte.class, SQLiteType.INTEGER);
            put(Short.class, SQLiteType.INTEGER);
            put(Integer.class, SQLiteType.INTEGER);
            put(Long.class, SQLiteType.INTEGER);
            put(Float.class, SQLiteType.REAL);
            put(Double.class, SQLiteType.REAL);
            put(Boolean.class, SQLiteType.INTEGER);
            put(Character.class, SQLiteType.TEXT);
            put(String.class, SQLiteType.TEXT);
            put(Byte[].class, SQLiteType.BLOB);
        }
    };
    private static HashMap<String, List<String>> sIndexGroupMap;
    private static HashMap<String, ConflictAction> sOnUniqueConflictsMap;
    private static HashMap<String, List<String>> sUniqueGroupMap;

    public enum SQLiteType {
        INTEGER,
        REAL,
        TEXT,
        BLOB
    }

    public static void execSql(String sql) {
        Cache.openDatabase().execSQL(sql);
    }

    public static void execSql(String sql, Object[] bindArgs) {
        Cache.openDatabase().execSQL(sql, bindArgs);
    }

    public static <T extends Model> List<T> rawQuery(Class<? extends Model> type, String sql, String[] selectionArgs) {
        Cursor cursor = Cache.openDatabase().rawQuery(sql, selectionArgs);
        List<T> entities = processCursor(type, cursor);
        cursor.close();
        return entities;
    }

    public static int intQuery(String sql, String[] selectionArgs) {
        Cursor cursor = Cache.openDatabase().rawQuery(sql, selectionArgs);
        int number = processIntCursor(cursor);
        cursor.close();
        return number;
    }

    public static <T extends Model> T rawQuerySingle(Class<? extends Model> type, String sql, String[] selectionArgs) {
        List<T> entities = rawQuery(type, sql, selectionArgs);
        if (entities.size() > 0) {
            return (T) entities.get(0);
        }
        return null;
    }

    public static ArrayList<String> createUniqueDefinition(TableInfo tableInfo) {
        ArrayList<String> definitions = new ArrayList();
        sUniqueGroupMap = new HashMap();
        sOnUniqueConflictsMap = new HashMap();
        for (Field field : tableInfo.getFields()) {
            createUniqueColumnDefinition(tableInfo, field);
        }
        if (!sUniqueGroupMap.isEmpty()) {
            for (String key : sUniqueGroupMap.keySet()) {
                List<String> group = (List) sUniqueGroupMap.get(key);
                ConflictAction conflictAction = (ConflictAction) sOnUniqueConflictsMap.get(key);
                definitions.add(String.format("UNIQUE (%s) ON CONFLICT %s", new Object[]{TextUtils.join(", ", group), conflictAction.toString()}));
            }
        }
        return definitions;
    }

    public static void createUniqueColumnDefinition(TableInfo tableInfo, Field field) {
        String name = tableInfo.getColumnName(field);
        Column column = (Column) field.getAnnotation(Column.class);
        if (!field.getName().equals("mId")) {
            String[] groups = column.uniqueGroups();
            ConflictAction[] conflictActions = column.onUniqueConflicts();
            if (groups.length == conflictActions.length) {
                for (int i = 0; i < groups.length; i++) {
                    String group = groups[i];
                    ConflictAction conflictAction = conflictActions[i];
                    if (!TextUtils.isEmpty(group)) {
                        List<String> list = (List) sUniqueGroupMap.get(group);
                        if (list == null) {
                            list = new ArrayList();
                        }
                        list.add(name);
                        sUniqueGroupMap.put(group, list);
                        sOnUniqueConflictsMap.put(group, conflictAction);
                    }
                }
            }
        }
    }

    public static String[] createIndexDefinition(TableInfo tableInfo) {
        ArrayList<String> definitions = new ArrayList();
        sIndexGroupMap = new HashMap();
        for (Field field : tableInfo.getFields()) {
            createIndexColumnDefinition(tableInfo, field);
        }
        if (sIndexGroupMap.isEmpty()) {
            return new String[0];
        }
        for (Entry<String, List<String>> entry : sIndexGroupMap.entrySet()) {
            definitions.add(String.format("CREATE INDEX IF NOT EXISTS %s on %s(%s);", new Object[]{"index_" + tableInfo.getTableName() + "_" + ((String) entry.getKey()), tableInfo.getTableName(), TextUtils.join(", ", (Iterable) entry.getValue())}));
        }
        return (String[]) definitions.toArray(new String[definitions.size()]);
    }

    public static void createIndexColumnDefinition(TableInfo tableInfo, Field field) {
        String name = tableInfo.getColumnName(field);
        Column column = (Column) field.getAnnotation(Column.class);
        if (!field.getName().equals("mId")) {
            List<String> list;
            if (column.index()) {
                list = new ArrayList();
                list.add(name);
                sIndexGroupMap.put(name, list);
            }
            for (String group : column.indexGroups()) {
                if (!TextUtils.isEmpty(group)) {
                    list = (List) sIndexGroupMap.get(group);
                    if (list == null) {
                        list = new ArrayList();
                    }
                    list.add(name);
                    sIndexGroupMap.put(group, list);
                }
            }
        }
    }

    public static String createTableDefinition(TableInfo tableInfo) {
        ArrayList<String> definitions = new ArrayList();
        for (Field field : tableInfo.getFields()) {
            String definition = createColumnDefinition(tableInfo, field);
            if (!TextUtils.isEmpty(definition)) {
                definitions.add(definition);
            }
        }
        definitions.addAll(createUniqueDefinition(tableInfo));
        return String.format("CREATE TABLE IF NOT EXISTS %s (%s);", new Object[]{tableInfo.getTableName(), TextUtils.join(", ", definitions)});
    }

    public static String createColumnDefinition(TableInfo tableInfo, Field field) {
        StringBuilder definition = new StringBuilder();
        Class<?> type = field.getType();
        String name = tableInfo.getColumnName(field);
        TypeSerializer typeSerializer = Cache.getParserForType(field.getType());
        Column column = (Column) field.getAnnotation(Column.class);
        if (typeSerializer != null) {
            type = typeSerializer.getSerializedType();
        }
        if (TYPE_MAP.containsKey(type)) {
            definition.append(name);
            definition.append(" ");
            definition.append(((SQLiteType) TYPE_MAP.get(type)).toString());
        } else if (ReflectionUtils.isModel(type)) {
            definition.append(name);
            definition.append(" ");
            definition.append(SQLiteType.INTEGER.toString());
        } else if (ReflectionUtils.isSubclassOf(type, Enum.class)) {
            definition.append(name);
            definition.append(" ");
            definition.append(SQLiteType.TEXT.toString());
        }
        if (TextUtils.isEmpty(definition)) {
            Log.e("No type mapping for: " + type.toString());
        } else {
            if (name.equals(tableInfo.getIdName())) {
                definition.append(" PRIMARY KEY AUTOINCREMENT");
            } else if (column != null) {
                if (column.length() > -1) {
                    definition.append("(");
                    definition.append(column.length());
                    definition.append(")");
                }
                if (column.notNull()) {
                    definition.append(" NOT NULL ON CONFLICT ");
                    definition.append(column.onNullConflict().toString());
                }
                if (column.unique()) {
                    definition.append(" UNIQUE ON CONFLICT ");
                    definition.append(column.onUniqueConflict().toString());
                }
            }
            if (FOREIGN_KEYS_SUPPORTED && ReflectionUtils.isModel(type)) {
                definition.append(" REFERENCES ");
                definition.append(Cache.getTableInfo((Class<? extends Model>) (Class) type).getTableName());
                definition.append("(" + tableInfo.getIdName() + ")");
                definition.append(" ON DELETE ");
                definition.append(column.onDelete().toString().replace("_", " "));
                definition.append(" ON UPDATE ");
                definition.append(column.onUpdate().toString().replace("_", " "));
            }
        }
        return definition.toString();
    }

    public static <T extends Model> List<T> processCursor(Class<? extends Model> type, Cursor cursor) {
        String idName = Cache.getTableInfo(type).getIdName();
        List<T> entities = new ArrayList();
        try {
            Constructor<?> entityConstructor = type.getConstructor(new Class[0]);
            if (cursor.moveToFirst()) {
                List<String> columnsOrdered = new ArrayList(Arrays.asList(cursor.getColumnNames()));
                do {
                    T entity = (T) Cache.getEntity(type, cursor.getLong(columnsOrdered.indexOf(idName)));
                    if (entity == null) {
                        entity = (T) entityConstructor.newInstance(new Object[0]);
                    }
                    entity.loadFromCursor(cursor);
                    entities.add(entity);
                } while (cursor.moveToNext());
            }
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Your model " + type.getName() + " does not define a default " + "constructor. The default constructor is required for " + "now in ActiveAndroid models, as the process to " + "populate the ORM model is : " + "1. instantiate default model " + "2. populate fields");
        } catch (Throwable e2) {
            Log.e("Failed to process cursor.", e2);
        }
        return entities;
    }

    private static int processIntCursor(Cursor cursor) {
        if (cursor.moveToFirst()) {
            return cursor.getInt(0);
        }
        return 0;
    }

    public static List<String> lexSqlScript(String sqlScript) {
        ArrayList<String> sl = new ArrayList();
        boolean inString = false;
        boolean quoteNext = false;
        StringBuilder b = new StringBuilder(100);
        for (int i = 0; i < sqlScript.length(); i++) {
            char c = sqlScript.charAt(i);
            if (c != ';' || inString || quoteNext) {
                if (c == '\'' && !quoteNext) {
                    inString = !inString;
                }
                if (c != '\\' || quoteNext) {
                    quoteNext = false;
                } else {
                    quoteNext = true;
                }
                b.append(c);
            } else {
                sl.add(b.toString());
                b = new StringBuilder(100);
                inString = false;
                quoteNext = false;
            }
        }
        if (b.length() > 0) {
            sl.add(b.toString());
        }
        return sl;
    }
}
