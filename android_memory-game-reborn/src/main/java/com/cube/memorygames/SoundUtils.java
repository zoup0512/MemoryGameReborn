package com.cube.memorygames;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.preference.PreferenceManager;
import com.memory.brain.training.games.R;
import com.mopub.volley.DefaultRetryPolicy;
import java.util.HashMap;
import java.util.Map;

public class SoundUtils {
    private static final float CURRENT_VOLUME = 0.2f;
    private static final int MAX_VOLUME = 100;
    private static final String PREF_SOUND_ENABLED = "soundEnabled";
    private static MediaPlayer mediaPlayer;
    private static Boolean playSound = null;
    private static SoundPool soundPool;
    private static Map<SOUND, Integer> soundPoolMap;

    public enum SOUND {
        WIN,
        FAIL,
        TAP,
        WRONG
    }

    public static boolean isPlaySound(Context context) {
        if (playSound == null) {
            playSound = Boolean.valueOf(PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREF_SOUND_ENABLED, true));
        }
        return playSound.booleanValue();
    }

    public static void setPlaySound(Context context, boolean playSound) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(PREF_SOUND_ENABLED, playSound).apply();
        playSound = Boolean.valueOf(playSound);
        if (playSound) {
            playBackgroundSound(context);
        } else {
            stopBackgroundSound();
        }
    }

    public static void initSounds(Context context) {
        soundPool = new SoundPool(2, 3, 100);
        soundPoolMap = new HashMap();
        soundPoolMap.put(SOUND.FAIL, Integer.valueOf(soundPool.load(context, R.raw.error, 1)));
        soundPoolMap.put(SOUND.TAP, Integer.valueOf(soundPool.load(context, R.raw.tap, 2)));
        soundPoolMap.put(SOUND.WIN, Integer.valueOf(soundPool.load(context, R.raw.win, 3)));
        soundPoolMap.put(SOUND.WRONG, Integer.valueOf(soundPool.load(context, R.raw.wrong, 4)));
    }

    public static void playBackgroundSound(Context context) {
    }

    public static void stopBackgroundSound() {
    }

    public static void playSound(Context context, SOUND sound) {
        if (soundPool == null || soundPoolMap == null) {
            initSounds(context);
        }
        if (isPlaySound(context)) {
            soundPool.play(((Integer) soundPoolMap.get(sound)).intValue(), CURRENT_VOLUME, CURRENT_VOLUME, 1, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT);
        }
    }
}
