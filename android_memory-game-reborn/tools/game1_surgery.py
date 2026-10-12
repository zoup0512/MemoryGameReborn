#!/usr/bin/env python3
"""Offline surgery on Game1MemoryGridActivity (base class of most games).

Removes: AdMob interstitial, Appodeal callbacks, Google Play billing setup,
Facebook CallbackManager, the network SyncDataAsyncTask, the online progress
dialog, the Play-Store offer dialogs and the ad-gated routing.
Keeps: all local game flow, challenge/workout modes, coins, lives, trophies.
showAdsIfNeeded(type) keeps its signature but dispatches straight to the
target action, so every untouched call site keeps working.
"""
from pathlib import Path

GAME1 = Path(r"D:\Code\workspace\android\memory-game\memory-game\src\main\java\com\cube\memorygames\games\Game1MemoryGridActivity.java")
text = GAME1.read_text(encoding="utf-8")
orig_len = len(text.splitlines())


def sub(old, new, label):
    global text
    if old not in text:
        raise SystemExit(f"NOT FOUND: {label}")
    text = text.replace(old, new)


# ---------- imports ----------
for imp in [
    "import com.appodeal.ads.Appodeal;\n",
    "import com.appodeal.ads.InterstitialCallbacks;\n",
    "import com.cube.memorygames.OfferDialog.PurchaseDialogType;\n",
    "import com.cube.memorygames.OfferDialogSelector;\n",
    "import com.cube.memorygames.activity.PlayOnlineActivity;\n",
    "import com.cube.memorygames.api.network.SyncDataAsyncTask;\n",
    "import com.cube.memorygames.billing.IabHelper;\n",
    "import com.cube.memorygames.billing.IabHelper.IabAsyncInProgressException;\n",
    "import com.cube.memorygames.billing.IabHelper.OnIabSetupFinishedListener;\n",
    "import com.cube.memorygames.billing.IabResult;\n",
    "import com.cube.memorygames.ui.OnlineProgressDialog;\n",
    "import com.facebook.CallbackManager;\n",
    "import com.facebook.CallbackManager.Factory;\n",
    "import com.google.android.gms.ads.AdListener;\n",
    "import com.google.android.gms.ads.AdRequest.Builder;\n",
    "import com.google.android.gms.ads.InterstitialAd;\n",
]:
    sub(imp, "", f"import {imp.strip()}")

# ---------- fields ----------
sub("    CallbackManager callbackManager;\n", "", "field callbackManager")
sub("    private IabHelper iabHelper;\n", "", "field iabHelper")
sub("    private boolean isSetupFinished;\n", "", "field isSetupFinished")
sub("    private InterstitialAd mInterstitialAd;\n", "", "field mInterstitialAd")
sub("    private int selectedAdType;\n", "", "field selectedAdType")
sub("    private double challengeAdsProbability;\n", "", "field challengeAdsProbability")

sub("        ButterKnife.bind((Activity) this);\n        this.callbackManager = Factory.create();\n",
    "        ButterKnife.bind((Activity) this);\n", "onCreate callbackManager")

# ---------- onCreate: online extras ----------
sub("""        this.isOnlineGame = getIntent().getBooleanExtra(PlayOnlineActivity.EXTRA_ONLINE_MODE, false);
        this.onlineProgressDelay = getIntent().getIntExtra(PlayOnlineActivity.EXTRA_ONLINE_PROGRESS_TIMEOUT, 0);
        this.onlineGameNumber = getIntent().getIntExtra(PlayOnlineActivity.EXTRA_ONLINE_GAME_NUMBER, 0);
        this.onlineTotalGames = getIntent().getIntExtra(PlayOnlineActivity.EXTRA_ONLINE_TOTAL_GAMES, 0);
        this.gameSeed = getIntent().getLongExtra(PlayOnlineActivity.EXTRA_GAME_SEED, System.currentTimeMillis());
        initRandom();
        this.challengeAdsProbability = GameRandom.nextDouble();
""",
    """        this.gameSeed = System.currentTimeMillis();
        initRandom();
""", "onCreate online extras")

# ---------- onCreate: drop purchase/ad setup, keep grid init ----------
start = text.index("        setUpPurchases();\n")
end = text.index("        this.gridContainer = (ViewGroup) findViewById(R.id.grid_container);\n")
text = text[:start] + text[end:]

# ---------- onDestroy ----------
sub("""    protected void onDestroy() {
        destroyPurchases();
        new SyncDataAsyncTask(MemoryApplicationModel.getInstance().getLocalDataManager()).execute(new Void[0]);
        killOnlineTimer();
        super.onDestroy();
    }
""",
    """    protected void onDestroy() {
        killOnlineTimer();
        super.onDestroy();
    }
""", "onDestroy")

