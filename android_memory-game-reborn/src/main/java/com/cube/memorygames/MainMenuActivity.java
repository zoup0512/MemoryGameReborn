package com.cube.memorygames;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.annotation.IdRes;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.text.TextUtils;
import android.view.Display;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import com.cube.memorygames.SharingDialog.IabStatus;
import com.cube.memorygames.SharingDialog.StatisticListener;
import com.cube.memorygames.api.local.LocalDataManager;
import com.cube.memorygames.api.local.challenge.ChallengeJsonGame;
import com.cube.memorygames.api.local.model.LocalUser;
import com.cube.memorygames.api.online.OnlineLobbyAdapter;
import com.cube.memorygames.api.online.OnlineSession;
import com.cube.memorygames.model.GameInfo;
import com.cube.memorygames.ui.AppRater;
import com.cube.memorygames.ui.GridSpaceItemDecorator;
import com.cube.memorygames.ui.MenuAdapter;
import com.cube.memorygames.ui.WorkoutAdapter;
import com.github.florent37.viewanimator.AnimationListener.Stop;
import com.github.florent37.viewanimator.ViewAnimator;
import com.memory.brain.training.games.R;
import com.roughike.bottombar.BottomBar;
import com.roughike.bottombar.OnTabSelectListener;
import com.roughike.bottombar.BottomBarTab;
import java.util.HashSet;
import java.util.List;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

/**
 * Offline main menu. The Online tab, Facebook login/callbacks, Google Play
 * billing (unlock-all / pro / remove-ads), the network sync task and the
 * cross-promo dialogs of the original app were removed; the local Sprint,
 * Workout, Challenge and Menu sections are unchanged.
 */
public class MainMenuActivity extends AppCompatActivity implements StatisticListener, IabStatus {
    public static final String EXTRA_CHALLENGE_GAME_INFO = "challengeLaunchGameInfo";
    public static final String EXTRA_CHECK_APP_RATE = "checkAppRate";
    public static final String EXTRA_GAME_INFO = "launchGameInfo";
    public static final String EXTRA_ONLINE = "onlineMode";
    public static final String EXTRA_SHOW_SECRET_GAME = "showSecretGame";
    public static final int HINT_COINS_GAME_COUNT = 2;
    public static final int HINT_TOP_GAME_COUNT = 4;
    public static final String PREF_BADGE_CHALLENGE_CLICKED = "badgeChallengeClicked2";
    private static final String PREF_COINS_BUBBLE_SHOWED = "coinsBubbleShowedPrefs";
    public static final String PREF_GAMES_PLAYED = "gamesPlayedPrefs";
    private static final String PREF_LAST_TAB_POSITION = "lastTabPosition";
    private static final String STATE_ONLINE_MODE_PAGE = "onlineModePage";
    private static final String PREF_TOP_BUBBLE_SHOWED = "topBubbleShowedPrefs";
    public static final int RESULT_LOGIN = 1001;
    private static final int TAB_CHALLENGE = 2;
    private static final int TAB_ONLINE = 3;
    private static final int TAB_MENU = 4;
    private static final int TAB_SPRINT = 0;
    private static final int TAB_WORKOUT = 1;
    private MemoryApplicationModel application;
    private ChallengeAdapter challengeAdapter;
    private ChallengeCLickListener challengeCLickListener = new ChallengeCLickListener();
    @BindView(R.id.coins_hint)
    View coinsHint;
    @BindView(R.id.divider)
    View divider;
    private GameClickListener gameClickListener = new GameClickListener();
    private GameListAdapter gameListAdapter;
    private GridSpaceItemDecorator gridSpaceItemDecorator;
    private boolean isSetupFinished;
    private LocalDataManager localDataManager;
    private BottomBar mBottomBar;
    private MenuAdapter menuAdapter;
    private OnlineLobbyAdapter onlineAdapter;
    private int onlineModePage;
    @BindView(R.id.panel_header)
    View panelHeader;
    @BindView(R.id.text_top_bar_rating)
    TextView ratingView;
    @BindView(R.id.recyclerView)
    RecyclerView recyclerView;
    @BindView(R.id.root)
    View root;
    @BindView(R.id.star)
    View star;
    @BindView(R.id.text_top_bar_stars_count)
    TextView starsView;
    @BindView(R.id.top_hint)
    View topHint;
    private WorkoutAdapter workoutAdapter;

