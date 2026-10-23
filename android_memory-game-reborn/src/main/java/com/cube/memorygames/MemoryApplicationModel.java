package com.cube.memorygames;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Handler;
import android.preference.PreferenceManager;
import androidx.multidex.MultiDexApplication;
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
 */
public class MemoryApplicationModel extends MultiDexApplication {
    private static final String PREFERENCE_LAST_GAME_PLAYED = "lastPlayedGameId";
    private static MemoryApplicationModel instance;
    private ArrayList<Integer> allCelldrawableIds;
    private Editor editor;
    private Gson gsonParser;
    private String lastPlayedGameId;
    private LocalDataManager localDataManager;
    private SharedPreferences preferences;
    private Handler uiHandler;

    public void onCreate() {
        super.onCreate();
        instance = this;
        com.cube.memorygames.diagnostics.DiagnosticLog.init(this);
        // Security context first: LocalDataManager loads (and stamps) the
        // User row during initializeDB below and needs the app context for
        // the Keystore-backed integrity keys (hardening design §4).
        com.cube.memorygames.security.SecurityMac.init(this);
        // Prime the release-signature check once (hardening design §5.1);
        // the cached verdict feeds the scattered degradation points.
        com.cube.memorygames.security.IntegritySentinel.isIntact(this);
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






}
