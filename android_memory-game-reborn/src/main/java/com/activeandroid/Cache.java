package com.activeandroid;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.support.v4.util.LruCache;
import com.activeandroid.serializer.TypeSerializer;
import com.activeandroid.util.Log;
import java.util.Collection;

public final class Cache {
    public static final int DEFAULT_CACHE_SIZE = 1024;
    private static Context sContext;
    private static DatabaseHelper sDatabaseHelper;
    private static LruCache<String, Model> sEntities;
    private static boolean sIsInitialized = false;
    private static ModelInfo sModelInfo;

    private Cache() {
    }

    public static synchronized void initialize(Configuration configuration) {
        synchronized (Cache.class) {
            if (sIsInitialized) {
                Log.v("ActiveAndroid already initialized.");
            } else {
                sContext = configuration.getContext();
                sModelInfo = new ModelInfo(configuration);
                sDatabaseHelper = new DatabaseHelper(configuration);
                sEntities = new LruCache(configuration.getCacheSize());
                openDatabase();
                sIsInitialized = true;
                Log.v("ActiveAndroid initialized successfully.");
            }
        }
    }

    public static synchronized void clear() {
        synchronized (Cache.class) {
            sEntities.evictAll();
            Log.v("Cache cleared.");
        }
    }

    public static synchronized void dispose() {
        synchronized (Cache.class) {
            closeDatabase();
            sEntities = null;
            sModelInfo = null;
            sDatabaseHelper = null;
            sIsInitialized = false;
            Log.v("ActiveAndroid disposed. Call initialize to use library.");
        }
    }

    public static boolean isInitialized() {
        return sIsInitialized;
    }

    public static synchronized SQLiteDatabase openDatabase() {
        SQLiteDatabase writableDatabase;
        synchronized (Cache.class) {
            writableDatabase = sDatabaseHelper.getWritableDatabase();
        }
        return writableDatabase;
    }

    public static synchronized void closeDatabase() {
        synchronized (Cache.class) {
            sDatabaseHelper.close();
        }
    }

    public static Context getContext() {
        return sContext;
    }

    public static String getIdentifier(Class<? extends Model> type, Long id) {
        return getTableName(type) + "@" + id;
    }

    public static String getIdentifier(Model entity) {
        return getIdentifier(entity.getClass(), entity.getId());
    }

    public static synchronized void addEntity(Model entity) {
        synchronized (Cache.class) {
            sEntities.put(getIdentifier(entity), entity);
        }
    }

    public static synchronized Model getEntity(Class<? extends Model> type, long id) {
        Model model;
        synchronized (Cache.class) {
            model = (Model) sEntities.get(getIdentifier(type, Long.valueOf(id)));
        }
        return model;
    }

    public static synchronized void removeEntity(Model entity) {
        synchronized (Cache.class) {
            sEntities.remove(getIdentifier(entity));
        }
    }

    public static synchronized Collection<TableInfo> getTableInfos() {
        Collection<TableInfo> tableInfos;
        synchronized (Cache.class) {
            tableInfos = sModelInfo.getTableInfos();
        }
        return tableInfos;
    }

    public static synchronized TableInfo getTableInfo(Class<? extends Model> type) {
        TableInfo tableInfo;
        synchronized (Cache.class) {
            tableInfo = sModelInfo.getTableInfo(type);
        }
        return tableInfo;
    }

    public static synchronized TypeSerializer getParserForType(Class<?> type) {
        TypeSerializer typeSerializer;
        synchronized (Cache.class) {
            typeSerializer = sModelInfo.getTypeSerializer(type);
        }
        return typeSerializer;
    }

    public static synchronized String getTableName(Class<? extends Model> type) {
        String tableName;
        synchronized (Cache.class) {
            tableName = sModelInfo.getTableInfo(type).getTableName();
        }
        return tableName;
    }
}