    private class ChallengeCLickListener implements OnClickListener {
        private ChallengeCLickListener() {
        }

        public void onClick(View v) {
            new ChallengeStartDialog(MainMenuActivity.this, (ChallengeJsonGame) v.getTag()).show();
        }
    }

    private class GameClickListener implements OnClickListener {
        private GameClickListener() {
        }

        public void onClick(final View v) {
            final GameInfo gameInfo = (GameInfo) v.getTag();
            String gameId = gameInfo.getId();
            LocalUser localUser = MainMenuActivity.this.localDataManager.getLocalUser();
            String buyGameId = "game" + (Integer.parseInt(gameId) + 1);
            if (!gameInfo.hasLock() || localUser.parseUnlockedContent().contains(buyGameId) || localUser.vip || localUser.gamesUnlocked) {
                MainMenuActivity.this.launchGame(gameInfo);
            } else {
                MainMenuActivity.this.application.logEvent(MainMenuActivity.this, MemoryApplicationModel.ANALYTICS_CATEGORY_UNLOCK, MemoryApplicationModel.ANALYTICS_EVENT_TRY_UNLOCK_GAME_PREFIX + gameInfo.getId());
                new UnlockDialog(MainMenuActivity.this, gameInfo, new OnClickListener() {
                    public void onClick(View view) {
                        MainMenuActivity.this.unlockGame(gameInfo, v);
                    }
                }).show();
            }
        }
    }

    class UnlockDialog extends Dialog {
        @BindView(R.id.cancel)
        TextView cancel;
        @BindView(R.id.message)
        TextView message;
        @BindView(R.id.ok)
        TextView ok;

        private final GameInfo gameInfo;

