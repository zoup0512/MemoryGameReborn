package com.cube.memorygames.api.online;

import android.content.Context;
import android.content.SharedPreferences;
import java.security.SecureRandom;

/** Device-only safe defaults and explicitly unreviewed drafts. Never used as an online account profile. */
public final class LocalProfile {
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("device_profile",Context.MODE_PRIVATE); }
    private LocalProfile() {}
    public static synchronized String nickname(Context c) {
        String name=prefs(c).getString("nickname",null);
        if(name==null) {
            name=ProfileRules.inspiration(new SecureRandom());
            prefs(c).edit().putString("nickname",name).commit();
        }
        return name;
    }
    public static String avatar(Context c) { return prefs(c).getString("avatar","avatar_1"); }
    public static String draft(Context c) { return prefs(c).getString("draft",nickname(c)); }
    public static boolean saveDraft(Context c,String nickname,String avatar) {
        return ProfileRules.validNickname(nickname) && ProfileRules.validAvatar(avatar)
                && prefs(c).edit().putString("draft",nickname).putString("avatar",avatar).commit();
    }
}
