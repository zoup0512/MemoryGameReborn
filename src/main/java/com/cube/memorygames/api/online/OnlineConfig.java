package com.cube.memorygames.api.online;

import android.content.Context;

/**
 * Online PK server endpoint configuration.
 *
 * The production default points at the overseas deployment (see
 * docs/online-pk-prd.md, decision D-2). Debug builds may override the
 * endpoint from the Online lobby (long-press on the lobby title) and the
 * override is stored locally so it survives restarts during device testing.
 */
public final class OnlineConfig {

    /** Production WebSocket endpoint (oversee deployment). */
    public static final String DEFAULT_ENDPOINT = "wss://pk.memorygame.example.com/ws";

    /** LAN test deployment (PRD 6.1 real-device integration testing). */
    public static final String LAN_TEST_ENDPOINT = "ws://192.168.110.140:8090/ws";

    /** Local development endpoint used when a debug override is set. */
    private static final String PREFS = "online_config";
    private static final String KEY_ENDPOINT = "endpoint";

    private OnlineConfig() {
    }

    public static String getEndpoint(Context context) {
        String override = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ENDPOINT, null);
        if (override != null && override.length() > 0) {
            return override;
        }
        // Debuggable builds (sideloaded test APKs) default to the LAN server;
        // release builds go to production.
        boolean debuggable = (context.getApplicationInfo().flags
                & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        return debuggable ? LAN_TEST_ENDPOINT : DEFAULT_ENDPOINT;
    }

    public static void setEndpoint(Context context, String endpoint) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_ENDPOINT, endpoint).apply();
    }

    public static boolean hasOverride(Context context) {
        String override = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ENDPOINT, null);
        return override != null && override.length() > 0;
    }
}
