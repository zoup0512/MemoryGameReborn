package com.activeandroid.content;

import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.util.SparseArray;
import com.activeandroid.ActiveAndroid;
import com.activeandroid.Cache;
import com.activeandroid.Configuration;
import com.activeandroid.Configuration.Builder;
import com.activeandroid.Model;
import com.activeandroid.TableInfo;
import com.yalantis.ucrop.util.FileUtils;
import java.util.ArrayList;
import java.util.List;

public class ContentProvider extends android.content.ContentProvider {
    private static final SparseArray<Class<? extends Model>> TYPE_CODES = new SparseArray();
    private static final UriMatcher URI_MATCHER = new UriMatcher(-1);
    private static String sAuthority;
    private static SparseArray<String> sMimeTypeCache = new SparseArray();

    public boolean onCreate() {
        ActiveAndroid.initialize(getConfiguration());
        sAuthority = getAuthority();
        List<TableInfo> tableInfos = new ArrayList(Cache.getTableInfos());
        int size = tableInfos.size();
        for (int i = 0; i < size; i++) {
            TableInfo tableInfo = (TableInfo) tableInfos.get(i);
            int tableKey = (i * 2) + 1;
            int itemKey = (i * 2) + 2;
            URI_MATCHER.addURI(sAuthority, tableInfo.getTableName().toLowerCase(), tableKey);
            TYPE_CODES.put(tableKey, tableInfo.getType());
            URI_MATCHER.addURI(sAuthority, tableInfo.getTableName().toLowerCase() + "/#", itemKey);
            TYPE_CODES.put(itemKey, tableInfo.getType());
        }
        return true;
    }

    public String getType(Uri uri) {
        int match = URI_MATCHER.match(uri);
        String cachedMimeType = (String) sMimeTypeCache.get(match);
        if (cachedMimeType != null) {
            return cachedMimeType;
        }
        Class<? extends Model> type = getModelType(uri);
        boolean single = match % 2 == 0;
        StringBuilder mimeType = new StringBuilder();
        mimeType.append("vnd");
        mimeType.append(FileUtils.HIDDEN_PREFIX);
        mimeType.append(sAuthority);
        mimeType.append(FileUtils.HIDDEN_PREFIX);
        mimeType.append(single ? "item" : "dir");
        mimeType.append("/");
        mimeType.append("vnd");
        mimeType.append(FileUtils.HIDDEN_PREFIX);
        mimeType.append(sAuthority);
        mimeType.append(FileUtils.HIDDEN_PREFIX);
        mimeType.append(Cache.getTableName(type));
        sMimeTypeCache.append(match, mimeType.toString());
        return mimeType.toString();
    }

    public Uri insert(Uri uri, ContentValues values) {
        Class<? extends Model> type = getModelType(uri);
        Long id = Long.valueOf(Cache.openDatabase().insert(Cache.getTableName(type), null, values));
        if (id == null || id.longValue() <= 0) {
            return null;
        }
        Uri retUri = createUri(type, id);
        notifyChange(retUri);
        return retUri;
    }

    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        int count = Cache.openDatabase().update(Cache.getTableName(getModelType(uri)), values, selection, selectionArgs);
        notifyChange(uri);
        return count;
    }

    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int count = Cache.openDatabase().delete(Cache.getTableName(getModelType(uri)), selection, selectionArgs);
        notifyChange(uri);
        return count;
    }

    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Cursor cursor = Cache.openDatabase().query(Cache.getTableName(getModelType(uri)), projection, selection, selectionArgs, null, null, sortOrder);
        cursor.setNotificationUri(getContext().getContentResolver(), uri);
        return cursor;
    }

    public static Uri createUri(Class<? extends Model> type, Long id) {
        StringBuilder uri = new StringBuilder();
        uri.append("content://");
        uri.append(sAuthority);
        uri.append("/");
        uri.append(Cache.getTableName(type).toLowerCase());
        if (id != null) {
            uri.append("/");
            uri.append(id.toString());
        }
        return Uri.parse(uri.toString());
    }

    protected String getAuthority() {
        return getContext().getPackageName();
    }

    protected Configuration getConfiguration() {
        return new Builder(getContext()).create();
    }

    private Class<? extends Model> getModelType(Uri uri) {
        int code = URI_MATCHER.match(uri);
        if (code != -1) {
            return (Class) TYPE_CODES.get(code);
        }
        return null;
    }

    private void notifyChange(Uri uri) {
        getContext().getContentResolver().notifyChange(uri, null);
    }
}
