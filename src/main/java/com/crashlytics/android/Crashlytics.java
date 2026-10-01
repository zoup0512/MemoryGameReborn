package com.crashlytics.android;

/**
 * Local replacement for the original Crashlytics facade. All reporting calls
 * are intentionally inert: the restored build keeps no network or analytics
 * backends. Exceptions surface through the standard log only.
 */
public final class Crashlytics {
    private Crashlytics() {
    }

    public static void logException(Throwable throwable) {
        if (throwable != null) {
            android.util.Log.w("CrashlyticsStub", throwable);
        }
    }

    public static void log(String message) {
        android.util.Log.d("CrashlyticsStub", String.valueOf(message));
    }
}
