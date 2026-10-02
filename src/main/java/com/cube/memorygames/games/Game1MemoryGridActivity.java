package com.cube.memorygames.games;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.DialogInterface.OnShowListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatDelegate;
import android.text.Html;
import android.view.Display;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TextView;
import android.widget.ToggleButton;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.cube.memorygames.ChallengeResultDialog;
import com.cube.memorygames.FailDialog;
import com.cube.memorygames.FailDialog.OnDialogClickListener;
import com.cube.memorygames.Games;
import com.cube.memorygames.MainMenuActivity;
import com.cube.memorygames.MemoryApplicationModel;
import com.cube.memorygames.Progression;
import com.cube.memorygames.SharingDialog;
import com.cube.memorygames.SharingDialog.IabStatus;
import com.cube.memorygames.SharingDialog.StatisticListener;
import com.cube.memorygames.SoundUtils;
import com.cube.memorygames.SoundUtils.SOUND;
import com.cube.memorygames.StartGameActivity;
import com.cube.memorygames.api.local.LocalDataManager;
import com.cube.memorygames.api.local.challenge.ChallengeJsonGame;
import com.cube.memorygames.api.local.model.LocalGameSession;
import com.cube.memorygames.api.local.model.LocalGameStats;
import com.cube.memorygames.api.local.model.LocalUser;
import com.cube.memorygames.api.local.workout.LevelInfo;
import com.cube.memorygames.api.online.OnlineHud;
import com.cube.memorygames.api.online.OnlineSession;
import com.cube.memorygames.logic.GameFlowState;
import com.cube.memorygames.logic.GameFlowStateTimer;
import com.cube.memorygames.logic.GameProgression1;
import com.cube.memorygames.logic.GameRandom;
import com.cube.memorygames.model.GameInfo;
import com.cube.memorygames.reminder.AppReminder;
import com.cube.memorygames.ui.GameGrid;
import com.cube.memorygames.ui.GridEventsListener;
import com.cube.memorygames.ui.PauseDialog;
import com.cube.memorygames.ui.RectangularGrid;
import com.cube.memorygames.ui.TimerView;
import com.cube.memorygames.ui.TrophyDialog;
import com.facebook.appevents.AppEventsConstants;
import com.memory.brain.training.games.R;
import com.squareup.picasso.Picasso;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimerTask;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