        public UnlockDialog(Context context, GameInfo gameInfo, final View.OnClickListener onClickListener) {
            super(context);
            this.gameInfo = gameInfo;
            com.cube.memorygames.ui.DialogUi.setContent(this, R.layout.dialog_unlock);
            ButterKnife.bind((Object) this, (Dialog) this);
            updateUnlockState();
            this.ok.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    // The balance may have changed since this dialog was opened.
                    if (!UnlockDialog.this.updateUnlockState()) {
                        return;
                    }
                    UnlockDialog.this.dismiss();
                    if (onClickListener != null) {
                        onClickListener.onClick(v);
                    }
                }
            });
            this.cancel.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    UnlockDialog.this.dismiss();
                }
            });
        }

        private boolean updateUnlockState() {
            int price = this.gameInfo.getStarsToUnlock();
            int money = MainMenuActivity.this.localDataManager.getLocalUser().money;
            this.message.setText(getContext().getString(R.string.dg_unlock_details,
                    getContext().getString(this.gameInfo.getGameNameRes()), price, money));
            boolean affordable = money >= price;
            TextView reason = (TextView) findViewById(R.id.dg_unlock_reason);
            reason.setVisibility(affordable ? View.GONE : View.VISIBLE);
            if (!affordable) {
                reason.setText(getContext().getString(R.string.dg_unlock_insufficient, price - money));
            }
            this.ok.setText(getContext().getString(R.string.dg_unlock_confirm, price));
            this.ok.setEnabled(affordable);
            this.ok.setAlpha(affordable ? 1.0f : 0.5f);
            return affordable;
        }

        @Override
        protected void onStart() {
            super.onStart();
            updateUnlockState();
        }
    }

    public static Intent newIntent(Context context, boolean showSecretGame) {
        Intent intent = new Intent(context, MainMenuActivity.class);
        intent.putExtra(EXTRA_CHECK_APP_RATE, true);
        intent.putExtra(EXTRA_SHOW_SECRET_GAME, showSecretGame);
        return intent;
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.onlineModePage = savedInstanceState != null
                ? savedInstanceState.getInt(STATE_ONLINE_MODE_PAGE, 0) : 0;
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        this.application = MemoryApplicationModel.getInstance();
        this.localDataManager = this.application.getLocalDataManager();
        OnlineSession.get().init(getApplicationContext());
        setContentView((int) R.layout.activity_main_menu);
        ButterKnife.bind((Activity) this);
        if (getIntent().getBooleanExtra(EXTRA_CHECK_APP_RATE, false)) {
            AppRater.app_launched(this, true);
        } else {
            AppRater.app_launched(this, false);
        }
        Typeface typefaceLight = Typeface.createFromAsset(getAssets(), "Roboto-Light.ttf");
        this.starsView.setTypeface(typefaceLight);
        this.ratingView.setTypeface(typefaceLight);
        showVerticalList();
        configureList(false);
        this.recyclerView.setAdapter(this.gameListAdapter);
        if (!displayCoinsHint()) {
            displayTopHint();
        }
        setupBottomBar(savedInstanceState);
        // Deep link straight into the Online tab (e.g. from a match
        // notification or an "enter online" intent). Posted because the
        // BottomBar only resolves tab ids once attached to the window.
        if (getIntent().getBooleanExtra(EXTRA_ONLINE, false)) {
            this.mBottomBar.post(new Runnable() {
                @Override
                public void run() {
                    MainMenuActivity.this.mBottomBar.selectTabWithId(R.id.bottomBarOnline);
                }
            });
        }
        updateStatistics();
        this.isSetupFinished = false;
        suggestWorkout();
    }

    private void suggestWorkout() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int startsCount = prefs.getInt(DialogTryWorkout.STARTS_COUNT_BEFORE_SHOW_KEY, 3);
        if (MemoryApplicationModel.getInstance().getLocalDataManager().getWorkoutRating() > 0.0f) {
            prefs.edit().putBoolean(DialogTryWorkout.TRY_WORKOUT_DIALOG_SHOW_KEY, false).apply();
        } else if (startsCount > 0) {
            prefs.edit().putInt(DialogTryWorkout.STARTS_COUNT_BEFORE_SHOW_KEY, startsCount - 1).apply();
        } else if (prefs.getBoolean(DialogTryWorkout.TRY_WORKOUT_DIALOG_SHOW_KEY, true)) {
            MemoryApplicationModel.getInstance().logEvent((Activity) this, MemoryApplicationModel.ANALYTICS_CATEGORY_WORKOUT, MemoryApplicationModel.ANALYTICS_EVENT_WORKOUT_DIALOG_SHOWED);
            new DialogTryWorkout(this, new OnClickListener() {
                public void onClick(View view) {
                    MemoryApplicationModel.getInstance().logEvent(MainMenuActivity.this, MemoryApplicationModel.ANALYTICS_CATEGORY_WORKOUT, MemoryApplicationModel.ANALYTICS_EVENT_WORKOUT_DIALOG_CLICKED);
                    MainMenuActivity.this.mBottomBar.selectTabAtPosition(1, false);
                }
            }).show();
        }
    }

    public void onBackPressed() {
        if (AppRater.checkGame248Offer(this)) {
            super.onBackPressed();
        }
    }

    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
    }

    private void setupBottomBar(Bundle savedInstanceState) {
        this.mBottomBar = (BottomBar) findViewById(R.id.bb_bottom_bar);
        this.mBottomBar.setOnTabSelectListener(new OnTabSelectListener() {
            public void onTabSelected(@IdRes int menuItemId) {
                int animationPadding = MainMenuActivity.this.getResources().getDimensionPixelOffset(R.dimen.padding_big);
                ViewAnimator.animate(MainMenuActivity.this.recyclerView).duration((long) 100).fadeOut().onStop(new Stop() {
                    public void onStop() {
                        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainMenuActivity.this);
                        int newTab = 0;
                        if (menuItemId == R.id.bottomBarCup) {
                            newTab = 0;
                            MainMenuActivity.this.configureList(false);
                            if (MainMenuActivity.this.recyclerView.getAdapter() != MainMenuActivity.this.gameListAdapter) {
                                MainMenuActivity.this.recyclerView.setAdapter(MainMenuActivity.this.gameListAdapter);
                            }
                            MainMenuActivity.this.recyclerView.setBackgroundColor(ContextCompat.getColor(MainMenuActivity.this, R.color.grid_stroke));
                            MainMenuActivity.this.gridSpaceItemDecorator.setSpace(MainMenuActivity.this.getResources().getDimensionPixelSize(R.dimen.cell_stroke_width));
                            MainMenuActivity.this.recyclerView.invalidateItemDecorations();
                            MainMenuActivity.this.gameListAdapter.setList(Games.get().getSprintGames());
                            MainMenuActivity.this.gameListAdapter.updateStatistics();
                            MainMenuActivity.this.panelHeader.setVisibility(0);
                            MainMenuActivity.this.divider.setVisibility(0);
                        } else if (menuItemId == R.id.bottomBarChallenge) {
                            newTab = 2;
                            MainMenuActivity.this.configureList(true);
                            if (MainMenuActivity.this.challengeAdapter == null) {
                                MainMenuActivity.this.challengeAdapter = new ChallengeAdapter(MainMenuActivity.this, MainMenuActivity.this.challengeCLickListener, (ChallengeJsonGame) MainMenuActivity.this.getIntent().getParcelableExtra(MainMenuActivity.EXTRA_CHALLENGE_GAME_INFO));
                            }
                            MainMenuActivity.this.recyclerView.setAdapter(MainMenuActivity.this.challengeAdapter);
                            MainMenuActivity.this.recyclerView.setBackgroundColor(ContextCompat.getColor(MainMenuActivity.this, R.color.grid_stroke));
                            MainMenuActivity.this.gridSpaceItemDecorator.setSpace(0);
                            MainMenuActivity.this.recyclerView.invalidateItemDecorations();
                            MainMenuActivity.this.panelHeader.setVisibility(0);
                            MainMenuActivity.this.divider.setVisibility(0);
                            MainMenuActivity.this.application.logEvent(MainMenuActivity.this, MemoryApplicationModel.ANALYTICS_CATEGORY_CHALLENGE, MemoryApplicationModel.ANALYTICS_EVENT_CHALLENGE_TAB_CLICKED);
                        } else if (menuItemId == R.id.bottomBarOnline) {
                            newTab = TAB_ONLINE;
                            // The one full-height row is a viewport. Only its mode-page
                            // ScrollViews scroll vertically; the shared RecyclerView must not.
                            MainMenuActivity.this.recyclerView.setLayoutManager(new LinearLayoutManager(
                                    MainMenuActivity.this, RecyclerView.VERTICAL, false) {
                                @Override
                                public boolean canScrollVertically() {
                                    return false;
                                }
                            });
                            if (MainMenuActivity.this.onlineAdapter == null) {
                                MainMenuActivity.this.onlineAdapter = new OnlineLobbyAdapter(MainMenuActivity.this,
                                        MainMenuActivity.this::updateHeaderStatistics);
                                MainMenuActivity.this.onlineAdapter.setSelectedPage(MainMenuActivity.this.onlineModePage);
                            }
                            MainMenuActivity.this.recyclerView.setAdapter(MainMenuActivity.this.onlineAdapter);
                            MainMenuActivity.this.recyclerView.setBackgroundColor(ContextCompat.getColor(MainMenuActivity.this, R.color.online_page));
                            MainMenuActivity.this.gridSpaceItemDecorator.setSpace(0);
                            MainMenuActivity.this.recyclerView.invalidateItemDecorations();
                            MainMenuActivity.this.panelHeader.setVisibility(View.VISIBLE);
                            MainMenuActivity.this.divider.setVisibility(View.VISIBLE);
                        } else if (menuItemId == R.id.bottomBarMenu) {
                            newTab = TAB_MENU;
                            MainMenuActivity.this.configureList(true);
                            if (MainMenuActivity.this.menuAdapter == null) {
                                MainMenuActivity.this.menuAdapter = new MenuAdapter(MainMenuActivity.this);
                            }
                            MainMenuActivity.this.recyclerView.setAdapter(MainMenuActivity.this.menuAdapter);
                            MainMenuActivity.this.recyclerView.setBackgroundColor(ContextCompat.getColor(MainMenuActivity.this, R.color.background3));
                            MainMenuActivity.this.gridSpaceItemDecorator.setSpace(MainMenuActivity.this.getResources().getDimensionPixelSize(R.dimen.cell_stroke_width));
                            MainMenuActivity.this.recyclerView.invalidateItemDecorations();
                            MainMenuActivity.this.panelHeader.setVisibility(8);
                            MainMenuActivity.this.divider.setVisibility(8);
                        } else if (menuItemId == R.id.bottomBarWorkout) {
                            MainMenuActivity.this.configureList(true);
                            newTab = 1;
                            if (MainMenuActivity.this.workoutAdapter == null) {
                                MainMenuActivity.this.workoutAdapter = new WorkoutAdapter(MainMenuActivity.this);
                            }
                            MainMenuActivity.this.recyclerView.setAdapter(MainMenuActivity.this.workoutAdapter);
                            MainMenuActivity.this.recyclerView.setBackgroundColor(-1);
                            MainMenuActivity.this.gridSpaceItemDecorator.setSpace(MainMenuActivity.this.getResources().getDimensionPixelSize(R.dimen.cell_stroke_width));
                            MainMenuActivity.this.recyclerView.invalidateItemDecorations();
                            MainMenuActivity.this.panelHeader.setVisibility(8);
                            MainMenuActivity.this.divider.setVisibility(8);
                            sharedPreferences.edit().putBoolean(MainMenuActivity.PREF_BADGE_CHALLENGE_CLICKED, true).apply();
                            MainMenuActivity.this.application.logEvent(MainMenuActivity.this, MemoryApplicationModel.ANALYTICS_CATEGORY_WORKOUT, MemoryApplicationModel.ANALYTICS_EVENT_WORKOUT_TAB_CLICKED);
                        }
                        MainMenuActivity.this.updateHeaderStatistics();
                        sharedPreferences.edit().putInt(MainMenuActivity.PREF_LAST_TAB_POSITION, newTab).apply();
                    }
                }).thenAnimate(MainMenuActivity.this.recyclerView).duration((long) 100).fadeIn().andAnimate(MainMenuActivity.this.recyclerView).duration((long) 100).interpolator(new DecelerateInterpolator()).translationY((float) animationPadding, 0.0f).start();
            }
        });
        int selectedTab = PreferenceManager.getDefaultSharedPreferences(this).getInt(PREF_LAST_TAB_POSITION, 0);
        if (selectedTab > TAB_ONLINE) {
            selectedTab = TAB_SPRINT;
        }
        showBadge();
        this.mBottomBar.selectTabAtPosition(selectedTab, false);
    }

    private void showBadge() {
        if (!PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PREF_BADGE_CHALLENGE_CLICKED, false)) {
            ((BottomBarTab) this.mBottomBar.getTabAtPosition(2)).setBadgeCount(1);
        }
    }

    private void showVerticalList() {
        this.gridSpaceItemDecorator = new GridSpaceItemDecorator(getResources().getDimensionPixelSize(R.dimen.cell_stroke_width));
        this.recyclerView.addItemDecoration(this.gridSpaceItemDecorator);
        this.gameListAdapter = new GameListAdapter(this, Games.get().getSprintGames(), this.gameClickListener);
        this.recyclerView.setClipToPadding(true);
        this.recyclerView.setPadding(0, 0, 0, 0);
        this.recyclerView.clearOnScrollListeners();
    }

    private void configureList(boolean alwaysList) {
        if (!getResources().getBoolean(R.bool.isTablet) || alwaysList) {
            this.recyclerView.setLayoutManager(new LinearLayoutManager(this, RecyclerView.VERTICAL, false));
        } else {
            this.recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        }
    }

    protected void onResume() {
        super.onResume();
        updateStatistics();
    }

    protected void onDestroy() {
        if (this.onlineAdapter != null) {
            this.onlineAdapter.dispose();
        }
        super.onDestroy();
    }

    private void launchGame(GameInfo gameInfo) {
        Intent intent = new Intent(this, StartGameActivity.class);
        intent.putExtra(StartGameActivity.EXTRA_CHALLENGE, false);
        intent.putExtra(EXTRA_GAME_INFO, gameInfo);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_to_left, R.anim.no_change);
        this.application.setLastGamePlayed(gameInfo.getId());
    }

    @OnClick({R.id.rating_icon})
    public void topClick() {
        this.application.logEvent((Activity) this, MemoryApplicationModel.ANALYTICS_CATEGORY_GENERAL, MemoryApplicationModel.ANALYTICS_EVENT_TOP_CLICKED);
        startActivity(new Intent(this, TrophiesActivity.class));
    }

    @OnClick({R.id.text_top_bar_rating})
    protected void topClick2() {
        topClick();
    }

    private void updateHeaderStatistics() {
        if (!isFinishing()) {
            this.starsView.setText(String.valueOf(this.mBottomBar.getCurrentTabPosition() == TAB_ONLINE
                    ? OnlineSession.get().getOnlineCoins() : this.localDataManager.getLocalUser().money));
            this.ratingView.setText(String.valueOf(round((double) this.localDataManager.getLocalUser().rating, 1)));
        }
    }

    public void updateStatistics() {
        if (!isFinishing()) {
            updateHeaderStatistics();
            if (this.mBottomBar.getCurrentTabPosition() == 0) {
                this.gameListAdapter.updateStatistics();
            } else if (this.mBottomBar.getCurrentTabPosition() == 2) {
                if (this.challengeAdapter != null) {
                    this.challengeAdapter.notifyDataSetChanged();
                }
            } else if (this.mBottomBar.getCurrentTabPosition() == TAB_MENU) {
                if (this.menuAdapter != null) {
                    this.menuAdapter.fillMenuItems();
                    this.menuAdapter.notifyDataSetChanged();
                }
            } else if (this.mBottomBar.getCurrentTabPosition() == TAB_ONLINE) {
                if (this.onlineAdapter != null) {
                    this.onlineAdapter.resume();
                }
            } else if (this.mBottomBar.getCurrentTabPosition() == 1 && this.workoutAdapter != null) {
                this.workoutAdapter.notifyDataSetChanged();
            }
        }
    }

    public Point getWindowSize() {
        Display display = ((WindowManager) getSystemService("window")).getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        return size;
    }

    public void onStatisticUpdated() {
        updateStatistics();
    }

    private void unlockGame(GameInfo gameInfo, View view) {
        String unlockedId = "game" + (Integer.parseInt(gameInfo.getId()) + 1);
        LocalUser user = this.localDataManager.getLocalUser();
        if (user.money < gameInfo.getStarsToUnlock() || user.parseUnlockedContent().contains(unlockedId)
                || user.vip || user.gamesUnlocked) {
            return;
        }
        if (!(view == null || view.getParent() == null)) {
            View lockView = ((ViewGroup) view.getParent()).findViewById(R.id.lock_container);
            if (lockView != null) {
                lockView.setVisibility(8);
            }
        }
        this.localDataManager.addCoinsTransaction(LocalDataManager.TYPE_BUY_GAME + gameInfo.getId(), -gameInfo.getStarsToUnlock(), "game" + (Integer.parseInt(gameInfo.getId()) + 1));
        updateStatistics();
        this.application.logEvent((Activity) this, MemoryApplicationModel.ANALYTICS_CATEGORY_UNLOCK, MemoryApplicationModel.ANALYTICS_EVENT_UNLOCKED_GAME_PREFIX + gameInfo.getId());
    }

    public static double round(double value, int places) {
        if (places < 0) {
            throw new IllegalArgumentException();
        }
        long factor = (long) Math.pow(10.0d, (double) places);
        return ((double) Math.round(value * ((double) factor))) / ((double) factor);
    }

    /** The original dialog offered coins via purchases/Facebook/ads; removed. */
    public void showMoneyDialog(boolean fromOnline) {
    }

    @OnClick({R.id.star})
    void showMoneyDialogClick() {
        showMoneyDialog(false);
    }

    @OnClick({R.id.text_top_bar_stars_count})
    void showMoneyDialogFromStarClick() {
        showMoneyDialog(false);
    }

    public boolean isIabSetupFinished() {
        return this.isSetupFinished;
    }

    private boolean displayCoinsHint() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (sharedPreferences.getBoolean(PREF_COINS_BUBBLE_SHOWED, false)) {
            return false;
        }
        int gamePlayed = sharedPreferences.getStringSet(PREF_GAMES_PLAYED, new HashSet()).size();
        System.err.println("gamePlayed = " + gamePlayed);
        if (gamePlayed < 2) {
            return false;
        }
        this.coinsHint.setVisibility(0);
        this.coinsHint.bringToFront();
        MarginLayoutParams layoutParams = (MarginLayoutParams) this.coinsHint.getLayoutParams();
        layoutParams.topMargin = getResources().getDimensionPixelSize(R.dimen.top_bar_height);
        this.coinsHint.setLayoutParams(layoutParams);
        float distance = getResources().getDimension(R.dimen.anim_distance);
        ViewAnimator.animate(this.coinsHint).alpha(0.0f, 1.0f).translationY(-distance, 0.0f)
                .interpolator(new DecelerateInterpolator()).duration(240)
                .thenAnimate(this.coinsHint).startDelay(7600).fadeOut().duration(200)
                .onStop(() -> this.coinsHint.setVisibility(View.INVISIBLE)).start();
        sharedPreferences.edit().putBoolean(PREF_COINS_BUBBLE_SHOWED, true).apply();
        return true;
    }

    private boolean displayTopHint() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (sharedPreferences.getBoolean(PREF_TOP_BUBBLE_SHOWED, false)) {
            return false;
        }
        int gamePlayed = sharedPreferences.getStringSet(PREF_GAMES_PLAYED, new HashSet()).size();
        System.err.println("gamePlayed = " + gamePlayed);
        if (gamePlayed < 4) {
            return false;
        }
        this.topHint.setVisibility(0);
        this.topHint.bringToFront();
        MarginLayoutParams layoutParams = (MarginLayoutParams) this.topHint.getLayoutParams();
        layoutParams.topMargin = getResources().getDimensionPixelSize(R.dimen.top_bar_height);
        this.topHint.setLayoutParams(layoutParams);
        float distance = getResources().getDimension(R.dimen.anim_distance);
        ViewAnimator.animate(this.topHint).fadeIn().duration(1000).thenAnimate(this.topHint).interpolator(new LinearInterpolator()).translationY(0.0f, distance, 0.0f, distance, 0.0f, distance, 0.0f, distance, 0.0f, distance, 0.0f).duration(3000).thenAnimate(this.topHint).startDelay(4000).fadeOut().duration(1000).start();
        sharedPreferences.edit().putBoolean(PREF_TOP_BUBBLE_SHOWED, true).apply();
        return true;
    }

    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(STATE_ONLINE_MODE_PAGE, this.onlineAdapter != null
                ? this.onlineAdapter.getSelectedPage() : this.onlineModePage);
        super.onSaveInstanceState(outState);
    }
}
