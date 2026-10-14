package com.activeandroid;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;
import com.activeandroid.util.IOUtils;
import com.activeandroid.util.Log;
import com.activeandroid.util.NaturalOrderComparator;
import com.activeandroid.util.SQLiteUtils;
import com.activeandroid.util.SqlParser;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class DatabaseHelper extends SQLiteOpenHelper {
    public static final String MIGRATION_PATH = "migrations";
    private final String mSqlParser;

    public DatabaseHelper(Configuration configuration) {
        super(configuration.getContext(), configuration.getDatabaseName(), null, configuration.getDatabaseVersion());
        copyAttachedDatabase(configuration.getContext(), configuration.getDatabaseName());
        this.mSqlParser = configuration.getSqlParser();
    }

    public void onOpen(SQLiteDatabase db) {
        executePragmas(db);
    }

    public void onCreate(SQLiteDatabase db) {
        executePragmas(db);
        executeCreate(db);
        executeMigrations(db, -1, db.getVersion());
        executeCreateIndex(db);
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        executePragmas(db);
        executeCreate(db);
        executeMigrations(db, oldVersion, newVersion);
    }

    public void copyAttachedDatabase(Context context, String databaseName) {
        File dbPath = context.getDatabasePath(databaseName);
        if (!dbPath.exists()) {
            dbPath.getParentFile().mkdirs();
            try {
                InputStream inputStream = context.getAssets().open(databaseName);
                OutputStream output = new FileOutputStream(dbPath);
                byte[] buffer = new byte[8192];
                while (true) {
                    int length = inputStream.read(buffer, 0, 8192);
                    if (length > 0) {
                        output.write(buffer, 0, length);
                    } else {
                        output.flush();
                        output.close();
                        inputStream.close();
                        return;
                    }
                }
            } catch (Throwable e) {
                Log.e("Failed to open file", e);
            }
        }
    }

    private void executePragmas(SQLiteDatabase db) {
        if (SQLiteUtils.FOREIGN_KEYS_SUPPORTED) {
            db.execSQL("PRAGMA foreign_keys=ON;");
            Log.i("Foreign Keys supported. Enabling foreign key features.");
        }
    }

    private void executeCreateIndex(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            for (TableInfo tableInfo : Cache.getTableInfos()) {
                for (String definition : SQLiteUtils.createIndexDefinition(tableInfo)) {
                    db.execSQL(definition);
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void executeCreate(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            for (TableInfo tableInfo : Cache.getTableInfos()) {
                db.execSQL(SQLiteUtils.createTableDefinition(tableInfo));
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private boolean executeMigrations(SQLiteDatabase db, int oldVersion, int newVersion) {
        boolean migrationExecuted = false;
        try {
            List<String> files = Arrays.asList(Cache.getContext().getAssets().list(MIGRATION_PATH));
            Collections.sort(files, new NaturalOrderComparator());
            db.beginTransaction();
            try {
                for (String file : files) {
                    int version = Integer.valueOf(file.replace(".sql", "")).intValue();
                    if (version > oldVersion && version <= newVersion) {
                        executeSqlScript(db, file);
                        migrationExecuted = true;
                        Log.i(file + " executed succesfully.");
                    }
                }
                db.setTransactionSuccessful();
                db.endTransaction();
            } catch (Throwable e) {
                Log.w("Skipping invalidly named file.", e);
                db.endTransaction();
            }
        } catch (Throwable e2) {
            Log.e("Failed to execute migrations.", e2);
        }
        return migrationExecuted;
    }

    /**
     * Restored implementation. The decompiled source carried a JADX stub here
     * ("Method not decompiled" / UnsupportedOperationException), which made
     * every migration a silent no-op: the throw was swallowed, the enclosing
     * transaction was poisoned and rolled back, and the schema never changed.
     * The two helpers below (executeDelimitedSqlScript / executeLegacySqlScript)
     * were intact, so the original logic could be restored faithfully from the
     * stub's own bytecode comments. The hardening DB v17 change (User.syncTag)
     * depends on this actually running on upgrade installs.
     */
    private void executeSqlScript(SQLiteDatabase db, String file) {
        InputStream stream = null;
        try {
            stream = Cache.getContext().getAssets().open(MIGRATION_PATH + "/" + file);
            if ("delimited".equalsIgnoreCase(this.mSqlParser)) {
                executeDelimitedSqlScript(db, stream);
            } else {
                executeLegacySqlScript(db, stream);
            }
        } catch (IOException e) {
            Log.e("Failed to execute " + file, e);
        } finally {
            IOUtils.closeQuietly(stream);
        }
    }

    private void executeDelimitedSqlScript(SQLiteDatabase db, InputStream stream) throws IOException {
        for (String command : SqlParser.parse(stream)) {
            db.execSQL(command);
        }
    }

    private void executeLegacySqlScript(SQLiteDatabase db, InputStream stream) throws IOException {
        Throwable th;
        Closeable reader = null;
        Closeable buffer = null;
        try {
            Reader reader2 = new InputStreamReader(stream);
            try {
                BufferedReader buffer2 = new BufferedReader(reader2);
                while (true) {
                    try {
                        String line = buffer2.readLine();
                        if (line != null) {
                            line = line.replace(";", "").trim();
                            if (!TextUtils.isEmpty(line)) {
                                db.execSQL(line);
                            }
                        } else {
                            IOUtils.closeQuietly(buffer2);
                            IOUtils.closeQuietly(reader2);
                            return;
                        }
                    } catch (Throwable th2) {
                        th = th2;
                        buffer = buffer2;
                        reader = reader2;
                    }
                }
            } catch (Throwable th3) {
                th = th3;
                reader = reader2;
                IOUtils.closeQuietly(buffer);
                IOUtils.closeQuietly(reader);
                throw new RuntimeException(th);
            }
        } catch (Throwable th4) {
            th = th4;
            IOUtils.closeQuietly(buffer);
            IOUtils.closeQuietly(reader);
            throw new RuntimeException(th);
        }
    }
}