# ---------- onActivityResult ----------
sub("""    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        this.iabHelper.handleActivityResult(requestCode, resultCode, data);
        this.callbackManager.onActivityResult(requestCode, resultCode, data);
    }

""", "", "onActivityResult")

# ---------- requestNewInterstitial ----------
sub("""    private void requestNewInterstitial() {
        this.mInterstitialAd.loadAd(new Builder().build());
    }

""", "", "requestNewInterstitial")

# ---------- onSaveInstanceState ----------
sub("""        outState.putInt(BUY_STARS_CLICKED_COUNT, this.buyStarsClickedCountCount);
        outState.putInt("selectedAdType", this.selectedAdType);
        outState.putInt(ONLINE_PROGRESS_DELAY, this.onlineProgressDelay);
        outState.putInt(MAX_LEVEL, this.maxLevel);
        outState.putInt(ONLINE_SCORE, this.onlineScore);
        outState.putLong(PAUSED_TIME, this.pausedTime);
        outState.putLong(PAUSED_COUNT, this.pausedCount);
        outState.putInt(USER_SCORE, this.userScore);
        outState.putLong(PAUSED_DURATION, this.pausedDuration);
        outState.putBoolean(ONLINE_REPLAYED, this.onlineReplayed);
""",
    """        outState.putInt(BUY_STARS_CLICKED_COUNT, this.buyStarsClickedCountCount);
        outState.putInt(MAX_LEVEL, this.maxLevel);
        outState.putInt(ONLINE_SCORE, this.onlineScore);
        outState.putLong(PAUSED_TIME, this.pausedTime);
        outState.putLong(PAUSED_COUNT, this.pausedCount);
        outState.putInt(USER_SCORE, this.userScore);
        outState.putLong(PAUSED_DURATION, this.pausedDuration);
""", "onSaveInstanceState")

# ---------- TimeoutDialog onShow: drop offer dialogs ----------
sub("""                    Game1MemoryGridActivity.this.setCurrentLevel();
                    if (!Game1MemoryGridActivity.this.isChallenge && !Game1MemoryGridActivity.this.isOnlineGame && !Game1MemoryGridActivity.this.isWorkout && !Game1MemoryGridActivity.this.showProDialog() && !Game1MemoryGridActivity.this.showRemoveAdsDialog() && !Game1MemoryGridActivity.this.showProDiscountDialog()) {
                        LocalUser localUser = Game1MemoryGridActivity.this.application.getLocalDataManager().getLocalUser();
                        if (!localUser.fromDeepLink && !localUser.deepLinkSent && !localUser.vip && !localUser.gamesUnlocked) {
                            Game1MemoryGridActivity.this.showSecretGameDialog();
                        }
                    }
""",
    """                    Game1MemoryGridActivity.this.setCurrentLevel();
""", "TimeoutDialog onShow")

# ---------- showOnlineReplayDialog: no OnlineProgressDialog ----------
sub("""    protected void showOnlineReplayDialog() {
        this.foreground.setVisibility(0);
        new OnlineProgressDialog(this, this.onlineScore, this.gameInfo.getId(), this.onlineGameNumber, this.onlineTotalGames, this.onlineProgressDelay, new OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.putExtra(PlayOnlineActivity.EXTRA_ONLINE_SCORE, Game1MemoryGridActivity.this.onlineScore);
                intent.putExtra(PlayOnlineActivity.EXTRA_LEVEL, Game1MemoryGridActivity.this.progression.getLevelNumber());
                Game1MemoryGridActivity.this.setResult(-1, intent);
                Game1MemoryGridActivity.this.finish();
                SoundUtils.stopBackgroundSound();
            }
        }).show();
    }
""",
    """    protected void showOnlineReplayDialog() {
        // online mode no longer exists; leave the game instead
        openMainMenu();
    }
""", "showOnlineReplayDialog")

# ---------- showAdsIfNeeded: direct dispatch ----------
start = text.index("    private void showAdsIfNeeded(int type) {")
end = text.index("    private void setCurrentLevel() {")
new_body = """    private void showAdsIfNeeded(int type) {
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

"""
text = text[:start] + new_body + text[end:]

# ---------- offer dialog methods ----------
start = text.index("    private boolean showProDialog() {")
end = text.index("    protected void displayLevelNumber() {")
text = text[:start] + text[end:]

