package com.cube.memorygames;

import android.annotation.SuppressLint;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Exercises the real home-screen hint without changing a normal installation's data. */
public final class DebugCoinsHintActivity extends MainMenuActivity {
    @SuppressLint("AppBundleLocaleChanges")
    @Override protected void onCreate(Bundle state) {
        if (!getPackageName().endsWith(".accounttest")) {
            throw new IllegalStateException("Coin hint previews require the isolated test application");
        }
        Configuration configuration = new Configuration(getResources().getConfiguration());
        DisplayMetrics metrics = new DisplayMetrics();
        metrics.setTo(getResources().getDisplayMetrics());
        String language = getIntent().getStringExtra("previewLocale");
        if (language != null) configuration.setLocale(Locale.forLanguageTag(language));
        float fontScale = getIntent().getFloatExtra("previewFontScale", 0f);
        int widthDp = getIntent().getIntExtra("previewWidthDp", 0);
        if (fontScale > 0f) configuration.fontScale = fontScale;
        if (widthDp > 0) {
            metrics.density = metrics.widthPixels / (float) widthDp;
            metrics.densityDpi = Math.round(metrics.density * 160f);
            configuration.densityDpi = metrics.densityDpi;
            configuration.screenWidthDp = widthDp;
        }
        metrics.scaledDensity = metrics.density * configuration.fontScale;
        getResources().updateConfiguration(configuration, metrics);

        Set<String> games = new HashSet<>();
        int played = getIntent().getIntExtra("previewGamesPlayed", HINT_COINS_GAME_COUNT);
        for (int i = 0; i < played; i++) games.add(Integer.toString(i));
        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putStringSet(PREF_GAMES_PLAYED, games)
                .putBoolean("coinsBubbleShowedPrefs", getIntent().getBooleanExtra("previewAlreadyShown", false))
                .putBoolean("topBubbleShowedPrefs", true)
                .putInt("lastTabPosition", 0)
                .putBoolean(DialogTryWorkout.TRY_WORKOUT_DIALOG_SHOW_KEY, false)
                .apply();
        super.onCreate(state);
    }
}
