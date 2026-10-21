package com.cube.memorygames.api.online;

import com.cube.memorygames.diagnostics.DiagnosticLog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.cube.memorygames.MemoryApplicationModel;
import com.cube.memorygames.api.online.OnlineMessages.AuthOk;
import com.cube.memorygames.api.online.OnlineMessages.MatchEnd;
import com.cube.memorygames.api.online.OnlineMessages.MatchFound;
import com.cube.memorygames.api.online.OnlineMessages.OpponentInfo;
import com.cube.memorygames.api.online.OnlineMessages.OpponentScore;
import com.cube.memorygames.api.online.OnlineMessages.RoundEnd;
import com.cube.memorygames.api.online.OnlineMessages.RoundStart;
import com.cube.memorygames.Games;
import com.cube.memorygames.MainMenuActivity;
import com.cube.memorygames.model.GameInfo;
import com.cube.memorygames.ui.TimerView;
import com.github.florent37.viewanimator.ViewAnimator;
import com.memory.brain.training.games.R;

import java.util.Locale;

/**
 * Orchestrates one online PK match: matchmaking feedback, per-round launch
 * of the game activities, intermission scoreboards and the final settlement.
 *
 * The shell replicates the original PlayOnlineActivity: the white top bar
 * (back arrow, title, coin count) of activity_play_online.xml, the VS header
 * of fragment_header.xml (ringed avatars with crown/elo badges, the ic_versus
 * bolt, the pulsing search circles), the waiting board's button_pro_normal
 * card with the 100dip TimerView countdown (dialog_online_progress.xml), the
 * settlement card of dialog_online_result.xml and the bottom white bar with
 * the 180dip green actions.
 */
public class OnlineMatchActivity extends AppCompatActivity implements OnlineSession.MatchListener {

    private static final int REQUEST_ROUND = 1001;
    private static final int REQUEST_TUTORIAL = 1002;
    /** Origin TUTORIAL_COUNT: per-game pre-round tutorials show twice. */
    private static final int TUTORIAL_COUNT = 2;
    private static final String PREF_TUTORIAL_SHOW = "OnlineTutorialShow";
    private static final long COUNTDOWN_MS = 3000L;
    /** Mirrors the server's acceptTimeoutMs (START gate window). */
    private static final int ACCEPT_WINDOW_MS = 30000;
    /** Origin START_TIMEOUT: the pre-round game list's countdown disc. */
    private static final long GAME_LIST_TIMEOUT_MS = 5000L;
    /** Original search-header pulse cadence (HeaderFragment: 400ms steps). */
    private static final long PULSE_INTERVAL_MS = 400L;
    private static final long PULSE_STEP_MS = 200L;

    // Top bar (activity_play_online.xml).
    private TextView coinsView;
    // VS header (fragment_header.xml).
    private OnlineUi.AvatarBlock myAvatar;
    private OnlineUi.AvatarBlock oppAvatar;
    private TextView myNameView;
    private TextView oppNameView;
    private ImageView boltView;
    private TextView modeChip;
    private int matchModeLabel = R.string.online_mode_ranked;
    private final View[] pulseCircles = new View[3];
    private int pulsePosition;
    // Board container and the boards swapped inside it.
    private FrameLayout content;
    private LinearLayout reconnectBar;
    private TextView reconnectText;
    private LinearLayout searchBoard;
    private LinearLayout matchBoard;
    private LinearLayout statsBoard;
    /** Views faded in one-by-one on the stats board (origin's chain). */
    private java.util.List<View> statsFadeViews;
    private LinearLayout gameListBoard;
    /** True while the accept-waiting card is up (label swap target). */
    private boolean acceptWaiting;
    private RoundStart pendingTutorialStart;
    private GameInfo pendingTutorialInfo;
    private int pendingTutorialRequestCode = -1;
    private TimerView gameListTimer;
    private RoundWaitCard intermissionCard;
    private boolean intermissionVisible;
    private boolean activityStarted;
    private LinearLayout waitingCard;
    private LinearLayout card;
    // Search board.
    private TextView searchMessage;
    private TextView searchHint;
    // Match board (round line + live score + launch countdown).
    private TextView statusView;
    private TextView detailView;
    private TextView scoreView;
    // Waiting card (dialog_online_progress.xml).
    private TextView waitingTitle;
    private TextView waitingLine;
    private TimerView timerView;
    // Settlement card (dialog_online_result.xml).
    private TextView titleView;
    private ImageView doodleView;
    private LinearLayout coinRow;
    private TextView coinDelta;
    private TextView coinTotal;
    private LinearLayout ratingRow;
    private TextView ratingDelta;
    private TextView ratingTotal;
    private TextView resultSubtitle;
    private TextView okButton;
    // Bottom bar (activity_play_online.xml buttonsContainer).
    private LinearLayout bottomBar;
    private TextView bottomButton;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean roundLaunchScheduled;
    private RoundLaunchGate launchGate = new RoundLaunchGate();
    private boolean destroyed;
    private int launchedRound = -1;
    private String diagnosticMatchId;
    private String finishReason="framework";

    private void diagnostic(String event,Object... extra) {
        try {
            String live=OnlineSession.get().getMatchId();if(live!=null)diagnosticMatchId=live;
            Object[] fields=new Object[extra.length+4];
            System.arraycopy(extra,0,fields,0,extra.length);
            fields[extra.length]="matchId";fields[extra.length+1]=diagnosticMatchId;
            fields[extra.length+2]="round";fields[extra.length+3]=launchedRound;
            DiagnosticLog.event(event,fields);
        } catch(RuntimeException ignored) { }
    }

    /**
     * The original HeaderFragment pulse: a position walks 0..5 and each of
     * the three grey circles is visible while the wave window covers it.
     */
    private final Runnable pulseRunnable = new Runnable() {
        @Override
        public void run() {
            for (int i = 0; i < OnlineMatchActivity.this.pulseCircles.length; i++) {
                final View circle = OnlineMatchActivity.this.pulseCircles[i];
                if (i >= OnlineMatchActivity.this.pulsePosition
                        || i <= OnlineMatchActivity.this.pulsePosition - 4) {
                    if (circle.getVisibility() == View.VISIBLE) {
                        circle.animate().scaleX(0.0f).scaleY(0.0f)
                                .setDuration(PULSE_STEP_MS).withEndAction(new Runnable() {
                                    @Override
                                    public void run() {
                                        circle.setVisibility(View.INVISIBLE);
                                    }
                                }).start();
                    }
                } else if (circle.getVisibility() != View.VISIBLE) {
                    circle.setVisibility(View.VISIBLE);
                    circle.setScaleX(0.0f);
                    circle.setScaleY(0.0f);
                    circle.animate().scaleX(1.0f).scaleY(1.0f).setDuration(PULSE_STEP_MS).start();
                }
            }
            OnlineMatchActivity.this.pulsePosition =
                    (OnlineMatchActivity.this.pulsePosition + 1) % 6;
            OnlineMatchActivity.this.handler.postDelayed(this, PULSE_INTERVAL_MS);
        }
    };

    private final Runnable intermissionTick = new Runnable() {
        @Override
        public void run() {
            if (!intermissionVisible || !activityStarted || isFinishing()
                    || OnlineSession.get().getMatchId() == null) {
                return;
            }
            updateIntermissionStatus();
            handler.postDelayed(this, 250L);
        }
    };