# ---------- setUpPurchases / destroyPurchases ----------
sub("""    private void setUpPurchases() {
        this.iabHelper = new IabHelper(this, MemoryApplicationModel.BASE_64_ENCODED_PUBLIC_KEY);
        this.isSetupFinished = false;
        this.iabHelper.startSetup(new OnIabSetupFinishedListener() {
            public void onIabSetupFinished(IabResult result) {
                Game1MemoryGridActivity.this.isSetupFinished = result.isSuccess();
            }
        });
    }

    private void destroyPurchases() {
        if (this.iabHelper != null) {
            try {
                this.iabHelper.dispose();
            } catch (IabAsyncInProgressException e) {
            } catch (IllegalArgumentException e2) {
            }
        }
        this.iabHelper = null;
    }

""", "", "setUpPurchases/destroyPurchases")

sub("""    public boolean isIabSetupFinished() {
        return this.isSetupFinished;
    }
""",
    """    public boolean isIabSetupFinished() {
        return false;
    }
""", "isIabSetupFinished")

# ---------- challenge finish dialog: no ad gating ----------
sub("""                dialog.dismiss();
                if (Game1MemoryGridActivity.this.challengeAdsProbability < Game1MemoryGridActivity.CHALLENGE_BEFORE_DIALOG_ADS_RANGE) {
                    Game1MemoryGridActivity.this.challengeHomeClickListener.onClick(null);
                } else {
                    Game1MemoryGridActivity.this.showAdsIfNeeded(31);
                }
""",
    """                dialog.dismiss();
                Game1MemoryGridActivity.this.challengeHomeClickListener.onClick(null);
""", "challenge home listener")
sub("""                dialog.dismiss();
                SoundUtils.stopBackgroundSound();
                if (Game1MemoryGridActivity.this.challengeAdsProbability < Game1MemoryGridActivity.CHALLENGE_BEFORE_DIALOG_ADS_RANGE) {
                    Game1MemoryGridActivity.this.challengeNextClickListener.onClick(null);
                } else {
                    Game1MemoryGridActivity.this.showAdsIfNeeded(33);
                }
""",
    """                dialog.dismiss();
                SoundUtils.stopBackgroundSound();
                Game1MemoryGridActivity.this.challengeNextClickListener.onClick(null);
""", "challenge next listener")
sub("""                dialog.dismiss();
                if (Game1MemoryGridActivity.this.challengeAdsProbability < Game1MemoryGridActivity.CHALLENGE_BEFORE_DIALOG_ADS_RANGE) {
                    Game1MemoryGridActivity.this.challengeReplayClickListener.onClick(null);
                } else {
                    Game1MemoryGridActivity.this.showAdsIfNeeded(32);
                }
""",
    """                dialog.dismiss();
                Game1MemoryGridActivity.this.challengeReplayClickListener.onClick(null);
""", "challenge replay listener")
sub("""        if (this.challengeAdsProbability < CHALLENGE_BEFORE_DIALOG_ADS_RANGE) {
            showAdsIfNeeded(34);
        } else {
            this.challengeOpenDialogClickListener.onClick(null);
        }
""",
    """        this.challengeOpenDialogClickListener.onClick(null);
""", "challenge open dialog")

# ---------- takeLife: no SharingDialog, video retry -> plain retry ----------
sub("""            new FailDialog(this, this.buyStarsClickedCountCount, new OnDialogClickListener() {
                public void onDialogClick(Dialog dialog) {
                    if (Game1MemoryGridActivity.this.localDataManager.getLocalUser().money < 30) {
                        new SharingDialog(Game1MemoryGridActivity.this.callbackManager, Game1MemoryGridActivity.this, Game1MemoryGridActivity.this, Game1MemoryGridActivity.this.iabHelper, Game1MemoryGridActivity.this, null, false).show();
                        return;
                    }
""",
    """            new FailDialog(this, this.buyStarsClickedCountCount, new OnDialogClickListener() {
                public void onDialogClick(Dialog dialog) {
                    if (Game1MemoryGridActivity.this.localDataManager.getLocalUser().money < 30) {
                        dialog.dismiss();
                        Game1MemoryGridActivity.this.showTimeout(false);
                        return;
                    }
""", "takeLife fail dialog")

# ---------- unused constants cleanup (keep names referenced elsewhere) ----------
sub("    private static final double CHALLENGE_BEFORE_DIALOG_ADS_RANGE = 0.25d;\n", "", "const ads range")

GAME1.write_text(text, encoding="utf-8")
print(f"Game1 surgery done: {orig_len} -> {len(text.splitlines())} lines")
