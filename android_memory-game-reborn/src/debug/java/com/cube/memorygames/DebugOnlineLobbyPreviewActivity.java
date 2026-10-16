package com.cube.memorygames;

import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;

import com.cube.memorygames.api.online.AccountManager;
import com.cube.memorygames.api.online.OnlineMessages;
import com.cube.memorygames.api.online.OnlineSession;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Locale;

/** Local-only UI fixture; synthetic profiles never connect, settle matches, or change credentials. */
public final class DebugOnlineLobbyPreviewActivity extends MainMenuActivity {
    @Override protected void onCreate(Bundle state) {
        if (!getPackageName().endsWith(".accounttest")) {
            throw new IllegalStateException("Online UI preview requires the isolated accounttest application");
        }
        configurePreview();
        OnlineSession session = OnlineSession.get();
        session.init(this);
        session.disconnect();
        setSessionField("reconnectBlocked", true);
        setSessionField("state", OnlineSession.STATE_IDLE);
        setSessionField("lastProfile", previewProfile());
        setSessionField("practiceStreak", 3);
        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putBoolean(DialogTryWorkout.TRY_WORKOUT_DIALOG_SHOW_KEY, false)
                .putBoolean("coinsBubbleShowedPrefs", true)
                .putBoolean("topBubbleShowedPrefs", true).apply();
        getIntent().putExtra(EXTRA_ONLINE, true);
        super.onCreate(state);
        if (getIntent().getBooleanExtra("previewRefresh", false)) {
            getWindow().getDecorView().postDelayed(() -> {
                OnlineMessages.AuthOk profile = session.getLastProfile();
                profile.balance = 777;
                if (session.getMatchListener() != null) {
                    session.getMatchListener().onAuthOk(profile);
                }
            }, getIntent().getIntExtra("previewRefreshDelayMs", 9000));
        }
        if (getIntent().getBooleanExtra("previewRecreate", false) && state == null) {
            getWindow().getDecorView().postDelayed(this::recreate,
                    getIntent().getIntExtra("previewRecreateDelayMs", 14000));
        }
    }

    private void configurePreview() {
        Configuration configuration = new Configuration(getResources().getConfiguration());
        DisplayMetrics metrics = new DisplayMetrics();
        metrics.setTo(getResources().getDisplayMetrics());
        float fontScale = getIntent().getFloatExtra("previewFontScale", 0f);
        int widthDp = getIntent().getIntExtra("previewWidthDp", 0);
        String language = getIntent().getStringExtra("previewLanguage");
        if (fontScale > 0f) configuration.fontScale = fontScale;
        if (widthDp > 0) {
            int windowWidth = Math.round(configuration.screenWidthDp * metrics.density);
            int windowHeight = Math.round(configuration.screenHeightDp * metrics.density);
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Rect bounds = getWindowManager().getCurrentWindowMetrics().getBounds();
                windowWidth = bounds.width();
                windowHeight = bounds.height();
            }
            metrics.density = windowWidth / (float) widthDp;
            metrics.densityDpi = Math.round(metrics.density * 160f);
            metrics.widthPixels = windowWidth;
            metrics.heightPixels = windowHeight;
            configuration.densityDpi = metrics.densityDpi;
            configuration.screenWidthDp = widthDp;
            configuration.screenHeightDp = Math.round(windowHeight / metrics.density);
        }
        if (language != null) configuration.setLocale(new Locale(language));
        metrics.scaledDensity = metrics.density * configuration.fontScale;
        getResources().updateConfiguration(configuration, metrics);
    }

    private OnlineMessages.AuthOk previewProfile() {
        OnlineMessages.AuthOk profile = new OnlineMessages.AuthOk();
        AccountManager.UserProfile cached = AccountManager.get(this).profile();
        profile.userId = cached != null && cached.userId > 0 ? cached.userId : 42L;
        profile.nickname = "UI Preview";
        profile.elo = 1200;
        profile.balance = 500;
        profile.entryFee = 30;
        profile.wins = 99;
        profile.losses = 88;
        profile.draws = 77;
        profile.practiceStreak = 3;
        if (!getIntent().getBooleanExtra("previewLegacy", false)) {
            boolean empty = getIntent().getBooleanExtra("previewEmpty", false);
            profile.modeStats = new LinkedHashMap<>();
            profile.modeStats.put(OnlineSession.MODE_RANKED, stats(empty ? 0 : 8, empty ? 0 : 1, empty ? 0 : 1));
            profile.modeStats.put(OnlineSession.MODE_PRACTICE, stats(empty ? 0 : 2, empty ? 0 : 6, empty ? 0 : 2));
            profile.modeStats.put(OnlineSession.MODE_FRIEND, stats(empty ? 0 : 1, empty ? 0 : 2, empty ? 0 : 1));
        }
        OnlineMessages.PracticeTier tier = new OnlineMessages.PracticeTier();
        tier.id = "BRONZE";
        tier.elo = 1000;
        tier.winCoins = 20;
        profile.practiceTiers = new OnlineMessages.PracticeTier[] {tier};
        return profile;
    }

    private static OnlineMessages.MatchStats stats(long wins, long losses, long draws) {
        OnlineMessages.MatchStats stats = new OnlineMessages.MatchStats();
        stats.wins = wins;
        stats.losses = losses;
        stats.draws = draws;
        return stats;
    }

    private static void setSessionField(String name, Object value) {
        try {
            Field field = OnlineSession.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(OnlineSession.get(), value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
