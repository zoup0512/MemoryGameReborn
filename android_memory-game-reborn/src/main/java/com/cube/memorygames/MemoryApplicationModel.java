package com.cube.memorygames;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.multidex.MultiDexApplication;
import android.text.TextUtils;
import com.activeandroid.ActiveAndroid;
import com.activeandroid.Configuration;
import com.memory.brain.training.games.R.drawable;
import com.cube.memorygames.activity.OnShowMoneyDialogListener;
import com.cube.memorygames.api.local.LocalDataManager;
import com.cube.memorygames.api.local.challenge.ChallengeDbGame;
import com.cube.memorygames.api.local.model.LocalCoinsTransaction;
import com.cube.memorygames.api.local.model.LocalGameSession;
import com.cube.memorygames.api.local.model.LocalGameStats;
import com.cube.memorygames.api.local.model.LocalUser;
import com.cube.memorygames.api.local.model.LocalWorkoutGame;
import com.google.gson.Gson;
import com.memory.brain.training.games.R;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Application model of the restored offline build.
 *
 * The original app initialized Fabric/Crashlytics, Facebook, Branch deep
 * links, Google Analytics and a retrofit based sync service here; all of
 * that was removed together with every network permission. What remains is
 * the purely local gameplay backbone: the ActiveAndroid database, sound
 * pool, shared preferences and the Gson parser.
 *
 * logEvent is kept as a no-op so that the untouched analytics call sites in
 * the game logic keep working unchanged.
 */
