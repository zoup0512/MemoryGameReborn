package com.cube.memorygames.api.online;

import android.content.Context;

/**
 * Online PK server endpoint configuration.
 *
 * The default points at the Tencent Cloud deployment (124.220.35.87, systemd
 * memorygames-pk.service, ws :8091). Debug builds may override the endpoint
 * from the Online lobby (long-press on the lobby title) and the override is
 * stored locally so it survives restarts during device testing.
 */
public final class OnlineConfig {

    /**
     * Production WebSocket endpoint: the cloud PK server. Port 8091, not
     * 8090 — 8090 on that box is taken by the Halo blog container.
     */
    public static final String DEFAULT_ENDPOINT = "ws://124.220.35.87:8091/ws";

    /**
     * Test deployment default for debuggable builds: same cloud server, so
     * on-device testing hits the same backend as release. Use the lobby
     * long-press override to point at a LAN box when testing locally.
     */
    public static final String LAN_TEST_ENDPOINT = "ws://124.220.35.87:8091/ws";

    /** Local development endpoint used when a debug override is set. */
    private static final String PREFS = "online_config";
    private static final String KEY_ENDPOINT = "endpoint";

    private OnlineConfig() {
    }

    public static String getEndpoint(Context context) {
        // Release builds ignore any stored override: a repackaged/modified
        // client (or a tampered preferences file) must not be able to steer
        // the release build at an impostor server. Only debuggable builds
        // honor the lobby long-press override (hardening design §3.2).
        boolean debuggable = (context.getApplicationInfo().flags
                & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (!debuggable) {
            return DEFAULT_ENDPOINT;
        }
        String override = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ENDPOINT, null);
        if (override != null && override.length() > 0) {
            return override;
        }
        return LAN_TEST_ENDPOINT;
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