    // ------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        diagnostic("match_create","reason",savedInstanceState==null?"create":"recreate","stack",DiagnosticLog.callerStack());
        OnlineSession.get().init(getApplicationContext());
        setContentView(buildView());
        OnlineSession.get().setMatchListener(this);
        if (OnlineSession.get().isInMatch()) {
            showMatchUi();
            if (OnlineSession.get().getLastRoundEnd() != null) {
                showIntermissionUi();
            } else if (OnlineSession.get().getCurrentRound() < 0
                    && OnlineSession.get().hasRequestedMatchStart()) {
                showAcceptWaitingUi();
            } else if (OnlineSession.get().isPracticeMode()) {
                restorePracticeRound();
            }
        } else {
            showSearchUi();
            OnlineSession.get().startMatchmaking();
        }
    }

    /** Keep the preparation token across in-process recreation, including an open tutorial. */
    private static final class RetainedRound {
        RoundLaunchGate gate;
        RoundStart tutorial;
        GameInfo info;
        int requestCode;
    }

    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        RetainedRound retained = new RetainedRound();
        retained.gate = launchGate;
        retained.tutorial = pendingTutorialStart;
        retained.info = pendingTutorialInfo;
        retained.requestCode = pendingTutorialRequestCode;
        return retained;
    }

    private void restorePracticeRound() {
        RoundStart snapshot = OnlineSession.get().getRoundStartForRestore();
        if (snapshot == null) return;
        RetainedRound retained = (RetainedRound) getLastCustomNonConfigurationInstance();
        if (retained != null && RoundIntermission.canLaunch(retained.gate.token(),
                OnlineSession.get().getMatchId(), snapshot.round, null)) {
            launchGate = retained.gate;
            if (!snapshot.preparing) launchGate.accept(snapshot);
            launchedRound = snapshot.round;
            if (retained.tutorial != null) {
                pendingTutorialStart = retained.tutorial;
                pendingTutorialInfo = retained.info;
                pendingTutorialRequestCode = retained.requestCode;
                roundLaunchScheduled = true;
                return;
            }
            if (launchGate.canComplete(launchGate.token())) {
                // Recreate only the interrupted countdown, never the tutorial.
                roundLaunchScheduled = true;
                showGameListOrCountdown(launchGate.token(), Games.get().getGameBiId(snapshot.gameId));
                return;
            }
        }
        onRoundStart(snapshot);
    }

    @Override
    protected void onStart() {
        super.onStart();
        this.activityStarted = true;
        if (this.intermissionVisible) {
            this.handler.removeCallbacks(this.intermissionTick);
            this.intermissionTick.run();
        }
    }

    @Override
    protected void onStop() {
        this.activityStarted = false;
        this.handler.removeCallbacks(this.intermissionTick);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        diagnostic("match_destroy","reason",isChangingConfigurations()?"configuration":isFinishing()?finishReason:"destroy","stack",DiagnosticLog.callerStack());
        this.destroyed = true;
        this.roundLaunchScheduled = false;
        this.handler.removeCallbacksAndMessages(null);
        // Clear only our own registration: returning to the lobby re-registers
        // the adapter in onResume, which runs BEFORE this onDestroy — an
        // unconditional null would deafen the lobby (device finding 2026-10-04).
        if (OnlineSession.get().getMatchListener() == this) {
            OnlineSession.get().setMatchListener(null);
        }
        super.onDestroy();
    }

    @Override
    public void finish() {
        diagnostic("match_finish","reason",finishReason,"stack",DiagnosticLog.callerStack());
        super.finish();
    }

    @Override
    public void onBackPressed() {
        OnlineSession session = OnlineSession.get();
        if (session.isInMatch()) {
            diagnostic("exit_dialog","reason","exit_confirm");
            com.cube.memorygames.ui.DialogUi.showOnlineExit(this, () -> {
                diagnostic("exit_confirm","reason","user_resign");
                finishReason="user_resign";
                OnlineSession.get().resign();
                finish();
            }, () -> diagnostic("exit_cancel","reason","exit_cancel"));
            return;
        }
        finishReason="back_no_match";
        session.cancelMatchmaking();
        super.onBackPressed();
    }

    // ------------------------------------------------------------------- UI

    private View buildView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(OnlineUi.color(this, R.color.online_page));
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(buildTopBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                OnlineUi.dim(this, R.dimen.online_toolbar_height)));

        root.addView(buildVsHeader(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        this.reconnectBar = buildReconnectBar();
        this.reconnectBar.setVisibility(View.GONE);
        root.addView(this.reconnectBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        this.content = new FrameLayout(this);
        this.content.setBackgroundColor(OnlineUi.color(this, R.color.online_page));
        this.content.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        root.addView(this.content);

        View divider = new View(this);
        divider.setBackgroundColor(OnlineUi.color(this, R.color.online_divider));
        root.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));

        this.bottomBar = buildBottomBar();
        root.addView(this.bottomBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        this.searchBoard = buildSearchBoard();
        this.matchBoard = buildMatchBoard();
        this.statsBoard = null;
        this.waitingCard = buildWaitingCard();
        this.card = buildSettlementCard();
        return root;
    }

    /** Modern toolbar with a compact, tappable-looking coin balance chip. */
    private View buildTopBar() {
        OnlineUi.ToolbarBlock toolbar = OnlineUi.toolbar(this, R.string.tab_online,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onBackPressed();
                    }
                });

        LinearLayout coinChip = new LinearLayout(this);
        coinChip.setOrientation(LinearLayout.HORIZONTAL);
        coinChip.setGravity(Gravity.CENTER);
        coinChip.setBackgroundResource(R.drawable.online_chip_background);
        coinChip.setPadding(dp(10), 0, dp(12), 0);
        coinChip.setMinimumHeight(OnlineUi.dim(this, R.dimen.online_chip_height));

        ImageView coin = new ImageView(this);
        coin.setImageResource(R.drawable.ic_coin);
        LinearLayout.LayoutParams coinParams = new LinearLayout.LayoutParams(dp(20), dp(20));
        coinParams.rightMargin = dp(6);
        coinChip.addView(coin, coinParams);

        this.coinsView = OnlineUi.text(this, "", 14.0f,
                R.color.online_text_primary, OnlineUi.medium(this));
        this.coinsView.setGravity(Gravity.CENTER_VERTICAL);
        this.coinsView.setSingleLine(true);
        coinChip.addView(this.coinsView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        toolbar.actions.addView(coinChip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        refreshCoins();
        return toolbar.view;
    }

    /** Persistent in-match connectivity notice; hidden again on reconnect. */
    private LinearLayout buildReconnectBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setBackgroundColor(OnlineUi.color(this, R.color.online_warning_soft));
        bar.setPadding(dp(16), dp(9), dp(16), dp(9));

        View dot = new View(this);
        dot.setBackground(OnlineUi.roundedBackground(this, R.color.online_warning, 20));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(8), dp(8));
        dotParams.rightMargin = dp(8);
        bar.addView(dot, dotParams);

        this.reconnectText = OnlineUi.text(this,
                getString(R.string.online_opponent_reconnecting), 13.0f,
                R.color.online_text_primary, OnlineUi.medium(this));
        this.reconnectText.setGravity(Gravity.CENTER);
        bar.addView(this.reconnectText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return bar;
    }

    /** Symmetric player header shared by search, ready, play and result states. */
    private View buildVsHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER_HORIZONTAL);
        header.setBackgroundColor(OnlineUi.color(this, R.color.online_surface));
        header.setPadding(OnlineUi.dim(this, R.dimen.online_page_padding), dp(14),
                OnlineUi.dim(this, R.dimen.online_page_padding), dp(14));

        this.modeChip = OnlineUi.chip(this, getString(R.string.online_mode_ranked));
        LinearLayout.LayoutParams modeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        modeParams.gravity = Gravity.CENTER_HORIZONTAL;
        modeParams.bottomMargin = dp(12);
        header.addView(this.modeChip, modeParams);

        LinearLayout players = new LinearLayout(this);
        players.setOrientation(LinearLayout.HORIZONTAL);
        players.setGravity(Gravity.CENTER_VERTICAL);
        players.setBaselineAligned(false);
        header.addView(players, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout mySide = playerHeaderSide();
        this.myAvatar = OnlineUi.avatar(this, 76, true);
        mySide.addView(this.myAvatar.view, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        this.myNameView = headerName();
        mySide.addView(this.myNameView, playerNameParams());
        players.addView(mySide, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        FrameLayout middle = new FrameLayout(this);
        LinearLayout middleRow = new LinearLayout(this);
        middleRow.setOrientation(LinearLayout.HORIZONTAL);
        int[] weights = {30, 40, 60};
        int[] sizes = {12, 18, 24};
        for (int i = 0; i < this.pulseCircles.length; i++) {
            FrameLayout slot = new FrameLayout(this);
            View circle = new View(this);
            circle.setBackgroundResource(R.drawable.online_mode_anim_background);
            circle.setVisibility(View.INVISIBLE);
            slot.addView(circle, new FrameLayout.LayoutParams(
                    dp(sizes[i]), dp(sizes[i]), Gravity.CENTER));
            this.pulseCircles[i] = circle;
            middleRow.addView(slot, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, weights[i]));
        }
        middle.addView(middleRow, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        this.boltView = new ImageView(this);
        this.boltView.setImageResource(R.drawable.ic_versus);
        this.boltView.setVisibility(View.GONE);
        this.boltView.setContentDescription(getString(R.string.online_vs));
        middle.addView(this.boltView, new FrameLayout.LayoutParams(
                dp(70), dp(70), Gravity.CENTER));
        players.addView(middle, new LinearLayout.LayoutParams(
                dp(88), dp(96)));

        LinearLayout opponentSide = playerHeaderSide();
        this.oppAvatar = OnlineUi.avatar(this, 76, true);
        opponentSide.addView(this.oppAvatar.view, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        this.oppNameView = headerName();
        opponentSide.addView(this.oppNameView, playerNameParams());
        players.addView(opponentSide, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        View divider = OnlineUi.divider(this);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        dividerParams.topMargin = dp(14);
        header.addView(divider, dividerParams);
        return header;
    }

    private LinearLayout playerHeaderSide() {
        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setGravity(Gravity.CENTER_HORIZONTAL);
        return side;
    }

    private LinearLayout.LayoutParams playerNameParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(9);
        return params;
    }

    private TextView headerName() {
        TextView name = OnlineUi.text(this, "", 15.0f,
                R.color.online_text_primary, OnlineUi.medium(this));
        name.setGravity(Gravity.CENTER_HORIZONTAL);
        name.setSingleLine(true);
        name.setMaxLines(1);
        return name;
    }

    /** Search state with the legacy automation label retained verbatim. */
    private LinearLayout buildSearchBoard() {
        LinearLayout board = OnlineUi.emphasisCard(this);
        board.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView eyebrow = OnlineUi.text(this, getString(R.string.online_search_title), 13.0f,
                R.color.online_primary, OnlineUi.bold(this));
        eyebrow.setGravity(Gravity.CENTER);
        board.addView(eyebrow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        this.searchMessage = OnlineUi.text(this,
                getString(R.string.searching_for_opponents), 24.0f,
                R.color.online_text_primary, OnlineUi.medium(this));
        this.searchMessage.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        messageParams.topMargin = dp(10);
        board.addView(this.searchMessage, messageParams);

        LinearLayout pulseLine = new LinearLayout(this);
        pulseLine.setGravity(Gravity.CENTER);
        for (int i = 0; i < 3; i++) {
            View dot = new View(this);
            dot.setBackground(OnlineUi.roundedBackground(this, R.color.online_primary, 20));
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(7), dp(7));
            dotParams.leftMargin = dp(4);
            dotParams.rightMargin = dp(4);
            pulseLine.addView(dot, dotParams);
        }
        LinearLayout.LayoutParams pulseParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pulseParams.topMargin = dp(14);
        board.addView(pulseLine, pulseParams);

        this.searchHint = OnlineUi.text(this,
                getString(R.string.online_search_hint), 15.0f,
                R.color.online_text_secondary, OnlineUi.regular(this));
        this.searchHint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = dp(14);
        board.addView(this.searchHint, hintParams);

        TextView stakeHint = OnlineUi.text(this,
                android.text.Html.fromHtml(getString(R.string.online_tutorial_1)), 13.0f,
                R.color.online_text_secondary, OnlineUi.regular(this));
        stakeHint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams stakeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stakeParams.topMargin = dp(8);
        board.addView(stakeHint, stakeParams);
        return board;
    }

    /**
     * fragment_online_stats.xml grammar: the pre-match comparison board -
     * STATISTICS header, wins/losses/draws/ratio rows in three columns and
     * the two BETS coins carrying the live entry fee.
     */
    private LinearLayout buildStatsBoard() {
        LinearLayout board = OnlineUi.card(this);
        board.setGravity(Gravity.CENTER_HORIZONTAL);

        java.util.List<View> fade = new java.util.ArrayList<View>();
        TextView readyTitle = OnlineUi.text(this,
                getString(R.string.online_match_ready_title), 24.0f,
                R.color.online_text_primary, OnlineUi.medium(this));
        readyTitle.setGravity(Gravity.CENTER);
        board.addView(readyTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        fade.add(readyTitle);

        TextView readySubtitle = OnlineUi.body(this, R.string.online_match_ready_subtitle);
        readySubtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(6);
        board.addView(readySubtitle, subtitleParams);
        fade.add(readySubtitle);

        View upperDivider = OnlineUi.divider(this);
        LinearLayout.LayoutParams upperDividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        upperDividerParams.topMargin = dp(16);
        upperDividerParams.bottomMargin = dp(12);
        board.addView(upperDivider, upperDividerParams);

        TextView statsTitle = OnlineUi.sectionTitle(this, R.string.stats);
        statsTitle.setGravity(Gravity.CENTER);
        board.addView(statsTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        fade.add(statsTitle);

        AuthOk profile = OnlineSession.get().getLastProfile();
        OpponentInfo opponent = OnlineSession.get().getOpponent();
        int myWins = profile != null ? profile.wins : 0;
        int myLosses = profile != null ? profile.losses : 0;
        int myDraws = profile != null ? profile.draws : 0;
        int oppWins = opponent != null ? opponent.wins : 0;
        int oppLosses = opponent != null ? opponent.losses : 0;
        int oppDraws = opponent != null ? opponent.draws : 0;
        fade.add(addStatsRow(board, myWins, R.string.online_title_wins, oppWins));
        fade.add(addStatsRow(board, myLosses, R.string.online_title_loses, oppLosses));
        fade.add(addStatsRow(board, myDraws, R.string.online_title_draws, oppDraws));
        fade.add(addStatsRow(board, ratio(myWins, myWins + myLosses + myDraws),
                R.string.online_title_ratio, ratio(oppWins, oppWins + oppLosses + oppDraws)));

        View lowerDivider = OnlineUi.divider(this);
        LinearLayout.LayoutParams lowerDividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lowerDividerParams.topMargin = dp(14);
        lowerDividerParams.bottomMargin = dp(14);
        board.addView(lowerDivider, lowerDividerParams);

        TextView betsTitle = OnlineUi.text(this,
                getString(R.string.online_stake_title), 14.0f,
                R.color.online_text_secondary, OnlineUi.medium(this));
        betsTitle.setGravity(Gravity.CENTER);
        board.addView(betsTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        fade.add(betsTitle);

        LinearLayout betsRow = new LinearLayout(this);
        betsRow.setGravity(Gravity.CENTER);
        int fee = OnlineSession.get().isPracticeMode() ? 0 : OnlineSession.get().getServerEntryFee();
        if (fee == 0) {
            TextView free = OnlineUi.chip(this, getString(R.string.online_free_match));
            betsRow.addView(free);
        } else {
            betsRow.addView(coinBadge(fee));
            TextView versus = OnlineUi.text(this, getString(R.string.online_vs), 12.0f,
                    R.color.online_text_secondary, OnlineUi.bold(this));
            versus.setGravity(Gravity.CENTER);
            betsRow.addView(versus, new LinearLayout.LayoutParams(dp(44), dp(38)));
            betsRow.addView(coinBadge(fee));
        }
        LinearLayout.LayoutParams betsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        betsParams.topMargin = dp(8);
        board.addView(betsRow, betsParams);
        fade.add(betsRow);
        this.statsFadeViews = fade;
        return board;
    }

    /** One wins/losses/draws/ratio line: value / title / value columns. */
    private View addStatsRow(LinearLayout parent, int mine, int titleRes, int theirs) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, dp(7));
        row.addView(statsCell(String.valueOf(mine), true), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.28f));
        TextView title = statsCell(getString(titleRes), false);
        row.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.44f));
        row.addView(statsCell(String.valueOf(theirs), true), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.28f));
        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private TextView statsCell(String text, boolean value) {
        TextView cell = OnlineUi.text(this, text, value ? 20.0f : 14.0f,
                value ? R.color.online_accent : R.color.online_text_secondary,
                value ? OnlineUi.medium(this) : OnlineUi.regular(this));
        cell.setGravity(Gravity.CENTER);
        return cell;
    }

    private int ratio(int wins, int total) {
        return total == 0 ? 0 : Math.round(wins * 100.0f / total);
    }

    /** Entry-fee coin badge used on both sides of the matchup. */
    private TextView coinBadge(int fee) {
        TextView badge = OnlineUi.text(this, String.valueOf(fee), 13.0f,
                R.color.online_text_primary, OnlineUi.bold(this));
        badge.setBackgroundResource(R.drawable.ic_coin);
        badge.setGravity(Gravity.CENTER);
        badge.setContentDescription(String.format(Locale.US,
                getString(R.string.online_entry_fee), fee));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(38), dp(38));
        badge.setLayoutParams(params);
        return badge;
    }

    /** Round line, live score and the 3-2-1 launch countdown. */
    private LinearLayout buildMatchBoard() {
        LinearLayout board = OnlineUi.emphasisCard(this);
        board.setGravity(Gravity.CENTER_HORIZONTAL);

        this.statusView = OnlineUi.text(this, "", 48.0f,
                R.color.online_primary, OnlineUi.bold(this));
        this.statusView.setGravity(Gravity.CENTER);
        board.addView(this.statusView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        this.detailView = OnlineUi.text(this, "", 16.0f,
                R.color.online_text_secondary, OnlineUi.medium(this));
        this.detailView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = dp(4);
        board.addView(this.detailView, detailParams);

        this.scoreView = OnlineUi.text(this, "", 32.0f,
                R.color.online_text_primary, OnlineUi.bold(this));
        this.scoreView.setGravity(Gravity.CENTER);
        this.scoreView.setBackgroundResource(R.drawable.online_score_pill);
        this.scoreView.setPadding(dp(22), dp(8), dp(22), dp(8));
        LinearLayout.LayoutParams scoreParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scoreParams.gravity = Gravity.CENTER_HORIZONTAL;
        scoreParams.topMargin = dp(18);
        board.addView(this.scoreView, scoreParams);
        return board;
    }

    /** Accept-gate waiting state with the original visible labels intact. */
    private LinearLayout buildWaitingCard() {
        LinearLayout card = OnlineUi.emphasisCard(this);
        card.setGravity(Gravity.CENTER_HORIZONTAL);

        this.waitingTitle = OnlineUi.text(this,
                getString(R.string.opponent_waiting), 24.0f,
                R.color.online_text_primary, OnlineUi.medium(this));
        this.waitingTitle.setGravity(Gravity.CENTER);
        card.addView(this.waitingTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        this.waitingLine = OnlineUi.body(this, R.string.online_waiting_subtitle);
        this.waitingLine.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lineParams.topMargin = dp(8);
        card.addView(this.waitingLine, lineParams);

        this.timerView = new TimerView(this);
        LinearLayout.LayoutParams timerParams = new LinearLayout.LayoutParams(dp(112), dp(112));
        timerParams.topMargin = dp(24);
        timerParams.bottomMargin = dp(8);
        timerParams.gravity = Gravity.CENTER_HORIZONTAL;
        card.addView(this.timerView, timerParams);
        return card;
    }

    /** Final settlement card with prominent result and reward hierarchy. */
    private LinearLayout buildSettlementCard() {
        LinearLayout card = OnlineUi.card(this);
        card.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView complete = OnlineUi.text(this,
                getString(R.string.online_match_complete), 12.0f,
                R.color.online_primary, OnlineUi.bold(this));
        complete.setGravity(Gravity.CENTER);
        card.addView(complete, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        this.titleView = OnlineUi.text(this, "", 28.0f,
                R.color.online_text_primary, OnlineUi.bold(this));
        this.titleView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dp(5);
        card.addView(this.titleView, titleParams);

        this.resultSubtitle = OnlineUi.text(this, "", 15.0f,
                R.color.online_text_secondary, OnlineUi.regular(this));
        this.resultSubtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams resultSubtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        resultSubtitleParams.topMargin = dp(6);
        card.addView(this.resultSubtitle, resultSubtitleParams);

        this.doodleView = new ImageView(this);
        this.doodleView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams doodleParams = new LinearLayout.LayoutParams(
                OnlineUi.dim(this, R.dimen.online_hero_art_size),
                OnlineUi.dim(this, R.dimen.online_hero_art_size));
        doodleParams.topMargin = dp(12);
        doodleParams.gravity = Gravity.CENTER_HORIZONTAL;
        card.addView(this.doodleView, doodleParams);

        LinearLayout rewards = new LinearLayout(this);
        rewards.setOrientation(LinearLayout.HORIZONTAL);
        rewards.setBaselineAligned(false);
        LinearLayout.LayoutParams rewardsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rewardsParams.topMargin = dp(14);
        card.addView(rewards, rewardsParams);

        LinearLayout coinReward = rewardColumn(R.string.online_reward_coins);
        this.coinRow = new LinearLayout(this);
        this.coinRow.setGravity(Gravity.CENTER);
        this.coinDelta = styledRowText();
        this.coinRow.addView(this.coinDelta);
        ImageView coin = new ImageView(this);
        coin.setImageResource(R.drawable.ic_coin);
        LinearLayout.LayoutParams coinParams = new LinearLayout.LayoutParams(dp(24), dp(24));
        coinParams.leftMargin = dp(8);
        coinParams.rightMargin = dp(8);
        this.coinRow.addView(coin, coinParams);
        this.coinTotal = styledRowText();
        this.coinRow.addView(this.coinTotal);
        coinReward.addView(this.coinRow, rewardValueParams());
        rewards.addView(coinReward, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        LinearLayout ratingReward = rewardColumn(R.string.online_reward_rating);
        this.ratingRow = new LinearLayout(this);
        this.ratingRow.setGravity(Gravity.CENTER);
        this.ratingDelta = styledRowText();
        this.ratingRow.addView(this.ratingDelta);
        ImageView crown = new ImageView(this);
        crown.setBackgroundResource(R.drawable.drawable_circle_online_rating);
        crown.setImageResource(R.drawable.ic_crown_new);
        crown.setColorFilter(Color.WHITE);
        int pad4 = dp(4);
        crown.setPadding(pad4, pad4, pad4, pad4);
        LinearLayout.LayoutParams crownParams = new LinearLayout.LayoutParams(dp(26), dp(26));
        crownParams.leftMargin = dp(8);
        crownParams.rightMargin = dp(8);
        this.ratingRow.addView(crown, crownParams);
        this.ratingTotal = styledRowText();
        this.ratingRow.addView(this.ratingTotal);
        ratingReward.addView(this.ratingRow, rewardValueParams());
        rewards.addView(ratingReward, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        this.okButton = OnlineUi.secondaryButton(this, R.string.ok);
        LinearLayout.LayoutParams okParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                OnlineUi.dim(this, R.dimen.online_button_height));
        okParams.topMargin = dp(22);
        card.addView(this.okButton, okParams);
        return card;
    }

    private LinearLayout rewardColumn(int titleRes) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setBackgroundColor(OnlineUi.color(this, R.color.online_surface_alt));
        column.setPadding(dp(6), dp(10), dp(6), dp(10));
        TextView title = OnlineUi.text(this, getString(titleRes), 12.0f,
                R.color.online_text_secondary, OnlineUi.medium(this));
        title.setGravity(Gravity.CENTER);
        column.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return column;
    }

    private LinearLayout.LayoutParams rewardValueParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.topMargin = dp(7);
        return params;
    }

    /**
     * item_list_online_game.xml grammar: the post-match results list - one
     * row per round with the game's name and icon and both scores, the
     * online_game_win_circle riding the round winner, then the bold totals.
     */
    private LinearLayout buildResultsBoard(MatchEnd end) {
        LinearLayout board = OnlineUi.card(this);
        board.setOrientation(LinearLayout.VERTICAL);

        TextView title = OnlineUi.sectionTitle(this, R.string.online_round_results);
        title.setGravity(Gravity.CENTER);
        board.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        OnlineMessages.RoundDesc[] plan = OnlineSession.get().getRounds();
        OnlineMessages.RoundScore[] scores = end.rounds;
        int rounds = scores != null ? scores.length : 0;
        int myTotal = 0;
        int oppTotal = 0;
        for (int i = 0; i < rounds; i++) {
            int mine = scores[i] != null ? scores[i].me : 0;
            int theirs = scores[i] != null ? scores[i].opp : 0;
            myTotal += mine;
            oppTotal += theirs;
            if (plan != null && i < plan.length) {
                GameInfo info = Games.get().getGameBiId(plan[i].gameId);
                if (info != null) {
                    board.addView(resultsRow(getString(info.getGameNameRes()),
                            info.getGameImageRes(), mine, theirs));
                }
            }
        }
        board.addView(resultsRow(null, 0, myTotal, oppTotal));
        return board;
    }

    /** One compact results row; a null game name marks the totals row. */
    private View resultsRow(String name, int imageRes, int mine, int theirs) {
        boolean totals = name == null;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, totals ? dp(14) : dp(10), 0, totals ? dp(2) : dp(10));
        if (totals) {
            row.setBackgroundColor(OnlineUi.color(this, R.color.online_primary_soft));
        }

        row.addView(resultsScore(String.valueOf(mine), totals, mine > theirs),
                new LinearLayout.LayoutParams(dp(54), dp(44)));

        LinearLayout game = new LinearLayout(this);
        game.setOrientation(LinearLayout.HORIZONTAL);
        game.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
        if (!totals) {
            ImageView icon = new ImageView(this);
            icon.setImageResource(imageRes);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(48), dp(48));
            iconParams.rightMargin = dp(10);
            game.addView(icon, iconParams);
            TextView nameView = OnlineUi.text(this, name, 15.0f,
                    R.color.online_text_primary, OnlineUi.medium(this));
            nameView.setGravity(Gravity.CENTER_VERTICAL);
            game.addView(nameView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            TextView total = OnlineUi.text(this, getString(R.string.online_total), 16.0f,
                    R.color.online_text_primary, OnlineUi.bold(this));
            total.setGravity(Gravity.CENTER);
            game.addView(total, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        row.addView(game, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        row.addView(resultsScore(String.valueOf(theirs), totals, theirs > mine),
                new LinearLayout.LayoutParams(dp(54), dp(44)));
        return row;
    }

    /** A score cell; round and match winners carry the winner ring. */
    private TextView resultsScore(String value, boolean bold, boolean winner) {
        TextView score = OnlineUi.text(this, value, bold ? 20.0f : 17.0f,
                winner ? R.color.online_success : R.color.online_accent,
                bold ? OnlineUi.bold(this) : OnlineUi.medium(this));
        score.setGravity(Gravity.CENTER);
        if (winner) {
            score.setBackgroundResource(R.drawable.online_game_win_circle);
        }
        int pad = dp(4);
        score.setPadding(pad, pad, pad, pad);
        return score;
    }

    /** Round completion feedback stays visible until the server starts the next round. */
    private void showIntermissionUi() {
        if (this.intermissionCard == null) this.intermissionCard = new RoundWaitCard(this);
        showScrollableBoard(this.intermissionCard.view, ViewGroup.LayoutParams.MATCH_PARENT);
        this.bottomBar.setVisibility(View.GONE);
        this.intermissionVisible = true;
        updateIntermissionStatus();
        this.handler.removeCallbacks(this.intermissionTick);
        if (this.activityStarted) {
            this.handler.postDelayed(this.intermissionTick, 250L);
        }
    }

    private void updateIntermissionStatus() {
        OnlineSession session = OnlineSession.get();
        RoundEnd end = session.getLastRoundEnd();
        OnlineMessages.RoundDesc[] rounds = session.getRounds();
        int round = end != null ? end.round : session.getCurrentRound();
        String nextGameId = end != null ? end.nextGameId : null;
        if (nextGameId == null && rounds != null && round > 0 && round < rounds.length) {
            nextGameId = rounds[round].gameId;
        }
        int total = rounds == null ? 0 : rounds.length;
        RoundWaitState state = RoundWaitState.duel(end, total,
                session.hasIntermissionCountdown(), session.intermissionRemainingMs());
        GameInfo nextGame = nextGameId == null || state.phase == RoundWaitState.Phase.SETTLING
                ? null : Games.get().getGameBiId(nextGameId);
        String summary = getString(R.string.round_wait_total) + " · "
                + getString(R.string.round_wait_you) + " " + session.getMyScore() + " : "
                + session.getOpponentScore() + " " + getString(R.string.round_wait_opponent);
        this.intermissionCard.bind(round, total, nextGame, summary);
        this.intermissionCard.showState(state);
    }

    /**
     * fragment_start_game.xml grammar: the pre-match game list - one row per
     * round (name + icon) under a hairline divider, with the 100dip
     * countdown disc origin showed before the first round launched.
     */
    private void showGameListUi(final RoundStart start, final GameInfo info) {
        LinearLayout board = OnlineUi.card(this);
        board.setOrientation(LinearLayout.VERTICAL);

        TextView title = OnlineUi.sectionTitle(this, R.string.generating_game_list);
        title.setGravity(Gravity.CENTER);
        board.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View divider = OnlineUi.divider(this);
        int pad = dp(10);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        dividerParams.bottomMargin = pad;
        board.addView(divider, dividerParams);

        OnlineMessages.RoundDesc[] rounds = OnlineSession.get().getRounds();
        if (rounds != null) {
            for (OnlineMessages.RoundDesc round : rounds) {
                GameInfo roundInfo = Games.get().getGameBiId(round.gameId);
                if (roundInfo == null) {
                    continue;
                }
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(4), dp(7), dp(4), dp(7));
                ImageView icon = new ImageView(this);
                icon.setImageResource(roundInfo.getGameImageRes());
                icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(54), dp(54));
                iconParams.rightMargin = dp(14);
                row.addView(icon, iconParams);
                TextView name = OnlineUi.text(this,
                        getString(roundInfo.getGameNameRes()), 16.0f,
                        R.color.online_text_primary, OnlineUi.medium(this));
                name.setGravity(Gravity.CENTER_VERTICAL);
                row.addView(name, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
                board.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        }

        this.gameListTimer = new TimerView(this);
        LinearLayout.LayoutParams timerParams = new LinearLayout.LayoutParams(dp(100), dp(100));
        timerParams.gravity = Gravity.CENTER_HORIZONTAL;
        timerParams.topMargin = dp(10);
        timerParams.bottomMargin = dp(10);
        board.addView(this.gameListTimer, timerParams);
        this.gameListBoard = board;

        showScrollableBoard(board, ViewGroup.LayoutParams.MATCH_PARENT);
        this.gameListTimer.showTimer((int) GAME_LIST_TIMEOUT_MS, false);
        this.roundLaunchScheduled = true;
        this.handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (canLaunchRound(start)) {
                    completeRoundPreparation(start, info);
                }
            }
        }, GAME_LIST_TIMEOUT_MS);
    }

    /** Persistent bottom action area using the redesigned full-width controls. */
    private LinearLayout buildBottomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setGravity(Gravity.CENTER_HORIZONTAL);
        bar.setBackgroundColor(OnlineUi.color(this, R.color.online_surface));
        int horizontal = OnlineUi.dim(this, R.dimen.online_page_padding);
        int vertical = dp(12);
        bar.setPadding(horizontal, vertical, horizontal, vertical);

        this.bottomButton = OnlineUi.primaryButton(this, R.string.cancel);
        bar.addView(this.bottomButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                OnlineUi.dim(this, R.dimen.online_button_height)));
        return bar;
    }

    private TextView styledRowText() {
        TextView text = OnlineUi.text(this, "", 18.0f,
                R.color.online_text_primary, OnlineUi.medium(this));
        text.setGravity(Gravity.CENTER_VERTICAL);
        return text;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    // --------------------------------------------------------------- boards

    private void showBoard(View board, int width, int gravity) {
        this.content.removeAllViews();
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width,
                ViewGroup.LayoutParams.WRAP_CONTENT, gravity);
        int margin = OnlineUi.dim(this, R.dimen.online_page_padding);
        params.leftMargin = margin;
        params.rightMargin = margin;
        params.topMargin = margin;
        params.bottomMargin = margin;
        this.content.addView(board, params);
    }

    /**
     * Same as {@link #showBoard} but the board lives in a fill-viewport
     * ScrollView: on tall/dense screens (fontScale > 1) the settlement card
     * and the results list can outgrow the frame and their bottom action
     * would be clipped out of the view tree — scrolling keeps it reachable.
     */
    private void showScrollableBoard(View board, int width) {
        this.intermissionVisible = false;
        this.handler.removeCallbacks(this.intermissionTick);
        this.content.removeAllViews();
        // Cached boards (e.g. the intermission card) keep their old wrapper
        // as parent after removeAllViews; re-adding them without detaching
        // first throws "The specified child already has a parent" (crashed
        // on the round-2 hand-off in the 2026-10-13 cloud regression).
        if (board.getParent() instanceof ViewGroup) {
            ((ViewGroup) board.getParent()).removeView(board);
        }
        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        FrameLayout inner = new FrameLayout(this);
        int margin = OnlineUi.dim(this, R.dimen.online_page_padding);
        int availableWidth = getResources().getDisplayMetrics().widthPixels - (margin * 2);
        int boardWidth = width == ViewGroup.LayoutParams.MATCH_PARENT
                ? ViewGroup.LayoutParams.MATCH_PARENT : Math.min(width, availableWidth);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(boardWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        params.leftMargin = margin;
        params.rightMargin = margin;
        params.topMargin = margin;
        params.bottomMargin = margin;
        inner.addView(board, params);
        scroller.addView(inner, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        this.content.addView(scroller, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void showSearchUi() {
        this.acceptWaiting = false;
        hideReconnectStatus();
        fillMyHeaderSide();
        resetOpponentSide();
        updateModeChip();
        startPulse();
        showScrollableBoard(this.searchBoard, ViewGroup.LayoutParams.MATCH_PARENT);
        this.bottomBar.setVisibility(View.VISIBLE);
        this.bottomButton.setText(R.string.cancel);
        this.bottomButton.setTextColor(OnlineUi.color(this, R.color.online_primary));
        this.bottomButton.setBackgroundResource(R.drawable.online_secondary_button);
        this.bottomButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finishReason="search_cancel";
                OnlineSession.get().cancelMatchmaking();
                finish();
            }
        });
    }

    private void showMatchUi() {
        this.acceptWaiting = false;
        fillMyHeaderSide();
        fillOpponentSide(OnlineSession.get().getOpponent());
        updateModeChip();
        stopPulse();
        // The comparison is built against the freshly found opponent, so it
        // is recreated on every match_found.
        if (this.statsBoard != null) {
            this.content.removeView(this.statsBoard);
        }
        this.statsBoard = buildStatsBoard();
        showScrollableBoard(this.statsBoard, ViewGroup.LayoutParams.MATCH_PARENT);
        // Origin faded the stats rows in one-by-one (100ms per row).
        if (this.statsFadeViews != null && !this.statsFadeViews.isEmpty()) {
            for (View view : this.statsFadeViews) {
                view.setAlpha(0.0f);
            }
            com.github.florent37.viewanimator.AnimationBuilder chain =
                    ViewAnimator.animate(this.statsFadeViews.get(0)).fadeIn().duration(100);
            for (int i = 1; i < this.statsFadeViews.size(); i++) {
                chain = chain.thenAnimate(this.statsFadeViews.get(i)).fadeIn().duration(100);
            }
            chain.start();
        }
        this.bottomBar.setVisibility(View.VISIBLE);
        this.bottomButton.setText(R.string.start);
        this.bottomButton.setTextColor(Color.WHITE);
        this.bottomButton.setBackgroundResource(R.drawable.online_primary_button);
        this.bottomButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OnlineSession.get().sendMatchStart();
                showAcceptWaitingUi();
            }
        });
    }

    /** Waiting for the opponent's START press (origin's accepted timer). */
    private void showAcceptWaitingUi() {
        this.acceptWaiting = true;
        this.waitingTitle.setText(R.string.opponent_waiting);
        this.waitingLine.setText(R.string.online_waiting_subtitle);
        this.waitingLine.setVisibility(View.VISIBLE);
        showScrollableBoard(this.waitingCard, ViewGroup.LayoutParams.MATCH_PARENT);
        this.timerView.setVisibility(View.VISIBLE);
        // Mirrors the server's accept window (acceptTimeoutMs).
        this.timerView.showTimer(ACCEPT_WINDOW_MS, true);
        this.bottomBar.setVisibility(View.GONE);
    }

    private void showSettlementUi(final MatchEnd end) {
        this.acceptWaiting = false;
        hideReconnectStatus();
        refreshCoins();
        boolean win = OnlineSession.RESULT_WIN.equals(end.result);
        boolean draw = OnlineSession.RESULT_DRAW.equals(end.result);
        this.titleView.setText(draw ? R.string.online_draw : (win ? R.string.online_win : R.string.online_lose));
        this.resultSubtitle.setText(draw ? R.string.online_result_draw_subtitle
                : (win ? R.string.online_result_win_subtitle : R.string.online_result_loss_subtitle));
        this.doodleView.setImageResource(win ? R.drawable.doodle_33
                : draw ? R.drawable.doodle_26 : R.drawable.doodle_31);
        this.coinDelta.setText(String.format(Locale.US, "%+d", end.coinDelta));
        this.coinTotal.setText(String.valueOf(end.balance));
        this.ratingDelta.setText(String.format(Locale.US, "%+d", end.eloDelta));
        this.ratingTotal.setText(String.valueOf(end.elo));
        this.myAvatar.rating.setText(String.valueOf(end.elo));

        showScrollableBoard(this.card,
                OnlineUi.dim(this, R.dimen.online_result_card_width));
        // OK drops the result card onto the per-round results list, exactly
        // like the original dialog revealed the results screen underneath.
        this.okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showScrollableBoard(buildResultsBoard(end),
                        ViewGroup.LayoutParams.MATCH_PARENT);
            }
        });
        this.bottomBar.setVisibility(View.VISIBLE);
        this.bottomButton.setText(R.string.nextDuel);
        this.bottomButton.setTextColor(Color.WHITE);
        this.bottomButton.setBackgroundResource(R.drawable.online_primary_button);
        this.bottomButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OnlineSession.get().startMatchmaking();
                showSearchUi();
            }
        });
    }

    // ---------------------------------------------------------- VS header

    private void updateModeChip() {
        if (OnlineSession.get().isPracticeMode()) {
            this.matchModeLabel = R.string.online_mode_practice;
        } else if (OnlineSession.get().isFriendMode()) {
            this.matchModeLabel = R.string.online_mode_friend;
        } else if (!OnlineSession.get().isInMatch()) {
            this.matchModeLabel = R.string.online_mode_ranked;
        }
        if (this.modeChip != null) {
            this.modeChip.setText(this.matchModeLabel);
        }
    }

    private void showReconnectStatus() {
        if (this.reconnectBar != null) {
            this.reconnectText.setText(R.string.online_opponent_reconnecting);
            this.reconnectBar.setVisibility(View.VISIBLE);
        }
    }

    private void hideReconnectStatus() {
        if (this.reconnectBar != null) {
            this.reconnectBar.setVisibility(View.GONE);
        }
    }

    private void fillMyHeaderSide() {
        AuthOk profile = OnlineSession.get().getLastProfile();
        this.myNameView.setText(profile != null ? profile.nickname
                : OnlineIdentity.getNickname(this));
        this.myAvatar.image.setImageResource(R.drawable.ic_user_placeholder);
        OnlineUi.applyAvatar(this,this.myAvatar,profile != null ? profile.avatarValue : null);
        this.myAvatar.badge.setVisibility(View.VISIBLE);
        this.myAvatar.rating.setText(String.valueOf(profile != null ? profile.elo : 0));
        refreshCoins();
    }

    private void fillOpponentSide(OpponentInfo opponent) {
        String name = opponent != null ? opponent.nickname : "";
        int elo = opponent != null ? opponent.elo : 0;
        this.oppNameView.setText(name);
        this.oppAvatar.image.setImageResource(R.drawable.ic_user_placeholder);
        this.oppAvatar.rating.setText(String.valueOf(elo));
        this.oppAvatar.badge.setVisibility(opponent != null ? View.VISIBLE : View.GONE);
        this.boltView.setAlpha(0.0f);
        this.boltView.setVisibility(View.VISIBLE);
        this.boltView.animate().alpha(1.0f).setDuration(500L).start();
    }

    private void resetOpponentSide() {
        this.oppNameView.setText("");
        this.oppAvatar.image.setImageDrawable(null);
        this.oppAvatar.badge.setVisibility(View.GONE);
        this.boltView.setVisibility(View.GONE);
    }

    private void startPulse() {
        this.pulsePosition = 0;
        this.handler.removeCallbacks(this.pulseRunnable);
        this.handler.postDelayed(this.pulseRunnable, PULSE_INTERVAL_MS);
    }

    private void stopPulse() {
        this.handler.removeCallbacks(this.pulseRunnable);
        for (View circle : this.pulseCircles) {
            circle.animate().cancel();
            circle.setVisibility(View.INVISIBLE);
            circle.setScaleX(1.0f);
            circle.setScaleY(1.0f);
        }
    }

    private void refreshCoins() {
        this.coinsView.setText(String.valueOf(OnlineSession.get().getOnlineCoins()));
    }

    // ---------------------------------------------------------- round launch

    private void launchRound(final RoundStart start) {
        this.intermissionVisible = false;
        this.handler.removeCallbacks(this.intermissionTick);
        this.acceptWaiting = false;
        GameInfo info = Games.get().getGameBiId(start.gameId);
        if (info == null) {
            diagnostic("unknown_game","gameId",start.gameId,"reason","unknown_game");
            finishReason="unknown_game";
            Toast.makeText(this, R.string.online_error_connection, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        this.launchedRound = start.round;
        int totalRounds = OnlineSession.get().getRounds() != null ? OnlineSession.get().getRounds().length : 0;
        this.detailView.setText(String.format(Locale.US, "%s %d/%d — %s",
                getString(R.string.online_round_word), start.round, totalRounds,
                getString(info.getGameNameRes())));
        this.scoreView.setText(String.format(Locale.US, "%d : %d",
                OnlineSession.get().getMyScore(), OnlineSession.get().getOpponentScore()));
        this.bottomBar.setVisibility(View.GONE);
        if (needsRoundTutorial(info)) {
            // Origin ran the game's how-to slideshow before its first online
            // appearances. Restricted to bot and friend duels here: ranked
            // rounds are timed from the server's round_start, so a slideshow
            // would eat the player's own clock against a human opponent.
            this.pendingTutorialStart = start;
            this.pendingTutorialInfo = info;
            this.roundLaunchScheduled = true;
            Intent tutorial = new Intent(this,
                    com.cube.memorygames.activity.OnlineGameTutorialActivity.class);
            tutorial.putExtra(MainMenuActivity.EXTRA_GAME_INFO, info);
            diagnostic("tutorial_launch","gameId",start.gameId,"round",start.round);
            this.pendingTutorialRequestCode = REQUEST_TUTORIAL + start.round;
            startActivityForResult(tutorial, this.pendingTutorialRequestCode);
            return;
        }
        showGameListOrCountdown(start, info);
    }

    private void showGameListOrCountdown(final RoundStart start, final GameInfo info) {
        if (start.round == 1) {
            // Origin opened the match with the full game list and its
            // countdown disc (START_TIMEOUT); later rounds use the 3-2-1.
            showGameListUi(start, info);
            return;
        }
        this.statusView.setText("3");
        showScrollableBoard(this.matchBoard, ViewGroup.LayoutParams.MATCH_PARENT);
        this.roundLaunchScheduled = true;
        this.handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!canLaunchRound(start)) {
                    return;
                }
                statusView.setText("2");
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!canLaunchRound(start)) {
                            return;
                        }
                        statusView.setText("1");
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                completeRoundPreparation(start, info);
                            }
                        }, COUNTDOWN_MS / 3);
                    }
                }, COUNTDOWN_MS / 3);
            }
        }, COUNTDOWN_MS / 3);
    }

    /**
     * The origin pre-round tutorial gate: first {@link #TUTORIAL_COUNT}
     * online appearances per game, and only when no human opponent's clock
     * runs against us (bot fallback and friend rooms).
     */
    private boolean needsRoundTutorial(GameInfo info) {
        boolean botOrFriend = OnlineSession.get().isFriendMode()
                || (OnlineSession.get().getOpponent() != null
                        && OnlineSession.get().getOpponent().isBot);
        if (!botOrFriend) {
            return false;
        }
        return android.preference.PreferenceManager.getDefaultSharedPreferences(this)
                .getInt(PREF_TUTORIAL_SHOW + info.getId(), 0) < TUTORIAL_COUNT;
    }

    private boolean canLaunchRound(RoundStart start) {
        OnlineSession session = OnlineSession.get();
        return !destroyed && !isFinishing() && this.roundLaunchScheduled && start != null
                && launchGate.canComplete(start) && this.launchedRound == start.round
                && RoundIntermission.canLaunch(start, session.getMatchId(),
                        session.getCurrentRound(), session.getLastRoundEnd());
    }

    private void completeRoundPreparation(RoundStart start, GameInfo info) {
        if (!canLaunchRound(start)) return;
        RoundLaunchGate.Action action = launchGate.complete(start);
        this.roundLaunchScheduled = false;
        if (action == RoundLaunchGate.Action.READY) {
            OnlineSession.get().sendRoundReady(start);
            this.statusView.setText(R.string.online_round_waiting_start);
            showScrollableBoard(this.matchBoard, ViewGroup.LayoutParams.MATCH_PARENT);
        } else if (action == RoundLaunchGate.Action.START) {
            startGameActivity(launchGate.formalFrame(), info);
        }
    }

    private void startGameActivity(RoundStart start, GameInfo info) {
        OnlineSession session = OnlineSession.get();
        if (destroyed || isFinishing() || !RoundIntermission.canLaunch(start,
                session.getMatchId(), session.getCurrentRound(), session.getLastRoundEnd())
                || !launchGate.markLaunched(start)) return;
        this.roundLaunchScheduled = false;
        Intent intent = new Intent(this, Games.get().getGameActivity(info));
        intent.putExtra(MainMenuActivity.EXTRA_GAME_INFO, info);
        intent.putExtra(OnlineSession.EXTRA_ONLINE_GAME, true);
        intent.putExtra(OnlineSession.EXTRA_ONLINE_SEED, roundSeed(start.round));
        intent.putExtra(OnlineSession.EXTRA_ONLINE_ROUND, start.round);
        diagnostic("game_launch","gameId",start.gameId,"round",start.round);
        startActivityForResult(intent, REQUEST_ROUND);
        overridePendingTransition(R.anim.slide_to_left, R.anim.no_change);
    }

    private long roundSeed(int round) {
        OnlineMessages.RoundDesc[] rounds = OnlineSession.get().getRounds();
        if (rounds != null && round >= 1 && round <= rounds.length) {
            return rounds[round - 1].seed;
        }
        return System.currentTimeMillis();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        diagnostic("game_return","attempt",requestCode,"code",resultCode);
        if (requestCode == this.pendingTutorialRequestCode) {
            this.pendingTutorialRequestCode = -1;
            if (this.pendingTutorialStart != null && this.pendingTutorialInfo != null
                    && canLaunchRound(this.pendingTutorialStart)) {
                android.content.SharedPreferences prefs =
                        android.preference.PreferenceManager.getDefaultSharedPreferences(this);
                prefs.edit().putInt(PREF_TUTORIAL_SHOW + this.pendingTutorialInfo.getId(),
                        prefs.getInt(PREF_TUTORIAL_SHOW + this.pendingTutorialInfo.getId(), 0) + 1)
                        .apply();
                RoundStart start = this.pendingTutorialStart;
                GameInfo info = this.pendingTutorialInfo;
                this.pendingTutorialStart = null;
                this.pendingTutorialInfo = null;
                completeRoundPreparation(start, info);
            }
            return;
        }
        if (requestCode == REQUEST_ROUND) {
            // Skip the waiting board when the match already settled while the
            // round activity was in front — showSettlementUi ran earlier and
            // must not be overwritten (real-device race, 2026-10-03).
            if (OnlineSession.get().getMatchId() == null || this.roundLaunchScheduled) {
                return;
            }
            showIntermissionUi();
        }
    }

    // -------------------------------------------------- OnlineSession events

    @Override
    public void onStateChanged(int state) {
    }

    @Override
    public void onAuthOk(AuthOk profile) {
    }

    @Override
    public void onMatchFound(MatchFound match) {
        launchGate.clear();
        roundLaunchScheduled = false;
        launchedRound = -1;
        diagnosticMatchId=match.matchId;
        showMatchUi();
    }

    @Override
    public void onMatchCancelled(MatchAcceptance.Action action, String mode) {
        diagnostic("match_cancelled","mode",mode,"reason","match_cancelled");
        if (action == MatchAcceptance.Action.IGNORE || destroyed || isFinishing()) {
            return;
        }
        launchGate.clear();
        roundLaunchScheduled = false;
        launchedRound = -1;
        int tutorialRequest = pendingTutorialRequestCode;
        pendingTutorialRequestCode = -1;
        pendingTutorialStart = null;
        pendingTutorialInfo = null;
        if (tutorialRequest >= 0) finishActivity(tutorialRequest);
        Toast.makeText(this, R.string.toast_game_not_accepted, Toast.LENGTH_SHORT).show();
        if (action == MatchAcceptance.Action.REQUEUE) {
            showSearchUi();
            OnlineSession.get().startMatchmaking();
        } else {
            finishReason="match_cancelled";
            finish();
        }
    }

    @Override
    public void onRoundStart(final RoundStart round) {
        OnlineSession session = OnlineSession.get();
        if (destroyed || isFinishing() || !RoundIntermission.canLaunch(round,
                session.getMatchId(), session.getCurrentRound(), session.getLastRoundEnd())) return;
        RoundLaunchGate.Action action = launchGate.accept(round, session.isPracticeMode());
        if (action == RoundLaunchGate.Action.PREPARE) {
            launchRound(round);
        } else if (action == RoundLaunchGate.Action.READY) {
            session.sendRoundReady(round);
        } else if (action == RoundLaunchGate.Action.START) {
            // Same round's formal frame bypasses tutorial/list/countdown, exactly once.
            startGameActivity(launchGate.formalFrame(), Games.get().getGameBiId(round.gameId));
        }
    }

    @Override
    public void onOpponentScore(OpponentScore score) {
        this.scoreView.setText(String.format(Locale.US, "%d : %d",
                OnlineSession.get().getMyScore(), score.score));
    }

    @Override
    public void onRoundEnd(RoundEnd round) {
        this.scoreView.setText(String.format(Locale.US, "%d : %d", round.myScore, round.oppScore));
        if (this.launchedRound <= round.round) {
            this.launchGate.clear();
            this.roundLaunchScheduled = false;
            if (this.pendingTutorialStart != null && this.pendingTutorialStart.round <= round.round) {
                int requestCode = this.pendingTutorialRequestCode;
                this.pendingTutorialStart = null;
                this.pendingTutorialInfo = null;
                this.pendingTutorialRequestCode = -1;
                if (requestCode >= 0) {
                    finishActivity(requestCode);
                }
            }
            showIntermissionUi();
        }
    }

    @Override
    public void onMatchEnd(MatchEnd end) {
        diagnosticMatchId=end.matchId;
        diagnostic("match_end","score",end.myScore);
        this.handler.removeCallbacksAndMessages(null);
        this.launchGate.clear();
        this.roundLaunchScheduled = false;
        this.launchedRound = -1;
        int tutorialRequestCode = this.pendingTutorialRequestCode;
        this.pendingTutorialRequestCode = -1;
        this.pendingTutorialStart = null;
        this.pendingTutorialInfo = null;
        if (tutorialRequestCode >= 0) {
            finishActivity(tutorialRequestCode);
        }
        showSettlementUi(end);
    }

    @Override
    public void onRoomCreated(String code) {
    }

    @Override
    public void onHistory(OnlineMessages.HistoryEntry[] entries) {
    }

    @Override
    public void onTop(OnlineMessages.TopEntry[] entries) {
    }

    @Override
    public void onOpponentStart() {
        // Origin's confirmed state: the waiting card's label flips to
        // "your opponent is ready" the moment the second START lands.
        if (this.acceptWaiting) {
            this.waitingTitle.setText(R.string.opponent_is_ready);
        }
    }

    @Override
    public void onOpponentDisconnected() {
        showReconnectStatus();
    }

    @Override
    public void onOpponentReconnected() {
        hideReconnectStatus();
    }

    @Override
    public void onError(int code, String message) {
        Toast.makeText(this, message != null ? message : getString(R.string.online_error_connection),
                Toast.LENGTH_SHORT).show();
        if (!OnlineSession.get().isInMatch()) {
            showSearchUi();
        }
    }
}