public class MemoryApplicationModel extends MultiDexApplication {
    public static final String ANALYTICS_CATEGORY_ADS = "Ads";
    public static final String ANALYTICS_CATEGORY_BONUSES = "Bonuses";
    public static final String ANALYTICS_CATEGORY_CHALLENGE = "Challenge";
    public static final String ANALYTICS_CATEGORY_GAMEPLAY_PREFIX = "Gameplay. Game ";
    public static final String ANALYTICS_CATEGORY_GENERAL = "General";
    public static final String ANALYTICS_CATEGORY_OFFERS = "Offers";
    public static final String ANALYTICS_CATEGORY_ONLINE = "Online Games";
    public static final String ANALYTICS_CATEGORY_PROMO = "Promo";
    public static final String ANALYTICS_CATEGORY_RATING = "Rating";
    public static final String ANALYTICS_CATEGORY_REMINDERS = "Reminders";
    public static final String ANALYTICS_CATEGORY_SUGGESTION = "Game Suggestions";
    public static final String ANALYTICS_CATEGORY_UNLOCK = "Unlock";
    public static final String ANALYTICS_CATEGORY_WORKOUT = "Workout";
    public static final String ANALYTICS_EVENT_CHALLENGE_GAME_STARTED = "Game Started ";
    public static final String ANALYTICS_EVENT_CHALLENGE_NEW_LEVEL = "New Level ";
    public static final String ANALYTICS_EVENT_CHALLENGE_PREV_LEVEL = "PREV Level ";
    public static final String ANALYTICS_EVENT_CHALLENGE_TAB_CLICKED = "Challenge Tab Clicked";
    public static final String ANALYTICS_EVENT_GAME_NEW_TROPHY = "New Trophy";
    public static final String ANALYTICS_EVENT_GAME_OPEN_TROPHIES = "Open Trophies";
    public static final String ANALYTICS_EVENT_GAME_STARTED_PREFIX = "Started Game ";
    public static final String ANALYTICS_EVENT_LEVEL_REACHED_PREFIX = "User reached level ";
    public static final String ANALYTICS_EVENT_NICK_ENTERED = "Nick entered";
    public static final String ANALYTICS_EVENT_NICK_SKIPPED = "Nick skipped";
    public static final String ANALYTICS_EVENT_OFFER_NEW_PRO = "Offer Dialog. New. Pro. Showed";
    public static final String ANALYTICS_EVENT_OFFER_NEW_PRO_CLICKED = "Offer Dialog. New. Pro. Clicked";
    public static final String ANALYTICS_EVENT_OFFER_NEW_PRO_DISCOUNT = "Offer Dialog. New. Pro Discount. Showed";
    public static final String ANALYTICS_EVENT_OFFER_NEW_PRO_DISCOUNT_CLICKED = "Offer Dialog. New. Pro Discount. Clicked";
    public static final String ANALYTICS_EVENT_OFFER_NEW_PRO_DISCOUNT_SUCCESS = "Offer Dialog. New. Pro Discount. Success";
    public static final String ANALYTICS_EVENT_OFFER_NEW_PRO_SUCCESS = "Offer Dialog. New. Pro. Success";
    public static final String ANALYTICS_EVENT_OFFER_NEW_REMOVE_ADS = "Offer Dialog. New. Remove Ads. Showed";
    public static final String ANALYTICS_EVENT_OFFER_NEW_REMOVE_ADS_CLICKED = "Offer Dialog. New. Remove Ads. Clicked";
    public static final String ANALYTICS_EVENT_OFFER_NEW_REMOVE_ADS_SUCCESS = "Offer Dialog. New. Remove Ads. Success";
    public static final String ANALYTICS_EVENT_OFFER_NEW_SECRET_GAME = "Offer Dialog. New. Secret Game. Showed";
    public static final String ANALYTICS_EVENT_OFFER_NEW_SECRET_GAME_CLICKED = "Offer Dialog. New. Secret Game. Clicked";
    public static final String ANALYTICS_EVENT_OFFER_NEW_SECRET_GAME_SUCCESS = "Offer Dialog. New. Secret Game. Success";
    public static final String ANALYTICS_EVENT_OFFER_OLD_PRO = "Offer Dialog. Old. Pro. Showed";
    public static final String ANALYTICS_EVENT_OFFER_OLD_PRO_DISCOUNT = "Offer Dialog. Old. Pro Discount. Showed";
    public static final String ANALYTICS_EVENT_OFFER_OLD_REMOVE_ADS = "Offer Dialog. Old. Remove Ads. Showed";
    public static final String ANALYTICS_EVENT_OFFER_OLD_SECRET_GAME = "Offer Dialog. Old. Secret Game. Showed";
    public static final String ANALYTICS_EVENT_OFFER_OLD_UNLIMITED_ONLINE = "Offer Dialog. Old. Unlimited Online. Showed";
    public static final String ANALYTICS_EVENT_REMINDER_SHOWED = "Reminder Showed ";
    public static final String ANALYTICS_EVENT_REMINDER_WORKED = "Reminder Worked ";
    public static final String ANALYTICS_EVENT_REPLAY_USED_PREFIX = "Replay activated. Game ";
    public static final String ANALYTICS_EVENT_SOUND_OFF = "Sound OFF";
    public static final String ANALYTICS_EVENT_SOUND_ON = "Sound ON";
    public static final String ANALYTICS_EVENT_SUGGESTION_CLICKED_PREFIX = "Suggestion Clicked. ";
    public static final String ANALYTICS_EVENT_TOP_CLICKED = "Top clicked";
    public static final String ANALYTICS_EVENT_TRY_UNLOCK_GAME_PREFIX = "Unlock Clicked. ";
    public static final String ANALYTICS_EVENT_UNLOCKED_GAME_PREFIX = "Unlocked. game ";
    public static final String ANALYTICS_EVENT_WORKOUT_DAY_GENERATED = "New Day Generated";
    public static final String ANALYTICS_EVENT_WORKOUT_DIALOG_CLICKED = "Dialog Clicked";
    public static final String ANALYTICS_EVENT_WORKOUT_DIALOG_SHOWED = "Dialog Showed";
    public static final String ANALYTICS_EVENT_WORKOUT_FINISHED = "Workout Finished";
    public static final String ANALYTICS_EVENT_WORKOUT_GAME_STARTED = "Game Started ";
    public static final String ANALYTICS_EVENT_WORKOUT_HELP_CLICKED = "Help Clicked";
    public static final String ANALYTICS_EVENT_WORKOUT_NEW_LEVEL = "New Level ";
    public static final String ANALYTICS_EVENT_WORKOUT_TAB_CLICKED = "Workout Tab Clicked";
    private static final String PREFERENCE_LAST_GAME_PLAYED = "lastPlayedGameId";
    private static MemoryApplicationModel instance;
    private ArrayList<Integer> allCelldrawableIds;
    private Editor editor;
    private Gson gsonParser;
    private boolean isNicknameSkipped = false;
    private String lastPlayedGameId;
    private LocalDataManager localDataManager;
    private OnShowMoneyDialogListener onShowMoneyDialogListener;
    private SharedPreferences preferences;
    private Handler uiHandler;

