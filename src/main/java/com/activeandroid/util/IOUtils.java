package com.activeandroid.util;

import android.database.Cursor;
import java.io.Closeable;

public class IOUtils {
    public static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Throwable e) {
                Log.e("Couldn't close closeable.", e);
            }
        }
    }

    public static void closeQuietly(Cursor cursor) {
        if (cursor != null) {
            try {
                cursor.close();
            } catch (Throwable e) {
                Log.e("Couldn't close cursor.", e);
            }
        }
    }
}
