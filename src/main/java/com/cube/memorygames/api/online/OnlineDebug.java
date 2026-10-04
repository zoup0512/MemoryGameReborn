package com.cube.memorygames.api.online;

import android.content.Context;

/**
 * Test hook for lengthening online rounds on debuggable builds. The 30-80s
 * production round timers are too fast for automated screenshot-and-tap
 * verification (4-5s per answer), so a fixed test duration can be injected
 * via SharedPreferences ("online_config" / "test_round_ms", value in ms).
 * Release builds and debug builds without the pref are unaffected.
 */
public final class OnlineDebug {

    private static final String PREFS = "online_config";
    private static final String KEY_TEST_ROUND_MS = "test_round_ms";

    private OnlineDebug() {
    }

    /** True when a test round duration is active (debug builds only). */
    public static boolean isTestRoundActive(Context context) {
        return testRoundMs(context) > 0L;
    }

    /**
     * Returns the overridden round duration, or the passed production
     * default when no override is set / the build is a release build.
     */
    public static long roundMs(Context context, long defaultMs) {
        long override = testRoundMs(context);
        return override > 0L ? override : defaultMs;
    }

    /**
     * Debug builds play with a free PK economy: no entry-fee gate in the
     * lobby and no local coin-ledger mirroring, so test matches never
     * drain the single-player balance (server keeps its own books; the LAN
     * test instance runs with entry-fee=0).
     */
    public static boolean freeEconomy(Context context) {
        return (context.getApplicationInfo().flags
                & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    private static long testRoundMs(Context context) {
        boolean debuggable = (context.getApplicationInfo().flags
                & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (!debuggable) {
            return 0L;
        }
        try {
            return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getLong(KEY_TEST_ROUND_MS, 0L);
        } catch (RuntimeException e) {
            return 0L;
        }
    }
}