public class Game1MemoryGridActivity extends AppCompatActivity implements StatisticListener, GridEventsListener, IabStatus {
    public static final int ADS_INTERVAL_SPRINT_FIRST = 2;
    public static final int ADS_INTERVAL_SPRINT_SECOND = 3;
    private static final int ADS_TYPE_CHALLENGE_HOME = 31;
    private static final int ADS_TYPE_CHALLENGE_NEXT = 33;
    private static final int ADS_TYPE_CHALLENGE_OPEN_DIALOG = 34;
    private static final int ADS_TYPE_CHALLENGE_REPLAY = 32;
    private static final int ADS_TYPE_SPRINT_NONE = 10;
    private static final int ADS_TYPE_SPRINT_TIMEOUT_BACKPRESS = 24;
    private static final int ADS_TYPE_SPRINT_TIMEOUT_GAME_SUGGESTION = 21;
    private static final int ADS_TYPE_SPRINT_TIMEOUT_MAIN_MENU = 22;
    private static final int ADS_TYPE_SPRINT_TIMEOUT_RETRY = 23;
    public static final int BUYSTARS_AMOUNT = 30;
    public static final String BUY_STARS_CLICKED_COUNT = "buyStarsClickedCountCount";
    public static final int FADE_ANIMATION_DURATION = 300;
    public static final int FINISH_ANIMATION_DURATION = 300;
    public static final int FINISH_FADE_ANIMATION_DURATION = 200;
    public static final int GAME_TIME_ONLINE = 45000;
    public static final int MAX_BUY_STARS_CLICKED = 7;
    public static final String MAX_LEVEL = "maxLevel";
    public static final int MAX_PAUSE_COUNT = 5;
    private static final float MIN_ADVANCED_HIGH_LEVEL_PASS_KOEF = 0.9f;
    private static final float MIN_ADVANCED_LEVEL_PASS_KOEF = 0.8f;
    public static final int MIN_GOAL_LEVEL = 6;
    protected static final double MIN_GRID_SIZE = 5.0d;
    private static final double MIN_LEVEL_PASS_KOEF = 0.665d;
    public static final String ONLINE_PROGRESS_DELAY = "onlineProgressDelay";
    public static final String ONLINE_REPLAYED = "onlineREplayed";
    public static final String ONLINE_SCORE = "onlineScore";
    public static final String PAUSED_COUNT = "pausedCount";
    public static final String PAUSED_DURATION = "pausedDuration";
    public static final String PAUSED_TIME = "pausedTime";
    public static final String PREF_ADS_SPRINT_LAST_NUMBER = "CountBeforeAdLastNumber";
    public static final String PREF_COUNT_BEFORE_ADS_SPRINT = "CountBeforeAdSprint";
    private static final int REPLAY_LEVEL_DELTA = 5;
    public static final String SELECTED_AD_TYPE = "selectedAdType";
    public static final int START_ANIMATION_DURATION = 400;
    public static final String USER_SCORE = "userScore";
    public static final String WATCHED_COUNT = "watchedCount";
    protected static int miniLevelTime = 3100;
    protected MemoryApplicationModel application;
    OnClickListener backPressClickListener = new OnClickListener() {
        public void onClick(View v) {
            Game1MemoryGridActivity.this.openMainMenu();
        }
    };
    protected int buyStarsClickedCountCount = 0;
    protected int challengeDuration = 1200;
    OnClickListener challengeHomeClickListener = new OnClickListener() {
        public void onClick(View v) {
            Game1MemoryGridActivity.this.challengeResultDialog = null;
            Game1MemoryGridActivity.this.openMainMenu();
        }
    };
    protected ChallengeJsonGame challengeJsonGame;
    OnClickListener challengeNextClickListener = new OnClickListener() {
        public void onClick(View v) {
            Game1MemoryGridActivity.this.challengeResultDialog = null;
            Intent intent = new Intent(Game1MemoryGridActivity.this, MainMenuActivity.class);
            intent.setFlags(335544320);
            intent.putExtra(MainMenuActivity.EXTRA_CHALLENGE_GAME_INFO, Game1MemoryGridActivity.this.challengeJsonGame);
            Game1MemoryGridActivity.this.startActivity(intent);
        }
    };
    OnClickListener challengeOpenDialogClickListener = new OnClickListener() {
        public void onClick(View v) {
            if (Game1MemoryGridActivity.this.challengeResultDialog != null) {
                Game1MemoryGridActivity.this.challengeResultDialog.show();
            } else {
                Game1MemoryGridActivity.this.openMainMenu();
            }
        }
    };
    OnClickListener challengeReplayClickListener = new OnClickListener() {
        public void onClick(View v) {
            Game1MemoryGridActivity.this.challengeResultDialog = null;
            Games.get().startChallengeGame(Game1MemoryGridActivity.this, Game1MemoryGridActivity.this.challengeJsonGame, Game1MemoryGridActivity.this.gameInfo);
            Game1MemoryGridActivity.this.finish();
        }
    };
    ChallengeResultDialog challengeResultDialog;
    @BindView(R.id.correct_item)
    ImageView correctItem;
    @BindView(R.id.foreground)
    View foreground;
    protected GameInfo gameInfo;
    @BindView(R.id.game_name)
    TextView gameName;
    protected long gameSeed = System.currentTimeMillis();
    protected LocalGameSession gameSession;
    protected GameGrid grid;
    protected ViewGroup gridContainer;
    protected boolean isChallenge;
    protected boolean isOnlineGame;
    protected boolean isWorkout;
    @BindView(R.id.level_hint)
    protected View levelHint;
    @BindView(R.id.level_number)
    TextView levelNumber;
    private long levelOnlineTime;
    @BindView(R.id.life1)
    View life1;
    @BindView(R.id.life2)
    View life2;
    protected LocalDataManager localDataManager;
    protected int maxLevel;
    protected int onlineGameNumber;
    protected int onlineProgressDelay;
    private boolean onlineReplayed;
    protected int onlineScore;
    protected int onlineTotalGames;
    @BindView(R.id.panel_lives)
    View panelLives;
    @BindView(R.id.panel_pause)
    View panelPause;
    @BindView(R.id.pauseButton)
    ImageView pauseButton;
    protected long pausedCount;
    protected long pausedDuration;
    protected long pausedTime;
    @BindView(R.id.progressBar)
    protected ProgressBar progressBar;
    protected Progression progression;
    private int readyPhaseDuration = 800;
    @BindView(R.id.root)
    View root;
    @BindView(R.id.sound)
    ToggleButton soundToggle;
    private int startDialogDelay = 1000;
    private long startedOnlineTime;
    protected GameFlowStateTimer stateTimer;
    @BindView(R.id.text_level_ready)
    protected TextView textLevelReady;
    protected TimeoutDialog timeoutDialog;
    @BindView(R.id.timerContainer)
    protected View timerContainer;
    private Handler timerOnlineHandler = new Handler();
    private Runnable timerOnlineRunnable = new Runnable() {
        public void run() {
            final long time = Game1MemoryGridActivity.this.levelOnlineTime - (System.currentTimeMillis() - Game1MemoryGridActivity.this.startedOnlineTime);
            if (time > 0) {
                Game1MemoryGridActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        Game1MemoryGridActivity.this.updateOnlineTimerText(time);
                    }
                });
                Game1MemoryGridActivity.this.timerOnlineHandler.postDelayed(this, 20);
                return;
            }
            Game1MemoryGridActivity.this.startedOnlineTime = 0;
            Game1MemoryGridActivity.this.progressBar.setVisibility(4);
            if (!Game1MemoryGridActivity.this.isFinishing()) {
                Game1MemoryGridActivity.this.showOnlineReplayDialog();
            }
        }
    };
    @BindView(R.id.timerView)
    TimerView timerView;
    protected int userScore;
    protected int videoWatchedCount = 0;

    protected class GridAnimationFlowState implements GameFlowState {
        protected GridAnimationFlowState() {
        }

        public void applyState() {
            Game1MemoryGridActivity.this.textLevelReady.setText(R.string.level_ready);
            Game1MemoryGridActivity.this.levelHint.setVisibility(8);
            Game1MemoryGridActivity.this.timerContainer.setVisibility(8);
            Game1MemoryGridActivity.this.disablePausePanel();
            Game1MemoryGridActivity.this.grid.animateCells();
        }

        public int getDuration() {
            return SharingDialog.DOLLAR1_COINS;
        }
    }

    public class ReadyFlowState implements GameFlowState {
        public void applyState() {
            if (Game1MemoryGridActivity.this.isOnlineGame) {
                Game1MemoryGridActivity.this.levelHint.setVisibility(8);
            } else {
                Game1MemoryGridActivity.this.textLevelReady.setText(R.string.level_ready);
                Game1MemoryGridActivity.this.levelHint.setVisibility(8);
                Game1MemoryGridActivity.this.timerContainer.setVisibility(0);
                Game1MemoryGridActivity.this.enablePausePanel();
                Game1MemoryGridActivity.this.timerView.showTimer(Game1MemoryGridActivity.this.readyPhaseDuration, false);
            }
            Game1MemoryGridActivity.this.enablePausePanel();
        }

        public int getDuration() {
            if (Game1MemoryGridActivity.this.isOnlineGame) {
                return 0;
            }
            return Game1MemoryGridActivity.this.readyPhaseDuration;
        }
    }

    protected class ShowChallengeFlowState implements GameFlowState {
        protected ShowChallengeFlowState() {
        }

        public void applyState() {
            Game1MemoryGridActivity.this.showChallenge();
        }

        public int getDuration() {
            return Game1MemoryGridActivity.this.challengeDuration;
        }
    }

    class TimeoutDialog extends Dialog {
        @BindView(R.id.fail_dialog_inner_title)
        TextView failDialogInnerTitle;
        @BindView(R.id.fail_dialog_title)
        TextView failDialogTitle;
        View.OnClickListener gameSuggestionClickListener = new View.OnClickListener() {
            public void onClick(View view) {
                if (TimeoutDialog.this.selectedSuggestedGame == null) {
                    TimeoutDialog.this.generateSuggestion();
                }
                Game1MemoryGridActivity.this.application.logEvent(Game1MemoryGridActivity.this, MemoryApplicationModel.ANALYTICS_CATEGORY_SUGGESTION, MemoryApplicationModel.ANALYTICS_EVENT_SUGGESTION_CLICKED_PREFIX + TimeoutDialog.this.selectedSuggestedGame.getId());
                Intent intent = new Intent();
                intent.putExtra(MainMenuActivity.EXTRA_GAME_INFO, TimeoutDialog.this.selectedSuggestedGame);
                intent.setClass(Game1MemoryGridActivity.this, StartGameActivity.class);
                Game1MemoryGridActivity.this.startActivity(intent);
                Game1MemoryGridActivity.this.application.setLastGamePlayed(TimeoutDialog.this.selectedSuggestedGame.getId());
                SoundUtils.stopBackgroundSound();
                Game1MemoryGridActivity.this.finish();
            }
        };
        @BindView(R.id.fail_dialog_game_suggestion_image)
        ImageButton gameSuggestionImage;
        @BindView(R.id.fail_dialog_game_suggestion_game_name)
        TextView gameSuggestionName;
        @BindView(R.id.fail_dialog_game_suggestion_container)
        View gameSuggestionsContainer;
        @BindView(R.id.mainMenu)
        TextView mainMenu;
        View.OnClickListener mainMenuClickListener = new View.OnClickListener() {
            public void onClick(View view) {
                if (Game1MemoryGridActivity.this.progression.getLevelNumber() > Game1MemoryGridActivity.this.gameSession.endLevel) {
                    Game1MemoryGridActivity.this.gameSession.endLevel = Game1MemoryGridActivity.this.progression.getLevelNumber();
                }
                Game1MemoryGridActivity.this.saveGameSession();
                TimeoutDialog.this.dismiss();
                Game1MemoryGridActivity.this.openMainMenu();
            }
        };
        @BindView(R.id.fail_dialog_restart_title)
        TextView restartTitle;
        View.OnClickListener retryClickListener = new View.OnClickListener() {
            public void onClick(View view) {
                String mode;
                Game1MemoryGridActivity.this.panelPause.setVisibility(0);
                TimeoutDialog.this.dismiss();
                Game1MemoryGridActivity.this.application.logEvent(Game1MemoryGridActivity.this, MemoryApplicationModel.ANALYTICS_CATEGORY_GENERAL, MemoryApplicationModel.ANALYTICS_EVENT_GAME_STARTED_PREFIX + Game1MemoryGridActivity.this.gameInfo.getAnalyticsName());
                if (Game1MemoryGridActivity.this.isChallenge) {
                    mode = MemoryApplicationModel.ANALYTICS_CATEGORY_CHALLENGE;
                } else if (Game1MemoryGridActivity.this.isOnlineGame) {
                    mode = "Online";
                } else if (Game1MemoryGridActivity.this.isWorkout) {
                    mode = MemoryApplicationModel.ANALYTICS_CATEGORY_WORKOUT;
                } else {
                    mode = "Sprint";
                }
                Answers.getInstance().logCustom((CustomEvent) ((CustomEvent) new CustomEvent(MemoryApplicationModel.ANALYTICS_EVENT_GAME_STARTED_PREFIX).putCustomAttribute("Level Name", Game1MemoryGridActivity.this.gameInfo.getAnalyticsName())).putCustomAttribute("Mode ", mode));
                Game1MemoryGridActivity.this.retryGame();
            }
        };
        private GameInfo selectedSuggestedGame;

        public TimeoutDialog(Context context) {
            super(context);
            setCancelable(false);
            getWindow().setBackgroundDrawableResource(17170445);
            requestWindowFeature(1);
            getWindow().clearFlags(2);
            setContentView(R.layout.dialog_timeout);
            ButterKnife.bind((Dialog) this);
            Typeface typeFaceRoboto = Typeface.createFromAsset(Game1MemoryGridActivity.this.getAssets(), "Roboto-Light.ttf");
            this.failDialogTitle.setTypeface(Typeface.createFromAsset(Game1MemoryGridActivity.this.getAssets(), "Roboto-Regular.ttf"));
            this.restartTitle.setTypeface(typeFaceRoboto);
            this.failDialogInnerTitle.setTypeface(typeFaceRoboto);
            this.gameSuggestionName.setTypeface(typeFaceRoboto, 1);
            setOnShowListener(new DialogInterface.OnShowListener() {
                public void onShow(DialogInterface dialog) {
                    Game1MemoryGridActivity.this.foreground.setBackgroundColor(ContextCompat.getColor(Game1MemoryGridActivity.this, R.color.background));
                    Game1MemoryGridActivity.this.foreground.setVisibility(0);
                    TimeoutDialog.this.generateSuggestion();
                    Game1MemoryGridActivity.this.setCurrentLevel();
                }
            });
            setOnDismissListener(new DialogInterface.OnDismissListener() {
                public void onDismiss(DialogInterface dialog) {
                    Game1MemoryGridActivity.this.foreground.setVisibility(8);
                }
            });
        }

        public void setTitle(int titleResId) {
            this.failDialogTitle.setText(titleResId);
        }

        private void generateSuggestion() {
            this.selectedSuggestedGame = Games.get().suggestRandomGame(Game1MemoryGridActivity.this.gameInfo);
            Picasso.with(Game1MemoryGridActivity.this).load(this.selectedSuggestedGame.getGameImageRes()).into(this.gameSuggestionImage);
            this.gameSuggestionName.setText(this.selectedSuggestedGame.getGameNameRes());
        }

        @OnClick({R.id.fail_dialog_game_suggestion_image})
        protected void gameSuggestionClick() {
            if (Game1MemoryGridActivity.this.isOnlineGame || Game1MemoryGridActivity.this.isChallenge || Game1MemoryGridActivity.this.isWorkout) {
                this.gameSuggestionClickListener.onClick(null);
            } else {
                Game1MemoryGridActivity.this.showAdsIfNeeded(21);
            }
        }

        @OnClick({R.id.mainMenu})
        protected void mainMenuContainerCLick() {
            if (Game1MemoryGridActivity.this.isOnlineGame || Game1MemoryGridActivity.this.isChallenge || Game1MemoryGridActivity.this.isWorkout) {
                this.mainMenuClickListener.onClick(null);
            } else {
                Game1MemoryGridActivity.this.showAdsIfNeeded(22);
            }
        }

        @OnClick({R.id.button_dialog_fail_retry})
        protected void retryClick() {
            if (Game1MemoryGridActivity.this.isOnlineGame || Game1MemoryGridActivity.this.isChallenge || Game1MemoryGridActivity.this.isWorkout) {
                this.retryClickListener.onClick(null);
            } else {
                Game1MemoryGridActivity.this.showAdsIfNeeded(23);
            }
        }

        public void onBackPressed() {
            if (Game1MemoryGridActivity.this.isOnlineGame || Game1MemoryGridActivity.this.isChallenge || Game1MemoryGridActivity.this.isWorkout) {
                this.mainMenuClickListener.onClick(null);
            } else {
                Game1MemoryGridActivity.this.showAdsIfNeeded(22);
            }
        }
    }

    protected class UserInputEnabledFlowState implements GameFlowState {
        protected UserInputEnabledFlowState() {
        }

        public void applyState() {
            Game1MemoryGridActivity.this.grid.hideChallengeCells();
            Game1MemoryGridActivity.this.grid.enableAllCells();
            Game1MemoryGridActivity.this.enablePausePanel();
        }

        public int getDuration() {
            return 0;
        }
    }

    protected void startOnlineLevelTimer() {
        if (this.isOnlineGame && isEnableLevelTimer()) {
            this.startedOnlineTime = System.currentTimeMillis();
            this.levelOnlineTime = (long) (this.gameInfo.getOnlineLevelTime() * 1000);
            this.progressBar.setVisibility(0);
            this.progressBar.setMax((int) (this.levelOnlineTime / 10));
            this.timerOnlineHandler.postDelayed(this.timerOnlineRunnable, 0);
        }
    }

    protected void killOnlineTimer() {
        if (this.isOnlineGame && isEnableLevelTimer()) {
            this.progressBar.setVisibility(4);
            this.timerOnlineHandler.removeCallbacks(this.timerOnlineRunnable);
        }
    }

    private void updateOnlineTimerText(long time) {
        if (this.isOnlineGame && isEnableLevelTimer()) {
            this.progressBar.setProgress((int) (time / 10));
        }
    }

    protected boolean isEnableLevelTimer() {
        return true;
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        this.application = MemoryApplicationModel.getInstance();
        setContentView((int) R.layout.main_activity);
        ButterKnife.bind((Activity) this);
        this.gameInfo = (GameInfo) getIntent().getParcelableExtra(MainMenuActivity.EXTRA_GAME_INFO);
        this.challengeJsonGame = (ChallengeJsonGame) getIntent().getParcelableExtra(MainMenuActivity.EXTRA_CHALLENGE_GAME_INFO);
        this.isChallenge = getIntent().getBooleanExtra(StartGameActivity.EXTRA_CHALLENGE, false);
        this.isWorkout = getIntent().getBooleanExtra(StartGameActivity.EXTRA_WORKOUT, false);
        // Online PK mode (see docs/online-pk-prd.md): the original online
        // branches kept in this class are re-enabled by this extra; without
        // it the single-player behaviour is bit-for-bit unchanged.
        this.isOnlineGame = getIntent().getBooleanExtra(OnlineSession.EXTRA_ONLINE_GAME, false);
        this.gameSeed = System.currentTimeMillis();
        if (this.isOnlineGame) {
            this.gameSeed = getIntent().getLongExtra(OnlineSession.EXTRA_ONLINE_SEED, this.gameSeed);
        }
        initRandom();
        if (this.isOnlineGame) {
            this.panelLives.setVisibility(4);
            OnlineHud.attach(this);
        }
        if (!(this.isWorkout || this.isChallenge)) {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            Set<String> gamesPlayedPrefs = sharedPreferences.getStringSet(MainMenuActivity.PREF_GAMES_PLAYED, new HashSet());
            Set<String> gamesPlayed = new HashSet();
            gamesPlayed.addAll(gamesPlayedPrefs);
            gamesPlayed.add(this.gameInfo.getId());
            sharedPreferences.edit().putStringSet(MainMenuActivity.PREF_GAMES_PLAYED, gamesPlayed).apply();
        }
        this.localDataManager = MemoryApplicationModel.getInstance().getLocalDataManager();
        if (savedInstanceState != null) {
            this.videoWatchedCount = savedInstanceState.getInt(WATCHED_COUNT, 0);
            this.buyStarsClickedCountCount = savedInstanceState.getInt(BUY_STARS_CLICKED_COUNT, 0);
            this.maxLevel = savedInstanceState.getInt(MAX_LEVEL, 0);
            this.onlineReplayed = savedInstanceState.getBoolean(ONLINE_REPLAYED, false);
            this.onlineScore = savedInstanceState.getInt(ONLINE_SCORE, 0);
            this.pausedTime = savedInstanceState.getLong(PAUSED_TIME, 0);
            this.userScore = savedInstanceState.getInt(USER_SCORE, 0);
            this.pausedDuration = savedInstanceState.getLong(PAUSED_DURATION, 0);
            this.pausedCount = savedInstanceState.getLong(PAUSED_COUNT, 0);
        } else {
            this.videoWatchedCount = 0;
            this.buyStarsClickedCountCount = 0;
            this.onlineScore = 0;
            this.maxLevel = 0;
            Map<String, LocalGameStats> statistics = this.localDataManager.getLocalGameStats();
            if (statistics != null) {
                LocalGameStats stat = (LocalGameStats) statistics.get(this.gameInfo.getId());
                if (stat != null) {
                    this.maxLevel = stat.maxLevel - 1;
                }
            }
        }
        this.gridContainer = (ViewGroup) findViewById(R.id.grid_container);
        createProgression();
        this.progression.startGame();
        createGameFlowStates();
        this.gameSession = new LocalGameSession();
        this.gameSession.startLevel = getStartingLevel();
        for (int i = 1; i < this.gameSession.startLevel; i++) {
            this.progression.nextLevel();
        }
        this.gameSession.endLevel = 1;
        this.gameName.setText(this.gameInfo.getGameNameRes());
        this.gameSession.game = this.gameInfo.getId();
        Typeface typeFaceRoboto = Typeface.createFromAsset(getAssets(), "Roboto-Light.ttf");
        this.gameName.setTypeface(typeFaceRoboto);
        this.levelNumber.setTypeface(typeFaceRoboto);
        this.textLevelReady.setTypeface(typeFaceRoboto);
        initializePause();
        startGame();
        SoundUtils.playBackgroundSound(this);
        updateColors();
    }

    private void updateColors() {
        int backgroundColor = ContextCompat.getColor(this, R.color.default_background);
        int backgroundTransparentColor = adjustAlpha(backgroundColor, 170);
        this.root.setBackgroundColor(backgroundColor);
        this.foreground.setBackgroundColor(backgroundColor);
        this.timerContainer.setBackgroundColor(backgroundTransparentColor);
    }

    private static int adjustAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    protected void enablePausePanel() {
        if (!this.isOnlineGame && this.pausedCount < 5) {
            this.panelPause.setEnabled(true);
            this.pauseButton.clearColorFilter();
        }
    }

    protected void disablePausePanel() {
        if (!this.isOnlineGame) {
            this.panelPause.setEnabled(false);
            this.pauseButton.setColorFilter(Color.parseColor("#c8c8c8"));
        }
    }

    private void initializePause() {
        if (this.isOnlineGame) {
            this.soundToggle.setVisibility(0);
            this.pauseButton.setVisibility(8);
            this.soundToggle.setChecked(SoundUtils.isPlaySound(this));
            return;
        }
        this.soundToggle.setVisibility(8);
        this.pauseButton.setVisibility(0);
    }

    private void restartLevelAfterInterstitial() {
        this.videoWatchedCount++;
        this.foreground.setVisibility(8);
        this.panelPause.setVisibility(0);
        if (!this.isChallenge) {
            restartLevel(false);
        } else if (this.gameInfo.getId().equals("3") || this.gameInfo.getId().equals("6") || this.gameInfo.getId().equals("8")) {
            restartLevel(false);
        } else {
            startNextLevel();
        }
    }

    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(BUY_STARS_CLICKED_COUNT, this.buyStarsClickedCountCount);
        outState.putInt(MAX_LEVEL, this.maxLevel);
        outState.putInt(ONLINE_SCORE, this.onlineScore);
        outState.putLong(PAUSED_TIME, this.pausedTime);
        outState.putLong(PAUSED_COUNT, this.pausedCount);
        outState.putInt(USER_SCORE, this.userScore);
        outState.putLong(PAUSED_DURATION, this.pausedDuration);
    }

    @OnClick({R.id.panel_pause})
    protected void soundClick() {
        if (this.isOnlineGame) {
            this.soundToggle.setChecked(!this.soundToggle.isChecked());
            if (this.soundToggle.isChecked()) {
                this.application.logEvent((Activity) this, MemoryApplicationModel.ANALYTICS_CATEGORY_GENERAL, MemoryApplicationModel.ANALYTICS_EVENT_SOUND_ON);
            } else {
                this.application.logEvent((Activity) this, MemoryApplicationModel.ANALYTICS_CATEGORY_GENERAL, MemoryApplicationModel.ANALYTICS_EVENT_SOUND_OFF);
            }
            SoundUtils.setPlaySound(this, this.soundToggle.isChecked());
        } else if (this.pausedCount < 5) {
            this.pausedCount++;
            this.pausedTime = System.currentTimeMillis();
            showPause();
            final boolean isGetReadyStep = this.stateTimer.getCurrentState() instanceof ReadyFlowState;
            if (isGetReadyStep) {
                this.stateTimer.pause();
            }
            new PauseDialog(this, this.gameInfo, new OnClickListener() {
                public void onClick(View v) {
                    Game1MemoryGridActivity.this.exitFromPauseClicked();
                    Game1MemoryGridActivity.this.showTimeout(false);
                }
            }, new OnClickListener() {
                public void onClick(View v) {
                    if (Game1MemoryGridActivity.this.isChallenge) {
                        Game1MemoryGridActivity.this.challengeReplayClickListener.onClick(null);
                        return;
                    }
                    Game1MemoryGridActivity.this.retryFromPauseClicked();
                    Game1MemoryGridActivity.this.foreground.setVisibility(8);
                    if (Game1MemoryGridActivity.this.timeoutDialog == null) {
                        Game1MemoryGridActivity.this.timeoutDialog = new TimeoutDialog(Game1MemoryGridActivity.this);
                    }
                    Game1MemoryGridActivity.this.panelPause.setVisibility(0);
                    Game1MemoryGridActivity.this.showAdsIfNeeded(23);
                }
            }, new OnDismissListener() {
                public void onDismiss(DialogInterface dialog) {
                    if (Game1MemoryGridActivity.this.pausedTime != 0) {
                        Game1MemoryGridActivity game1MemoryGridActivity = Game1MemoryGridActivity.this;
                        game1MemoryGridActivity.pausedDuration += System.currentTimeMillis() - Game1MemoryGridActivity.this.pausedTime;
                    }
                    Game1MemoryGridActivity.this.pausedTime = 0;
                    Game1MemoryGridActivity.this.hidePause();
                    if (isGetReadyStep) {
                        Game1MemoryGridActivity.this.stateTimer.resume();
                    }
                }
            }).show();
            if (this.pausedCount >= 5) {
                disablePausePanel();
            }
        }
    }

    protected void exitFromPauseClicked() {
    }

    protected void retryFromPauseClicked() {
    }

    protected void showPause() {
    }

    protected void hidePause() {
    }

    protected void onDestroy() {
        killOnlineTimer();
        super.onDestroy();
    }

    protected void createProgression() {
        this.progression = new GameProgression1();
    }

    protected void startGame() {
        String mode;
        AppReminder.appStarted(this);
        this.application.logEvent((Activity) this, MemoryApplicationModel.ANALYTICS_CATEGORY_GENERAL, MemoryApplicationModel.ANALYTICS_EVENT_GAME_STARTED_PREFIX + this.gameInfo.getAnalyticsName());
        if (this.isChallenge) {
            mode = MemoryApplicationModel.ANALYTICS_CATEGORY_CHALLENGE;
        } else if (this.isOnlineGame) {
            mode = "Online";
        } else if (this.isWorkout) {
            mode = MemoryApplicationModel.ANALYTICS_CATEGORY_WORKOUT;
        } else {
            mode = "Sprint";
        }
        Answers.getInstance().logCustom((CustomEvent) ((CustomEvent) new CustomEvent(MemoryApplicationModel.ANALYTICS_EVENT_GAME_STARTED_PREFIX).putCustomAttribute("Level Name", this.gameInfo.getAnalyticsName())).putCustomAttribute("Mode ", mode));
        startOnlineLevelTimer();
        startLevel();
    }

    private void initRandom() {
        if (this.isOnlineGame) {
            GameRandom.init(this.gameSeed);
        } else {
            GameRandom.init();
        }
    }

    public void retryGame() {
        this.buyStarsClickedCountCount = 0;
        this.videoWatchedCount = 0;
        this.pausedDuration = 0;
        this.pausedTime = 0;
        this.pausedCount = 0;
        this.userScore = 0;
        enablePausePanel();
        this.life1.setVisibility(0);
        this.life2.setVisibility(0);
        int currentLevel = this.progression.getLevelNumber();
        if (currentLevel > this.gameSession.endLevel) {
            this.gameSession.endLevel = currentLevel;
        }
        saveGameSession();
        String game = this.gameSession.game;
        this.gameSession = new LocalGameSession();
        this.gameSession.moneyEarned = 0;
        this.gameSession.moneyPaid = 0;
        this.gameSession.game = game;
        currentLevel = getStartingLevel();
        this.gameSession.startLevel = currentLevel;
        this.gameSession.endLevel = currentLevel;
        this.progression.startGame();
        for (int i = 1; i < currentLevel; i++) {
            this.progression.nextLevel();
        }
        if (this.grid != null) {
            this.gridContainer.removeView((View) this.grid);
        }
        this.timerContainer.setVisibility(8);
        startLevel();
    }

    protected int getStartingLevel() {
        if (this.isOnlineGame) {
            return 1;
        }
        String id = this.gameInfo.getId();
        int i = -1;
        switch (id.hashCode()) {
            case 51:
                if (id.equals("3")) {
                    i = 0;
                    break;
                }
                break;
            case 54:
                if (id.equals("6")) {
                    i = 1;
                    break;
                }
                break;
            case 56:
                if (id.equals("8")) {
                    i = 2;
                    break;
                }
                break;
            case 57:
                if (id.equals("9")) {
                    i = 3;
                    break;
                }
                break;
            case 1569:
                if (id.equals("12")) {
                    i = 4;
                    break;
                }
                break;
            case 1570:
                if (id.equals("13")) {
                    i = 5;
                    break;
                }
                break;
            case 1571:
                if (id.equals("14")) {
                    i = 6;
                    break;
                }
                break;
            case 1572:
                if (id.equals("15")) {
                    i = 7;
                    break;
                }
                break;
            case 1573:
                if (id.equals("16")) {
                    i = 8;
                    break;
                }
                break;
            case 1574:
                if (id.equals("17")) {
                    i = 9;
                    break;
                }
                break;
            case 1575:
                if (id.equals("18")) {
                    i = 10;
                    break;
                }
                break;
            case 1576:
                if (id.equals("19")) {
                    i = 11;
                    break;
                }
                break;
            case 1598:
                if (id.equals("20")) {
                    i = 14;
                    break;
                }
                break;
            case 1599:
                if (id.equals("21")) {
                    i = 12;
                    break;
                }
                break;
            case SharingDialog.DOLLAR2_COINS /*1600*/:
                if (id.equals("22")) {
                    i = 13;
                    break;
                }
                break;
        }
        switch (i) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
            case 10:
            case 11:
            case 12:
            case 13:
                return 1;
            case 14:
                if (!this.isChallenge) {
                    return 1;
                }
                break;
        }
        if (this.isWorkout) {
            float avgLevel = this.localDataManager.getAverageWorkoutLevel(this.gameInfo.getId());
            if (avgLevel > 6.0f) {
                return ((int) (((double) avgLevel) + 0.5d)) - 5;
            }
            return 1;
        } else if (!this.isChallenge) {
            LocalGameStats stats = (LocalGameStats) MemoryApplicationModel.getInstance().getLocalDataManager().getLocalGameStats().get(this.gameInfo.getId());
            if (stats == null || stats.avgLevel <= 6.0f) {
                return 1;
            }
            return ((int) (((double) stats.avgLevel) + 0.5d)) - 5;
        } else if (this.challengeJsonGame.getLevel() > 6) {
            return (this.challengeJsonGame.getLevel() - 6) + 1;
        } else {
            return 1;
        }
    }

    public void restartLevel(boolean takeMoney) {
        if (this.grid != null) {
            this.gridContainer.removeView((View) this.grid);
        }
        this.timerContainer.setVisibility(8);
        startLevel();
        if (takeMoney) {
            this.gameSession.moneyPaid += 30;
            this.localDataManager.addCoinsTransaction(LocalDataManager.TYPE_BUY_LEVEL, -30);
        }
    }

    protected void startNextLevel() {
        if (this.grid != null) {
            this.gridContainer.removeView((View) this.grid);
        }
        this.timerContainer.setVisibility(8);
        if (!this.isChallenge || this.progression.getLevelNumber() < this.challengeJsonGame.getLevel()) {
            this.progression.nextLevel();
            startLevel();
            return;
        }
        this.grid.disableAllCells();
        this.grid.showChallengeCells();
        this.grid.animateFinishCells();
        disablePausePanel();
        retryFromPauseClicked();
        new Handler().postDelayed(new Runnable() {
            public void run() {
                if (!Game1MemoryGridActivity.this.isFinishing()) {
                    Game1MemoryGridActivity.this.enablePausePanel();
                    Game1MemoryGridActivity.this.showChallengeFinishDialog();
                }
            }
        }, 200);
    }

    protected void createGameFlowStates() {
        List<GameFlowState> states = new ArrayList();
        states.add(new ReadyFlowState());
        states.add(new GridAnimationFlowState());
        states.add(new ShowChallengeFlowState());
        states.add(new UserInputEnabledFlowState());
        this.stateTimer = new GameFlowStateTimer(states);
    }

    public boolean isIabSetupFinished() {
        return false;
    }

    protected void startLevel() {
        displayLevelNumber();
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int side = Math.min(size.x, size.y);
        if (((double) this.progression.getCurrentGridSize()) < MIN_GRID_SIZE) {
            side = (int) ((((double) side) / MIN_GRID_SIZE) * ((double) this.progression.getCurrentGridSize()));
        }
        LayoutParams params = new LayoutParams(side, side);
        params.addRule(13, -1);
        this.grid = new RectangularGrid(this, this.progression.getCurrentGridSize(), this.progression.getCurrentGridSize(), this.progression.getCurrentWinCells(), side, side);
        ((RectangularGrid) this.grid).setShowAnimation(true);
        this.gridContainer.addView((View) this.grid, 0, params);
        this.grid.setGridEventsListener(this);
        this.grid.buildGrid();
        this.stateTimer.start();
    }

    protected void showChallenge() {
        this.timerContainer.setVisibility(8);
        this.grid.showChallengeCells();
    }

    protected int giveStars() {
        int newStars;
        if (this.isChallenge || this.isOnlineGame || this.isWorkout) {
            newStars = 0;
        } else if (this.progression.getLevelNumber() <= 3) {
            newStars = 0;
        } else if (this.progression.getLevelNumber() <= 10) {
            newStars = 1;
        } else if (this.progression.getLevelNumber() <= 14) {
            newStars = 2;
        } else {
            newStars = 3;
        }
        if (newStars > 0) {
            this.gameSession.moneyEarned += newStars;
            this.localDataManager.addCoinsTransaction(LocalDataManager.TYPE_GAME_EARNED, newStars);
        }
        return newStars;
    }

    protected void updateOnlineScore() {
        int level = this.progression.getLevelNumber();
        int levelScore = 0;
        String id = this.gameInfo.getId();
        int obj = -1;
        switch (id.hashCode()) {
            case 48:
                if (id.equals(AppEventsConstants.EVENT_PARAM_VALUE_NO)) {
                    obj = 8;
                    break;
                }
                break;
            case 49:
                if (id.equals(AppEventsConstants.EVENT_PARAM_VALUE_YES)) {
                    obj = 0;
                    break;
                }
                break;
            case 50:
                if (id.equals("2")) {
                    obj = 9;
                    break;
                }
                break;
            case 51:
                if (id.equals("3")) {
                    obj = 17;
                    break;
                }
                break;
            case 52:
                if (id.equals("4")) {
                    obj = 10;
                    break;
                }
                break;
            case 53:
                if (id.equals("5")) {
                    obj = 1;
                    break;
                }
                break;
            case 54:
                if (id.equals("6")) {
                    obj = 11;
                    break;
                }
                break;
            case 55:
                if (id.equals("7")) {
                    obj = 12;
                    break;
                }
                break;
            case 56:
                if (id.equals("8")) {
                    obj = 2;
                    break;
                }
                break;
            case 57:
                if (id.equals("9")) {
                    obj = 3;
                    break;
                }
                break;
            case 1567:
                if (id.equals("10")) {
                    obj = 13;
                    break;
                }
                break;
            case 1568:
                if (id.equals("11")) {
                    obj = 18;
                    break;
                }
                break;
            case 1569:
                if (id.equals("12")) {
                    obj = 4;
                    break;
                }
                break;
            case 1570:
                if (id.equals("13")) {
                    obj = 14;
                    break;
                }
                break;
            case 1571:
                if (id.equals("14")) {
                    obj = 5;
                    break;
                }
                break;
            case 1572:
                if (id.equals("15")) {
                    obj = 6;
                    break;
                }
                break;
            case 1573:
                if (id.equals("16")) {
                    obj = 19;
                    break;
                }
                break;
            case 1574:
                if (id.equals("17")) {
                    obj = 15;
                    break;
                }
                break;
            case 1575:
                if (id.equals("18")) {
                    obj = 16;
                    break;
                }
                break;
            case 1576:
                if (id.equals("19")) {
                    obj = 7;
                    break;
                }
                break;
        }
        switch (obj) {
            case 0:
            case 1:
            case 2:
                levelScore = 16;
                break;
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
                levelScore = 8;
                break;
            case 8:
            case 9:
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
            case 16:
                levelScore = 10;
                break;
            case 17:
            case 18:
                levelScore = 4;
                break;
            case 19:
                levelScore = 12;
                break;
        }
        if (!(this.gameInfo.getId().equals("14") || this.gameInfo.getId().equals("15") || this.gameInfo.getId().equals("19"))) {
            levelScore += level * 2;
        }
        this.onlineScore += levelScore;
        if (this.isOnlineGame) {
            OnlineSession.get().reportLevelWin(level, this.onlineScore);
        }
    }

    protected void showWin() {
        if (this.isChallenge) {
            this.userScore += getLevelScore(this.progression.getLevelNumber());
        } else {
            giveStars();
        }
        int nextLevel = this.progression.getLevelNumber();
        Integer newCup = null;
        if (nextLevel > this.maxLevel) {
            newCup = getNewCup(this.gameInfo, nextLevel, this.maxLevel);
            this.maxLevel = nextLevel;
        }
        if (this.isOnlineGame) {
            updateOnlineScore();
            this.textLevelReady.setText("");
            this.levelHint.setVisibility(0);
            this.timerContainer.setVisibility(0);
            this.stateTimer.scheduleAndRunTask(new TimerTask() {
                public void run() {
                    Game1MemoryGridActivity.this.runOnUiThread(new Runnable() {
                        public void run() {
                            Game1MemoryGridActivity.this.startNextLevel();
                        }
                    });
                }
            }, 800);
        } else if (this.isWorkout || this.isChallenge || newCup == null) {
            this.foreground.setVisibility(8);
            this.panelPause.setVisibility(0);
            disablePausePanel();
            this.pauseButton.clearColorFilter();
            this.grid.animateFinishCells();
            this.foreground.postDelayed(new Runnable() {
                public void run() {
                    Game1MemoryGridActivity.this.startNextLevel();
                }
            }, 500);
        } else {
            new TrophyDialog(this, ((Integer) Games.get().getTrophyImages(this.gameInfo).get(newCup.intValue())).intValue(), ((Integer) Games.get().getTrophyStrings().get(newCup.intValue())).intValue(), new OnDismissListener() {
                public void onDismiss(DialogInterface dialogInterface) {
                    Game1MemoryGridActivity.this.foreground.setVisibility(8);
                    Game1MemoryGridActivity.this.panelPause.setVisibility(0);
                    Game1MemoryGridActivity.this.startNextLevel();
                }
            }).show();
        }
        this.grid.disableAllCells();
        SoundUtils.playSound(this, SOUND.WIN);
    }

    protected Integer getNewCup(GameInfo gameInfo, int nextLevel, int maxLevel) {
        List<Integer> trophyLevels = Games.get().getTrophyLevels(gameInfo);
        int oldCup = getCup(trophyLevels, maxLevel);
        int newCup = getCup(trophyLevels, nextLevel);
        if (newCup > oldCup) {
            return Integer.valueOf(newCup);
        }
        return null;
    }

    private int getCup(List<Integer> trophyLevels, int level) {
        for (int i = trophyLevels.size() - 1; i >= 0; i--) {
            if (level >= ((Integer) trophyLevels.get(i)).intValue()) {
                return i;
            }
        }
        return -1;
    }

    protected void showFailure() {
        showFailureDialog();
        if (this.life2.getVisibility() == 0) {
            SoundUtils.playSound(this, SOUND.WRONG);
        } else {
            SoundUtils.playSound(this, SOUND.FAIL);
        }
    }

    protected void showFailureDialog() {
        this.grid.disableAllCells();
        this.grid.showChallengeCells();
        showAppropriateDialog();
        if (!this.isOnlineGame) {
            this.grid.animateFinishCells();
        }
    }

    protected void showAppropriateDialog() {
        if (this.progression.getLevelNumber() > this.gameSession.endLevel) {
            this.gameSession.endLevel = this.progression.getLevelNumber();
        }
        saveGameSession();
        disablePausePanel();
        new Handler().postDelayed(new Runnable() {
            public void run() {
                if (!Game1MemoryGridActivity.this.isFinishing()) {
                    Game1MemoryGridActivity.this.enablePausePanel();
                    int nextLevel = Game1MemoryGridActivity.this.progression.getLevelNumber() - 1;
                    Integer newCup = null;
                    if (nextLevel > Game1MemoryGridActivity.this.maxLevel) {
                        newCup = Game1MemoryGridActivity.this.getNewCup(Game1MemoryGridActivity.this.gameInfo, nextLevel, Game1MemoryGridActivity.this.maxLevel);
                        Game1MemoryGridActivity.this.maxLevel = nextLevel;
                    }
                    if (Game1MemoryGridActivity.this.isOnlineGame) {
                        if (Game1MemoryGridActivity.this.gameInfo.getId().equals("3") || Game1MemoryGridActivity.this.gameInfo.getId().equals("6") || Game1MemoryGridActivity.this.gameInfo.getId().equals("8")) {
                            Game1MemoryGridActivity.this.restartLevel(false);
                        } else {
                            Game1MemoryGridActivity.this.startNextLevel();
                        }
                    } else if (Game1MemoryGridActivity.this.isWorkout || Game1MemoryGridActivity.this.isChallenge || newCup == null) {
                        Game1MemoryGridActivity.this.takeLife();
                    } else {
                        new TrophyDialog(Game1MemoryGridActivity.this, ((Integer) Games.get().getTrophyImages(Game1MemoryGridActivity.this.gameInfo).get(newCup.intValue())).intValue(), ((Integer) Games.get().getTrophyStrings().get(newCup.intValue())).intValue(), new OnDismissListener() {
                            public void onDismiss(DialogInterface dialogInterface) {
                                Game1MemoryGridActivity.this.takeLife();
                            }
                        }).show();
                    }
                }
            }
        }, this.isOnlineGame ? getStartDialogOnlineDelay() : (long) this.startDialogDelay);
    }

    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
    }

    private void takeLife() {
        if (this.videoWatchedCount < 2) {
            restartLevelAfterInterstitial();
            this.life1.setVisibility(8);
            if (this.videoWatchedCount == 2) {
                this.life2.setVisibility(8);
            }
        } else if (this.isWorkout) {
            this.foreground.setVisibility(0);
            this.panelPause.setVisibility(4);
            showTimeout(false);
        } else if (this.isChallenge) {
            this.foreground.setVisibility(0);
            this.panelPause.setVisibility(4);
            showChallengeFinishDialog();
        } else {
            this.foreground.setVisibility(0);
            this.panelPause.setVisibility(4);
            new FailDialog(this, this.buyStarsClickedCountCount, new OnDialogClickListener() {
                public void onDialogClick(Dialog dialog) {
                    if (Game1MemoryGridActivity.this.localDataManager.getLocalUser().money < 30) {
                        dialog.dismiss();
                        Game1MemoryGridActivity.this.showTimeout(false);
                        return;
                    }
                    Game1MemoryGridActivity game1MemoryGridActivity = Game1MemoryGridActivity.this;
                    game1MemoryGridActivity.buyStarsClickedCountCount++;
                    Game1MemoryGridActivity.this.application.logEvent(Game1MemoryGridActivity.this, MemoryApplicationModel.ANALYTICS_CATEGORY_BONUSES, MemoryApplicationModel.ANALYTICS_EVENT_REPLAY_USED_PREFIX + Game1MemoryGridActivity.this.gameInfo.getId());
                    Game1MemoryGridActivity.this.foreground.setVisibility(8);
                    Game1MemoryGridActivity.this.panelPause.setVisibility(0);
                    Game1MemoryGridActivity.this.restartLevel(true);
                    dialog.dismiss();
                }
            }, new OnDialogClickListener() {
                public void onDialogClick(Dialog dialog) {
                    Game1MemoryGridActivity.this.foreground.setVisibility(8);
                    dialog.dismiss();
                    if (Game1MemoryGridActivity.this.timeoutDialog == null) {
                        Game1MemoryGridActivity.this.timeoutDialog = new TimeoutDialog(Game1MemoryGridActivity.this);
                    }
                    Game1MemoryGridActivity.this.panelPause.setVisibility(0);
                    Game1MemoryGridActivity.this.showAdsIfNeeded(23);
                }
            }, new OnDialogClickListener() {
                public void onDialogClick(Dialog dialog) {
                    dialog.dismiss();
                    Game1MemoryGridActivity.this.showTimeout(false);
                }
            }).show();
        }
    }

    protected void showChallengeFinishDialog() {
        displayLevelNumber();
        disablePausePanel();
        int stars = this.application.getLocalDataManager().addGameAndGetStars(this, this.challengeJsonGame.getLevelNumber(), this.gameInfo.getId(), this.userScore, calculateGoalScore(), this.challengeJsonGame.getLevel());
        if (stars > 0) {
            this.application.getLocalDataManager().setShowChallengeTutorial(this, this.gameInfo.getAnalyticsName());
        }
        this.challengeResultDialog = new ChallengeResultDialog(this, this.gameInfo.getGameNameRes(), this.userScore, stars, new ChallengeResultDialog.OnDialogClickListener() {
            public void onDialogClick(Dialog dialog) {
                dialog.dismiss();
                Game1MemoryGridActivity.this.challengeHomeClickListener.onClick(null);
            }
        }, new ChallengeResultDialog.OnDialogClickListener() {
            public void onDialogClick(Dialog dialog) {
                dialog.dismiss();
                SoundUtils.stopBackgroundSound();
                Game1MemoryGridActivity.this.challengeNextClickListener.onClick(null);
            }
        }, new ChallengeResultDialog.OnDialogClickListener() {
            public void onDialogClick(Dialog dialog) {
                dialog.dismiss();
                Game1MemoryGridActivity.this.challengeReplayClickListener.onClick(null);
            }
        });
        this.challengeOpenDialogClickListener.onClick(null);
    }

    private void showAdsIfNeeded(int type) {
        OnClickListener onClickListener;
        switch (type) {
            case 21:
                onClickListener = this.timeoutDialog.gameSuggestionClickListener;
                break;
            case 22:
                onClickListener = this.timeoutDialog.mainMenuClickListener;
                break;
            case 23:
                onClickListener = this.timeoutDialog.retryClickListener;
                break;
            case 24:
                onClickListener = this.backPressClickListener;
                break;
            case 31:
                onClickListener = this.challengeHomeClickListener;
                break;
            case 32:
                onClickListener = this.challengeReplayClickListener;
                break;
            case 33:
                onClickListener = this.challengeNextClickListener;
                break;
            case 34:
                onClickListener = this.challengeOpenDialogClickListener;
                break;
            default:
                return;
        }
        onClickListener.onClick(null);
    }

    private void setCurrentLevel() {
        String atCurrentLevelText = getString(R.string.at_current_level_placeholder, new Object[]{Integer.valueOf(this.progression.getLevelNumber())});
    }

    public void onStatisticUpdated() {
    }

    protected long getStartDialogOnlineDelay() {
        return 2000;
    }

    protected void saveGameSession() {
        if (this.isWorkout) {
            this.localDataManager.addWorkoutResult(this.gameInfo.getId(), this.gameSession.endLevel);
        } else if (!this.isChallenge && !this.isOnlineGame) {
            this.localDataManager.addGameSession(this, this.gameSession);
        }
    }

    protected void openMainMenu() {
        if (this.isOnlineGame) {
            // Leaving an online round mid-play forfeits the match; the exit
            // paths of every game funnel through here (backPressClickListener).
            killOnlineTimer();
            OnlineSession.get().resign();
            SoundUtils.stopBackgroundSound();
            finish();
            return;
        }
        SoundUtils.stopBackgroundSound();
        Intent intent = new Intent(this, MainMenuActivity.class);
        intent.setFlags(335544320);
        startActivity(intent);
    }

    public void onSuccessCellClicked(int lastAddedCellClicked) {
        SoundUtils.playSound(this, SOUND.TAP);
        if (this.grid.getCurrentSuccessCellsClicked() >= this.grid.getSuccessCells()) {
            showWin();
        }
    }

    public void onFailCellClicked() {
        showFailure();
    }

    public void onBackPressed() {
        List<String> showDialogList = Arrays.asList(new String[]{AppEventsConstants.EVENT_PARAM_VALUE_NO, AppEventsConstants.EVENT_PARAM_VALUE_YES, "2", "4", "5", "7", "8"});
        if (this.isOnlineGame && showDialogList.contains(this.gameInfo.getId())) {
            showOnlineBackPressDialog();
        } else {
            confirmBackPress();
        }
    }

    private void confirmBackPress() {
        killOnlineTimer();
        int currentLevel = this.progression.getLevelNumber();
        if (currentLevel > this.gameSession.endLevel) {
            this.gameSession.endLevel = currentLevel;
        }
        saveGameSession();
        showAdsIfNeeded(24);
    }

    private void showOnlineBackPressDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage((int) R.string.dialog_lose_bet);
        builder.setNegativeButton((int) R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        builder.setPositiveButton((int) R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                Game1MemoryGridActivity.this.confirmBackPress();
                dialog.dismiss();
            }
        });
        builder.show();
    }

    protected void clearWrongCells() {
    }

    protected void showOnlineReplayDialog() {
        // Online PK: the round timer ran out. Report the final score and
        // return to OnlineMatchActivity, which shows the intermission board
        // until the server broadcast round_end / match_end.
        killOnlineTimer();
        OnlineSession.get().onRoundFinished(this.onlineScore);
        SoundUtils.stopBackgroundSound();
        finish();
    }

    protected void showTimeout(boolean isTimeOut) {
        this.grid.disableAllCells();
        this.grid.showChallengeCells();
        if (this.timeoutDialog == null) {
            this.timeoutDialog = new TimeoutDialog(this);
        }
        if (isTimeOut) {
            this.timeoutDialog.setTitle(R.string.dialog_timeout_title);
        } else {
            this.timeoutDialog.setTitle(R.string.dialog_you_finished_game);
        }
        int nextLevel = this.progression.getLevelNumber() - 1;
        Integer newCup = null;
        if (nextLevel > this.maxLevel) {
            newCup = getNewCup(this.gameInfo, nextLevel, this.maxLevel);
            this.maxLevel = nextLevel;
        }
        if (this.isOnlineGame) {
            showOnlineReplayDialog();
        } else if (this.isWorkout) {
            confirmBackPress();
        } else if (this.isChallenge) {
            showChallengeFinishDialog();
        } else if (newCup == null) {
            this.panelPause.setVisibility(4);
            if (!isFinishing()) {
                this.timeoutDialog.show();
            }
        } else {
            new TrophyDialog(this, ((Integer) Games.get().getTrophyImages(this.gameInfo).get(newCup.intValue())).intValue(), ((Integer) Games.get().getTrophyStrings().get(newCup.intValue())).intValue(), new OnDismissListener() {
                public void onDismiss(DialogInterface dialogInterface) {
                    Game1MemoryGridActivity.this.panelPause.setVisibility(4);
                    Game1MemoryGridActivity.this.timeoutDialog.show();
                }
            }).show();
        }
        if (isTimeOut) {
            SoundUtils.playSound(this, SOUND.FAIL);
        }
    }

    protected void displayLevelNumber() {
        if (this.isChallenge) {
            this.levelNumber.setText(Html.fromHtml("<b>" + String.valueOf(this.userScore) + " </b> / " + getMinPassScore() + "</b>"));
        } else {
            this.levelNumber.setText(getString(R.string.level_number_prefix) + " " + this.progression.getLevelNumber());
        }
    }

    protected int calculateGoalScore() {
        int goalScore = 0;
        int finishLevel = this.challengeJsonGame.getLevel();
        for (int i = getStartingLevel(); i <= finishLevel; i++) {
            goalScore += getLevelScore(i);
        }
        return goalScore;
    }

    protected int getLevelScore(int level) {
        int levelScore = 0;
        String id = this.gameInfo.getId();
        int obj = -1;
        switch (id.hashCode()) {
            case 48:
                if (id.equals(AppEventsConstants.EVENT_PARAM_VALUE_NO)) {
                    obj = 8;
                    break;
                }
                break;
            case 49:
                if (id.equals(AppEventsConstants.EVENT_PARAM_VALUE_YES)) {
                    obj = 0;
                    break;
                }
                break;
            case 50:
                if (id.equals("2")) {
                    obj = 9;
                    break;
                }
                break;
            case 51:
                if (id.equals("3")) {
                    obj = 20;
                    break;
                }
                break;
            case 52:
                if (id.equals("4")) {
                    obj = 10;
                    break;
                }
                break;
            case 53:
                if (id.equals("5")) {
                    obj = 1;
                    break;
                }
                break;
            case 54:
                if (id.equals("6")) {
                    obj = 11;
                    break;
                }
                break;
            case 55:
                if (id.equals("7")) {
                    obj = 12;
                    break;
                }
                break;
            case 56:
                if (id.equals("8")) {
                    obj = 2;
                    break;
                }
                break;
            case 57:
                if (id.equals("9")) {
                    obj = 3;
                    break;
                }
                break;
            case 1567:
                if (id.equals("10")) {
                    obj = 13;
                    break;
                }
                break;
            case 1568:
                if (id.equals("11")) {
                    obj = 21;
                    break;
                }
                break;
            case 1569:
                if (id.equals("12")) {
                    obj = 4;
                    break;
                }
                break;
            case 1570:
                if (id.equals("13")) {
                    obj = 14;
                    break;
                }
                break;
            case 1571:
                if (id.equals("14")) {
                    obj = 5;
                    break;
                }
                break;
            case 1572:
                if (id.equals("15")) {
                    obj = 6;
                    break;
                }
                break;
            case 1574:
                if (id.equals("17")) {
                    obj = 15;
                    break;
                }
                break;
            case 1575:
                if (id.equals("18")) {
                    obj = 16;
                    break;
                }
                break;
            case 1576:
                if (id.equals("19")) {
                    obj = 7;
                    break;
                }
                break;
            case 1598:
                if (id.equals("20")) {
                    obj = 17;
                    break;
                }
                break;
            case 1599:
                if (id.equals("21")) {
                    obj = 18;
                    break;
                }
                break;
            case SharingDialog.DOLLAR2_COINS /*1600*/:
                if (id.equals("22")) {
                    obj = 19;
                    break;
                }
                break;
        }
        switch (obj) {
            case 0:
            case 1:
            case 2:
                levelScore = 16;
                break;
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
                levelScore = 8;
                break;
            case 8:
            case 9:
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
            case 16:
            case 17:
            case 18:
            case 19:
                levelScore = 10;
                break;
            case 20:
            case 21:
                levelScore = 4;
                break;
        }
        if (this.gameInfo.getId().equals("14") || this.gameInfo.getId().equals("15") || this.gameInfo.getId().equals("19")) {
            return levelScore;
        }
        return levelScore + (level * 2);
    }

    private static boolean isAdvancedStarsCount(String gameId) {
        int obj = -1;
        switch (gameId.hashCode()) {
            case 1571:
                if (gameId.equals("14")) {
                    obj = 0;
                    break;
                }
                break;
            case 1572:
                if (gameId.equals("15")) {
                    obj = 1;
                    break;
                }
                break;
            case 1576:
                if (gameId.equals("19")) {
                    obj = 2;
                    break;
                }
                break;
        }
        switch (obj) {
            case 0:
                return true;
            case 1:
                return true;
            case 2:
                return true;
            default:
                return false;
        }
    }

    protected String getMinPassScore() {
        if (isAdvancedStarsCount(this.gameInfo.getId())) {
            return String.valueOf((int) (((float) calculateGoalScore()) * getAdvancedPassKoef(this.challengeJsonGame.getLevel())));
        }
        return String.valueOf((int) (((double) calculateGoalScore()) * MIN_LEVEL_PASS_KOEF));
    }

    private static float getAdvancedPassKoef(int level) {
        if (level < 23) {
            return MIN_ADVANCED_LEVEL_PASS_KOEF;
        }
        return MIN_ADVANCED_HIGH_LEVEL_PASS_KOEF;
    }
}
