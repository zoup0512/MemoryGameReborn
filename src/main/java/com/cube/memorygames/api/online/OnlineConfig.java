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

    /** Production WebSocket endpoint (overseas deployment). */
    public static final String DEFAULT_ENDPOINT = "wss://pk.memorygame.example.com/ws";

    /** Local development endpoint used when a debug override is set. */
    private static final String PREFS = "online_config";
    private static final String KEY_ENDPOINT = "endpoint";

    private OnlineConfig() {
    }

    public static String getEndpoint(Context context) {
        String override = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ENDPOINT, null);
        return override != null && override.length() > 0 ? override : DEFAULT_ENDPOINT;
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