    public void onCreate() {
        super.onCreate();
        instance = this;
        initializeDB();
        SoundUtils.initSounds(getApplicationContext());
        this.localDataManager = new LocalDataManager();
        initWithPreferences();
        this.gsonParser = new Gson();
        this.uiHandler = new Handler();
    }

    protected void initializeDB() {
        Configuration.Builder configurationBuilder = new Configuration.Builder(this);
        configurationBuilder.addModelClass(LocalUser.class);
        configurationBuilder.addModelClass(LocalGameSession.class);
        configurationBuilder.addModelClass(LocalCoinsTransaction.class);
        configurationBuilder.addModelClass(LocalGameStats.class);
        configurationBuilder.addModelClass(LocalWorkoutGame.class);
        configurationBuilder.addModelClass(ChallengeDbGame.class);
        ActiveAndroid.initialize(configurationBuilder.create());
    }

    public void onTerminate() {
        super.onTerminate();
        ActiveAndroid.dispose();
    }

    /** No-op: the restored build has no analytics backend. */
    public void logEvent(String screenName, String category, String action) {
    }

    /** No-op: the restored build has no analytics backend. */
    public void logEvent(Activity activity, String category, String action) {
    }

    public static MemoryApplicationModel getInstance() {
        return instance;
    }

    public List<Integer> getAllCellDrawableIds() {
        if (this.allCelldrawableIds == null) {
            this.allCelldrawableIds = new ArrayList();
            for (Field f : drawable.class.getFields()) {
                try {
                    if (f.getName().contains("icg_")) {
                        this.allCelldrawableIds.add(Integer.valueOf(f.getInt(null)));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return this.allCelldrawableIds;
    }

    public LocalDataManager getLocalDataManager() {
        return this.localDataManager;
    }

    private void initWithPreferences() {
        this.preferences = PreferenceManager.getDefaultSharedPreferences(this);
        this.editor = this.preferences.edit();
        this.lastPlayedGameId = this.preferences.getString(PREFERENCE_LAST_GAME_PLAYED, "0");
    }

    public String getLastPlayedGameId() {
        return this.lastPlayedGameId;
    }

    public void setLastGamePlayed(String lastPlayedGameId) {
        this.lastPlayedGameId = lastPlayedGameId;
        this.editor.putString(PREFERENCE_LAST_GAME_PLAYED, lastPlayedGameId).commit();
    }

    public boolean isNicknameSkipped() {
        return this.isNicknameSkipped;
    }

    public void setNicknameSkipped(boolean nicknameSkipped) {
        this.isNicknameSkipped = nicknameSkipped;
    }

    public Gson getGsonParser() {
        return this.gsonParser;
    }

    public OnShowMoneyDialogListener getOnShowMoneyDialogListener() {
        return this.onShowMoneyDialogListener;
    }

    public void setOnShowMoneyDialogListener(OnShowMoneyDialogListener onShowMoneyDialogListener) {
        this.onShowMoneyDialogListener = onShowMoneyDialogListener;
    }

    public boolean isNetworkAvailable() {
        return false;
    }
}
