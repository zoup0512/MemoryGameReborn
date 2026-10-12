package com.cube.memorygames.api.online;

import android.content.Context;

import java.util.UUID;

/**
 * Guest identity for the online PK mode, stored in SharedPreferences so it
 * survives restarts. Deliberately not persisted in the ActiveAndroid SQLite
 * schema: adding columns to the decompiled LocalUser model would require a
 * DB migration on a fragile restored codebase.
 */
public final class OnlineIdentity {

    private static final String PREFS = "online_identity";
    private static final String KEY_UUID = "uuid";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_NICKNAME = "nickname";
    private static final String KEY_GIFT_GIVEN = "welcomeGiftGiven";

    private OnlineIdentity() {
    }

    public static String getOrCreateUuid(Context context) {
        String uuid = getString(context, KEY_UUID);
        if (uuid == null || uuid.length() == 0) {
            uuid = UUID.randomUUID().toString();
            setString(context, KEY_UUID, uuid);
        }
        return uuid;
    }

    public static String getToken(Context context) {
        return getString(context, KEY_TOKEN);
    }

    public static void saveToken(Context context, String token, long userId) {
        setString(context, KEY_TOKEN, token);
        setString(context, KEY_USER_ID, String.valueOf(userId));
    }

    public static void clearToken(Context context) {
        setString(context, KEY_TOKEN, null);
        setString(context, KEY_USER_ID, null);
    }

    public static long getUserId(Context context) {
        String id = getString(context, KEY_USER_ID);
        if (id == null || id.length() == 0) {
            return -1L;
        }
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    public static String getNickname(Context context) {
        String nickname = getString(context, KEY_NICKNAME);
        if (nickname == null || nickname.length() == 0) {
            nickname = defaultNickname();
            setString(context, KEY_NICKNAME, nickname);
        }
        return nickname;
    }

    public static void setNickname(Context context, String nickname) {
        setString(context, KEY_NICKNAME, nickname);
    }

    /** One-time welcome coins granted on first entry into the PK mode (PRD 3.9). */
    public static boolean consumeWelcomeGift(Context context) {
        if (getBoolean(context, KEY_GIFT_GIVEN, false)) {
            return false;
        }
        setBoolean(context, KEY_GIFT_GIVEN, true);
        return true;
    }

    private static String defaultNickname() {
        return "Player" + (int) (Math.random() * 9000.0d + 1000.0d);
    }

    private static String getString(Context context, String key) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, null);
    }

    private static void setString(Context context, String key, String value) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(key, value).apply();
    }

    private static boolean getBoolean(Context context, String key, boolean def) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(key, def);
    }

    private static void setBoolean(Context context, String key, boolean value) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(key, value).apply();
    }
}
