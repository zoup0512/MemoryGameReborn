package com.cube.memorygames.api.online;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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

    private Typeface fontLight;
    // Top bar (activity_play_online.xml).
    private TextView coinsView;
    // VS header (fragment_header.xml).
    private OnlineUi.AvatarBlock myAvatar;
    private OnlineUi.AvatarBlock oppAvatar;
    private TextView myNameView;
    private TextView oppNameView;
    private ImageView boltView;
    private final View[] pulseCircles = new View[3];
    private int pulsePosition;
    // Board container and the boards swapped inside it.
    private FrameLayout content;
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
    private TimerView gameListTimer;
    // Intermission card between rounds (dialog_online_progress.xml).
    private View intermissionBoard;
    private TextView interTitle;
    private TextView interGameNumber;
    private TextView interScore;
    private TimerView interTimer;
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
    private Button okButton;
    // Bottom bar (activity_play_online.xml buttonsContainer).
    private LinearLayout bottomBar;
    private TextView bottomButton;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean roundLaunchScheduled;
    private int launchedRound = -1;

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

    // ------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        OnlineSession.get().init(getApplicationContext());
        this.fontLight = Typeface.createFromAsset(getAssets(), "Roboto-Light.ttf");
        setContentView(buildView());
        OnlineSession.get().setMatchListener(this);
        if (OnlineSession.get().isInMatch()) {
            showMatchUi();
        } else {
            showSearchUi();
            OnlineSession.get().startMatchmaking();
        }
    }

    @Override
    protected void onDestroy() {
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
        android.util.Log.d("OnlinePK", "finish() called", new IllegalStateException("trace"));
        super.finish();
    }

    @Override
    public void onBackPressed() {
        OnlineSession session = OnlineSession.get();
        if (session.isInMatch()) {
            new AlertDialog.Builder(this)
                    .setMessage(session.isPracticeMode()
                            // Practice has no bet to lose; keep the forfeit warning honest.
                            ? R.string.online_quit_practice
                            : R.string.dialog_lose_bet)
                    .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    })
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            OnlineSession.get().resign();
                            OnlineMatchActivity.this.finish();
                        }
                    })
                    .show();
            return;
        }
        session.cancelMatchmaking();
        super.onBackPressed();
    }

    // ------------------------------------------------------------------- UI

    private View buildView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.background));
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(buildTopBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(45)));

        root.addView(buildVsHeader(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        this.content = new FrameLayout(this);
        this.content.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        root.addView(this.content);

        View divider = new View(this);
        divider.setBackgroundColor(ContextCompat.getColor(this, R.color.grid_stroke));
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

    /** activity_play_online.xml's panel_header: back, title, coin count. */
    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Color.WHITE);
        bar.setPadding(0, 0, dp(15), 0);

        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_arrow_back_white);
        back.setColorFilter(Color.BLACK);
        back.setPadding(dp(8), dp(8), dp(8), dp(8));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(dp(45), dp(45));
        backParams.leftMargin = dp(8);
        backParams.rightMargin = dp(8);
        bar.addView(back, backParams);

        TextView title = new TextView(this);
        title.setText(R.string.tab_online);
        title.setTypeface(this.fontLight);
        title.setTextSize(20.0f);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView spacer = new TextView(this);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

        this.coinsView = new TextView(this);
        this.coinsView.setTypeface(this.fontLight, Typeface.BOLD);
        this.coinsView.setTextSize(20.0f);
        this.coinsView.setTextColor(Color.BLACK);
        this.coinsView.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        this.coinsView.setPadding(dp(4), 0, dp(4), 0);
        bar.addView(this.coinsView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ImageView coin = new ImageView(this);
        coin.setImageResource(R.drawable.ic_coin);
        coin.setPadding(dp(11), dp(11), dp(11), dp(11));
        bar.addView(coin, new LinearLayout.LayoutParams(dp(45), dp(45)));

        refreshCoins();
        return bar;
    }

    /**
     * fragment_header.xml: my avatar left, the pulse/bolt slot in the middle,
     * the opponent right; 18sp names under each side.
     */
    private View buildVsHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);

        LinearLayout avatars = new LinearLayout(this);
        avatars.setOrientation(LinearLayout.HORIZONTAL);
        avatars.setBaselineAligned(false);
        LinearLayout.LayoutParams avatarsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(100));
        avatarsParams.topMargin = dp(10);
        header.addView(avatars, avatarsParams);

        this.myAvatar = OnlineUi.avatar(this, 100, true);
        LinearLayout.LayoutParams myParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        myParams.leftMargin = dp(25);
        myParams.topMargin = dp(12);
        avatars.addView(this.myAvatar.view, myParams);

        FrameLayout middle = new FrameLayout(this);
        LinearLayout middleRow = new LinearLayout(this);
        middleRow.setOrientation(LinearLayout.HORIZONTAL);
        int[] weights = {30, 40, 60};
        int[] sizes = {16, 24, 32};
        for (int i = 0; i < this.pulseCircles.length; i++) {
            FrameLayout slot = new FrameLayout(this);
            View circle = new View(this);
            circle.setBackgroundResource(R.drawable.online_mode_anim_background);
            circle.setVisibility(View.INVISIBLE);
            FrameLayout.LayoutParams circleParams = new FrameLayout.LayoutParams(dp(sizes[i]), dp(sizes[i]), Gravity.CENTER);
            slot.addView(circle, circleParams);
            this.pulseCircles[i] = circle;
            LinearLayout.LayoutParams slotParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weights[i]);
            middleRow.addView(slot, slotParams);
        }
        middle.addView(middleRow, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        this.boltView = new ImageView(this);
        this.boltView.setImageResource(R.drawable.ic_versus);
        this.boltView.setVisibility(View.GONE);
        FrameLayout.LayoutParams boltParams = new FrameLayout.LayoutParams(dp(96), dp(96), Gravity.CENTER);
        middle.addView(this.boltView, boltParams);
        avatars.addView(middle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

        this.oppAvatar = OnlineUi.avatar(this, 100, true);
        LinearLayout.LayoutParams oppParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        oppParams.rightMargin = dp(25);
        oppParams.topMargin = dp(12);
        avatars.addView(this.oppAvatar.view, oppParams);

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.HORIZONTAL);
        this.myNameView = headerName();
        names.addView(this.myNameView, new LinearLayout.LayoutParams(dp(150), ViewGroup.LayoutParams.WRAP_CONTENT));
        names.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1.0f));
        this.oppNameView = headerName();
        names.addView(this.oppNameView, new LinearLayout.LayoutParams(dp(150), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams namesParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        header.addView(names, namesParams);
        return header;
    }

    private TextView headerName() {
        TextView name = new TextView(this);
        name.setTypeface(this.fontLight);
        name.setTextSize(18.0f);
        name.setTextColor(Color.BLACK);
        name.setGravity(Gravity.CENTER_HORIZONTAL);
        name.setMaxLines(1);
        name.setPadding(0, dp(10), 0, dp(4));
        return name;
    }

    /** fragment_search.xml: the centred searching message plus a hint line. */
    private LinearLayout buildSearchBoard() {
        LinearLayout board = new LinearLayout(this);
        board.setOrientation(LinearLayout.VERTICAL);
        board.setGravity(Gravity.CENTER);
        board.setPadding(dp(24), 0, dp(24), 0);

        this.searchMessage = new TextView(this);
        this.searchMessage.setTypeface(this.fontLight);
        this.searchMessage.setTextSize(18.0f);
        this.searchMessage.setTextColor(Color.BLACK);
        this.searchMessage.setGravity(Gravity.CENTER);
        this.searchMessage.setText(R.string.searching_for_opponents);
        board.addView(this.searchMessage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        this.searchHint = new TextView(this);
        this.searchHint.setTypeface(this.fontLight);
        this.searchHint.setTextSize(15.0f);
        this.searchHint.setTextColor(ContextCompat.getColor(this, R.color.onlineTextHint));
        this.searchHint.setGravity(Gravity.CENTER);
        this.searchHint.setPadding(0, dp(16), 0, 0);
        this.searchHint.setText(android.text.Html.fromHtml(getString(R.string.online_tutorial_1)));
        board.addView(this.searchHint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return board;
    }

    /**
     * fragment_online_stats.xml grammar: the pre-match comparison board -
     * STATISTICS header, wins/losses/draws/ratio rows in three columns and
     * the two BETS coins carrying the live entry fee.
     */
    private LinearLayout buildStatsBoard() {
        LinearLayout board = new LinearLayout(this);
        board.setOrientation(LinearLayout.VERTICAL);

        java.util.List<View> fade = new java.util.ArrayList<View>();
        TextView statsTitle = new TextView(this);
        statsTitle.setTypeface(this.fontLight, Typeface.BOLD);
        statsTitle.setTextSize(20.0f);
        statsTitle.setTextColor(Color.BLACK);
        statsTitle.setGravity(Gravity.CENTER);
        statsTitle.setText(R.string.stats);
        statsTitle.setPadding(0, dp(8), 0, dp(8));
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

        TextView betsTitle = new TextView(this);
        betsTitle.setTypeface(this.fontLight, Typeface.BOLD);
        betsTitle.setTextSize(20.0f);
        betsTitle.setTextColor(Color.BLACK);
        betsTitle.setGravity(Gravity.CENTER);
        betsTitle.setText(R.string.bets);
        betsTitle.setPadding(0, dp(16), 0, dp(8));
        board.addView(betsTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        fade.add(betsTitle);

        LinearLayout betsRow = new LinearLayout(this);
        betsRow.setGravity(Gravity.CENTER);
        // Practice duels are free: showing the ranked entry fee would imply
        // a stake the match never charges.
        int fee = OnlineSession.get().isPracticeMode() ? 0 : OnlineSession.get().getServerEntryFee();
        betsRow.addView(coinBadge(fee));
        betsRow.addView(coinBadge(fee));
        board.addView(betsRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        fade.add(betsRow);
        this.statsFadeViews = fade;
        return board;
    }

    /** One wins/losses/draws/ratio line: value / title / value columns. */
    private View addStatsRow(LinearLayout parent, int mine, int titleRes, int theirs) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(statsCell(String.valueOf(mine)), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.33f));
        TextView title = statsCell(getString(titleRes));
        title.setTypeface(this.fontLight, Typeface.BOLD);
        row.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.34f));
        row.addView(statsCell(String.valueOf(theirs)), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.33f));
        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private TextView statsCell(String text) {
        TextView cell = new TextView(this);
        cell.setTypeface(this.fontLight);
        cell.setTextSize(18.0f);
        cell.setTextColor(ContextCompat.getColor(this, R.color.onlineStatsColor));
        cell.setGravity(Gravity.CENTER);
        cell.setText(text);
        return cell;
    }

    private int ratio(int wins, int total) {
        return total == 0 ? 0 : Math.round(wins * 100.0f / total);
    }

    /** fragment_online_stats.xml's ic_coin badge showing the entry fee. */
    private TextView coinBadge(int fee) {
        TextView badge = new TextView(this);
        badge.setTypeface(this.fontLight);
        badge.setTextSize(14.0f);
        badge.setTextColor(Color.WHITE);
        badge.setBackgroundResource(R.drawable.ic_coin);
        badge.setGravity(Gravity.CENTER);
        badge.setText(String.valueOf(fee));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(38), dp(38));
        params.leftMargin = dp(16);
        params.rightMargin = dp(16);
        badge.setLayoutParams(params);
        return badge;
    }

    /** Round line + live score + the 3-2-1 launch countdown. */
    private LinearLayout buildMatchBoard() {
        LinearLayout board = new LinearLayout(this);
        board.setOrientation(LinearLayout.VERTICAL);
        board.setGravity(Gravity.CENTER);

        this.statusView = new TextView(this);
        this.statusView.setTypeface(this.fontLight, Typeface.BOLD);
        this.statusView.setTextSize(25.0f);
        this.statusView.setTextColor(ContextCompat.getColor(this, R.color.grid_correct));
        this.statusView.setGravity(Gravity.CENTER);
        board.addView(this.statusView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        this.detailView = new TextView(this);
        this.detailView.setTypeface(this.fontLight);
        this.detailView.setTextSize(16.0f);
        this.detailView.setTextColor(Color.BLACK);
        this.detailView.setGravity(Gravity.CENTER);
        this.detailView.setPadding(0, dp(8), 0, 0);
        board.addView(this.detailView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        this.scoreView = new TextView(this);
        this.scoreView.setTypeface(this.fontLight, Typeface.BOLD);
        this.scoreView.setTextSize(34.0f);
        this.scoreView.setTextColor(Color.BLACK);
        this.scoreView.setGravity(Gravity.CENTER);
        this.scoreView.setPadding(0, dp(10), 0, 0);
        board.addView(this.scoreView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return board;
    }

    /**
     * The waiting board as the original progress dialog: button_pro_normal
     * card, bold grid_correct title, a 16sp line and the 100dip TimerView.
     */
    private LinearLayout buildWaitingCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackgroundResource(R.drawable.button_pro_normal);
        card.setPadding(0, dp(8), 0, dp(16));

        this.waitingTitle = new TextView(this);
        this.waitingTitle.setTypeface(this.fontLight, Typeface.BOLD);
        this.waitingTitle.setTextSize(20.0f);
        this.waitingTitle.setTextColor(ContextCompat.getColor(this, R.color.grid_correct));
        this.waitingTitle.setGravity(Gravity.CENTER);
        this.waitingTitle.setText(R.string.opponent_waiting);
        card.addView(this.waitingTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        this.waitingLine = new TextView(this);
        this.waitingLine.setTypeface(this.fontLight);
        this.waitingLine.setTextSize(16.0f);
        this.waitingLine.setTextColor(Color.BLACK);
        this.waitingLine.setGravity(Gravity.CENTER);
        this.waitingLine.setPadding(dp(16), dp(24), dp(16), 0);
        card.addView(this.waitingLine, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        this.timerView = new TimerView(this);
        LinearLayout.LayoutParams timerParams = new LinearLayout.LayoutParams(dp(100), dp(100));
        timerParams.topMargin = dp(40);
        timerParams.bottomMargin = dp(40);
        timerParams.gravity = Gravity.CENTER_HORIZONTAL;
        card.addView(this.timerView, timerParams);
        return card;
    }

    /**
     * dialog_online_result.xml as a board: 300dip white cell, 25sp
     * grid_correct title, 110dip doodle, coin and crown rows, OK action.
     */
    private LinearLayout buildSettlementCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackgroundResource(R.drawable.drawable_state_cell_empty);
        card.setPadding(0, 0, 0, dp(16));

        this.titleView = new TextView(this);
        this.titleView.setTypeface(this.fontLight);
        this.titleView.setTextSize(25.0f);
        this.titleView.setTextColor(ContextCompat.getColor(this, R.color.grid_correct));
        this.titleView.setGravity(Gravity.CENTER);
        this.titleView.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.addView(this.titleView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        this.doodleView = new ImageView(this);
        LinearLayout.LayoutParams doodleParams = new LinearLayout.LayoutParams(dp(110), dp(110));
        doodleParams.topMargin = dp(24);
        doodleParams.gravity = Gravity.CENTER_HORIZONTAL;
        card.addView(this.doodleView, doodleParams);

        // Coin row (+15 [coin] 1298) — dialog_online_result.xml parity.
        this.coinRow = new LinearLayout(this);
        this.coinRow.setGravity(Gravity.CENTER);
        this.coinRow.setPadding(dp(16), dp(8), dp(16), dp(8));
        this.coinDelta = styledRowText();
        this.coinRow.addView(this.coinDelta);
        ImageView coin = new ImageView(this);
        coin.setImageResource(R.drawable.ic_coin);
        LinearLayout.LayoutParams coinParams = new LinearLayout.LayoutParams(dp(24), dp(24));
        coinParams.leftMargin = dp(16);
        coinParams.rightMargin = dp(16);
        coinParams.gravity = Gravity.CENTER_VERTICAL;
        this.coinRow.addView(coin, coinParams);
        this.coinTotal = styledRowText();
        this.coinRow.addView(this.coinTotal);
        card.addView(this.coinRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Rating row (-5 [crown] 45) — dialog_online_result.xml parity.
        this.ratingRow = new LinearLayout(this);
        this.ratingRow.setGravity(Gravity.CENTER);
        this.ratingRow.setPadding(dp(16), dp(8), dp(16), dp(8));
        this.ratingDelta = styledRowText();
        this.ratingRow.addView(this.ratingDelta);
        ImageView crown = new ImageView(this);
        crown.setBackgroundResource(R.drawable.drawable_circle_online_rating);
        crown.setImageResource(R.drawable.ic_crown_new);
        crown.setColorFilter(Color.WHITE);
        int pad4 = dp(4);
        crown.setPadding(pad4, pad4, pad4, pad4);
        LinearLayout.LayoutParams crownParams = new LinearLayout.LayoutParams(dp(26), dp(26));
        crownParams.leftMargin = dp(16);
        crownParams.rightMargin = dp(16);
        crownParams.gravity = Gravity.CENTER_VERTICAL;
        this.ratingRow.addView(crown, crownParams);
        this.ratingTotal = styledRowText();
        this.ratingRow.addView(this.ratingTotal);
        card.addView(this.ratingRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        this.okButton = originalButton();
        this.okButton.setText(R.string.ok);
        LinearLayout.LayoutParams okParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        okParams.topMargin = dp(48);
        okParams.gravity = Gravity.CENTER_HORIZONTAL;
        card.addView(this.okButton, okParams);
        return card;
    }

    /**
     * item_list_online_game.xml grammar: the post-match results list - one
     * row per round with the game's name and icon and both scores, the
     * online_game_win_circle riding the round winner, then the bold totals.
     */
    private LinearLayout buildResultsBoard(MatchEnd end) {
        LinearLayout board = new LinearLayout(this);
        board.setOrientation(LinearLayout.VERTICAL);

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

    /** One results row; a null game name marks the bold totals row. */
    private View resultsRow(String name, int imageRes, int mine, int theirs) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);

        boolean totals = name == null;
        if (!totals) {
            TextView nameView = new TextView(this);
            nameView.setTypeface(this.fontLight);
            nameView.setTextSize(18.0f);
            nameView.setTextColor(Color.BLACK);
            nameView.setGravity(Gravity.CENTER);
            nameView.setText(name);
            row.addView(nameView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        LinearLayout scores = new LinearLayout(this);
        scores.setOrientation(LinearLayout.HORIZONTAL);
        scores.addView(resultsScore(String.valueOf(mine), totals, mine > theirs),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.24f));
        if (!totals) {
            ImageView icon = new ImageView(this);
            icon.setImageResource(imageRes);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            scores.addView(icon, new LinearLayout.LayoutParams(0, dp(100), 0.52f));
        } else {
            TextView placeholder = new TextView(this);
            placeholder.setGravity(Gravity.CENTER);
            placeholder.setTypeface(this.fontLight, Typeface.BOLD);
            placeholder.setTextSize(18.0f);
            placeholder.setTextColor(ContextCompat.getColor(this, R.color.onlineStatsColor));
            placeholder.setText("-");
            scores.addView(placeholder, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.52f));
        }
        scores.addView(resultsScore(String.valueOf(theirs), totals, theirs > mine),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.24f));
        row.addView(scores, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    /** A score cell; the round winner's carries the win circle. */
    private TextView resultsScore(String value, boolean bold, boolean winner) {
        TextView score = new TextView(this);
        score.setTypeface(this.fontLight, bold ? Typeface.BOLD : Typeface.NORMAL);
        score.setTextSize(18.0f);
        score.setTextColor(ContextCompat.getColor(this, R.color.onlineStatsColor));
        score.setGravity(Gravity.CENTER);
        score.setText(value);
        if (winner) {
            score.setBackgroundResource(R.drawable.online_game_win_circle);
        }
        int pad = dp(4);
        score.setPadding(pad, pad, pad, pad);
        return score;
    }

    /**
     * The between-rounds intermission card: the original
     * dialog_online_progress.xml inflated as a board - "Game finished",
     * "Your Score: x", "Game n/total" and the 100dip countdown disc riding
     * the rest of the round while the opponent may still be playing.
     */
    private void showIntermissionUi() {
        if (this.intermissionBoard == null) {
            this.intermissionBoard = getLayoutInflater().inflate(R.layout.dialog_online_progress, null);
            this.interTitle = (TextView) this.intermissionBoard.findViewById(R.id.dialog_title);
            this.interGameNumber = (TextView) this.intermissionBoard.findViewById(R.id.gameNumber);
            this.interScore = (TextView) this.intermissionBoard.findViewById(R.id.your_score);
            this.interTimer = (TimerView) this.intermissionBoard.findViewById(R.id.timerView);
            Typeface regular = Typeface.createFromAsset(getAssets(), "Roboto-Regular.ttf");
            this.interTitle.setTypeface(regular);
            this.interGameNumber.setTypeface(this.fontLight);
            this.interScore.setTypeface(regular);
        }
        this.interTitle.setText(R.string.game_finished);
        OnlineMessages.RoundDesc[] rounds = OnlineSession.get().getRounds();
        int total = rounds != null ? rounds.length : 0;
        this.interGameNumber.setText(String.format(Locale.US, "%s %d/%d",
                getString(R.string.game), Math.max(OnlineSession.get().getCurrentRound(), 1),
                Math.max(total, 1)));
        this.interScore.setText(String.format(Locale.US, "%s: %d",
                getString(R.string.your_score), OnlineSession.get().getMyScore()));
        showBoard(this.intermissionBoard, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP);
        long remaining = OnlineSession.get().roundRemainingMs();
        if (remaining >= 1000L) {
            this.interTimer.setVisibility(View.VISIBLE);
            this.interTimer.showTimer((int) remaining, true);
        } else {
            this.interTimer.setVisibility(View.GONE);
        }
    }

    /**
     * fragment_start_game.xml grammar: the pre-match game list - one row per
     * round (name + icon) under a hairline divider, with the 100dip
     * countdown disc origin showed before the first round launched.
     */
    private void showGameListUi(final RoundStart start, final GameInfo info) {
        LinearLayout board = new LinearLayout(this);
        board.setOrientation(LinearLayout.VERTICAL);

        View divider = new View(this);
        divider.setBackgroundColor(ContextCompat.getColor(this, R.color.grid_stroke));
        int pad = dp(10);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        dividerParams.topMargin = pad;
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
                row.setOrientation(LinearLayout.VERTICAL);
                TextView name = new TextView(this);
                name.setTypeface(this.fontLight);
                name.setTextSize(18.0f);
                name.setTextColor(Color.BLACK);
                name.setGravity(Gravity.CENTER);
                name.setText(roundInfo.getGameNameRes());
                row.addView(name, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                ImageView icon = new ImageView(this);
                icon.setImageResource(roundInfo.getGameImageRes());
                icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(100));
                iconParams.topMargin = dp(4);
                iconParams.bottomMargin = dp(4);
                row.addView(icon, iconParams);
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

        showBoard(board, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP);
        this.gameListTimer.showTimer((int) GAME_LIST_TIMEOUT_MS, false);
        this.roundLaunchScheduled = true;
        this.handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (OnlineSession.get().getMatchId() != null) {
                    startGameActivity(start, info);
                }
            }
        }, GAME_LIST_TIMEOUT_MS);
    }

    /** activity_play_online.xml's white bottom bar with one 180dip action. */
    private LinearLayout buildBottomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setGravity(Gravity.CENTER_HORIZONTAL);
        bar.setBackgroundColor(Color.WHITE);
        int pad = getResources().getDimensionPixelOffset(R.dimen.padding_big);
        bar.setPadding(0, pad, 0, pad);

        this.bottomButton = OnlineUi.menuButton(this, this.fontLight, R.string.cancel);
        bar.addView(this.bottomButton, new LinearLayout.LayoutParams(dp(180), ViewGroup.LayoutParams.WRAP_CONTENT));
        return bar;
    }

    private TextView styledRowText() {
        TextView text = new TextView(this);
        text.setTypeface(this.fontLight);
        text.setTextSize(20.0f);
        text.setTextColor(Color.BLACK);
        return text;
    }

    /** The original flat green action button (button_play, 42dip high). */
    private Button originalButton() {
        Button button = new Button(this);
        button.setTypeface(this.fontLight);
        button.setTextSize(20.0f);
        button.setTextColor(Color.WHITE);
        button.setBackgroundResource(R.drawable.button_play);
        button.setAllCaps(false);
        button.setPadding(dp(48), 0, dp(48), 0);
        return button;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    // --------------------------------------------------------------- boards

    private void showBoard(View board, int width, int gravity) {
        this.content.removeAllViews();
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width,
                ViewGroup.LayoutParams.WRAP_CONTENT, gravity);
        if (board == this.waitingCard || board == this.matchBoard || board == this.searchBoard) {
            params.leftMargin = dp(16);
            params.rightMargin = dp(16);
        }
        this.content.addView(board, params);
    }

    /**
     * Same as {@link #showBoard} but the board lives in a fill-viewport
     * ScrollView: on tall/dense screens (fontScale > 1) the settlement card
     * and the results list can outgrow the frame and their bottom action
     * would be clipped out of the view tree — scrolling keeps it reachable.
     */
    private void showScrollableBoard(View board, int width) {
        this.content.removeAllViews();
        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        FrameLayout inner = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        int margin = dp(16);
        params.leftMargin = margin;
        params.rightMargin = margin;
        inner.addView(board, params);
        scroller.addView(inner, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        this.content.addView(scroller, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void showSearchUi() {
        this.acceptWaiting = false;
        fillMyHeaderSide();
        resetOpponentSide();
        startPulse();
        showBoard(this.searchBoard, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER);
        this.bottomBar.setVisibility(View.VISIBLE);
        this.bottomButton.setText(R.string.cancel);
        this.bottomButton.setBackgroundResource(R.drawable.button_play);
        this.bottomButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OnlineSession.get().cancelMatchmaking();
                finish();
            }
        });
    }

    private void showMatchUi() {
        this.acceptWaiting = false;
        fillMyHeaderSide();
        fillOpponentSide(OnlineSession.get().getOpponent());
        stopPulse();
        // The comparison is built against the freshly found opponent, so it
        // is recreated on every match_found.
        if (this.statsBoard != null) {
            this.content.removeView(this.statsBoard);
        }
        this.statsBoard = buildStatsBoard();
        showBoard(this.statsBoard, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER);
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
        // Origin's START confirmation used the blue button_play_online skin.
        this.bottomButton.setBackgroundResource(R.drawable.button_play_online);
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
        this.waitingLine.setText("");
        this.waitingLine.setVisibility(View.GONE);
        showBoard(this.waitingCard, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER);
        this.timerView.setVisibility(View.VISIBLE);
        // Mirrors the server's accept window (acceptTimeoutMs).
        this.timerView.showTimer(ACCEPT_WINDOW_MS, true);
        this.bottomBar.setVisibility(View.GONE);
    }

    private void showSettlementUi(final MatchEnd end) {
        this.acceptWaiting = false;
        refreshCoins();
        boolean win = OnlineSession.RESULT_WIN.equals(end.result);
        boolean draw = OnlineSession.RESULT_DRAW.equals(end.result);
        this.titleView.setText(draw ? R.string.online_draw : (win ? R.string.online_win : R.string.online_lose));
        this.doodleView.setImageResource(win ? R.drawable.doodle_33
                : draw ? R.drawable.doodle_26 : R.drawable.doodle_31);
        this.coinDelta.setText(String.format(Locale.US, "%+d", end.coinDelta));
        this.coinTotal.setText(String.valueOf(end.balance));
        this.ratingDelta.setText(String.format(Locale.US, "%+d", end.eloDelta));
        this.ratingTotal.setText(String.valueOf(end.elo));
        this.myAvatar.rating.setText(String.valueOf(end.elo));

        showScrollableBoard(this.card, dp(300));
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
        this.bottomButton.setBackgroundResource(R.drawable.button_play);
        this.bottomButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OnlineSession.get().startMatchmaking();
                showSearchUi();
            }
        });
    }

    // ---------------------------------------------------------- VS header

    private void fillMyHeaderSide() {
        AuthOk profile = OnlineSession.get().getLastProfile();
        this.myNameView.setText(profile != null ? profile.nickname
                : OnlineIdentity.getNickname(this));
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
        this.coinsView.setText(String.valueOf(MemoryApplicationModel.getInstance()
                .getLocalDataManager().getLocalUser().money));
    }

    // ---------------------------------------------------------- round launch

    private void launchRound(final RoundStart start) {
        this.acceptWaiting = false;
        GameInfo info = Games.get().getGameBiId(start.gameId);
        if (info == null) {
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
            startActivityForResult(tutorial, REQUEST_TUTORIAL);
            return;
        }
        if (start.round == 1) {
            // Origin opened the match with the full game list and its
            // countdown disc (START_TIMEOUT); later rounds use the 3-2-1.
            showGameListUi(start, info);
            return;
        }
        this.statusView.setText("3");
        showBoard(this.matchBoard, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER);
        this.roundLaunchScheduled = true;
        this.handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                statusView.setText("2");
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        statusView.setText("1");
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                startGameActivity(start, info);
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

    private void startGameActivity(RoundStart start, GameInfo info) {
        if (isFinishing() || OnlineSession.get().getMatchId() == null) {
            // The match settled/cancelled while a launch was pending.
            return;
        }
        this.roundLaunchScheduled = false;
        Intent intent = new Intent(this, Games.get().getGameActivity(info));
        intent.putExtra(MainMenuActivity.EXTRA_GAME_INFO, info);
        intent.putExtra(OnlineSession.EXTRA_ONLINE_GAME, true);
        intent.putExtra(OnlineSession.EXTRA_ONLINE_SEED, roundSeed(start.round));
        intent.putExtra(OnlineSession.EXTRA_ONLINE_ROUND, start.round);
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
        if (requestCode == REQUEST_TUTORIAL) {
            if (this.pendingTutorialStart != null && this.pendingTutorialInfo != null) {
                android.content.SharedPreferences prefs =
                        android.preference.PreferenceManager.getDefaultSharedPreferences(this);
                prefs.edit().putInt(PREF_TUTORIAL_SHOW + this.pendingTutorialInfo.getId(),
                        prefs.getInt(PREF_TUTORIAL_SHOW + this.pendingTutorialInfo.getId(), 0) + 1)
                        .apply();
                RoundStart start = this.pendingTutorialStart;
                GameInfo info = this.pendingTutorialInfo;
                this.pendingTutorialStart = null;
                this.pendingTutorialInfo = null;
                startGameActivity(start, info);
            }
            return;
        }
        if (requestCode == REQUEST_ROUND) {
            // Skip the waiting board when the match already settled while the
            // round activity was in front — showSettlementUi ran earlier and
            // must not be overwritten (real-device race, 2026-10-03).
            if (OnlineSession.get().getMatchId() == null) {
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
        showMatchUi();
    }

    @Override
    public void onMatchCancelled() {
        if (OnlineSession.get().isInMatch()) {
            // The START gate expired or the opponent walked away.
            Toast.makeText(this, R.string.toast_game_not_accepted, Toast.LENGTH_SHORT).show();
        }
        showSearchUi();
    }

    @Override
    public void onRoundStart(final RoundStart round) {
        if (this.roundLaunchScheduled || this.launchedRound == round.round) {
            return;
        }
        launchRound(round);
    }

    @Override
    public void onOpponentScore(OpponentScore score) {
        this.scoreView.setText(String.format(Locale.US, "%d : %d",
                OnlineSession.get().getMyScore(), score.score));
    }

    @Override
    public void onRoundEnd(RoundEnd round) {
        this.scoreView.setText(String.format(Locale.US, "%d : %d", round.myScore, round.oppScore));
        if (this.interScore != null) {
            this.interScore.setText(String.format(Locale.US, "%s: %d",
                    getString(R.string.your_score), round.myScore));
        }
    }

    @Override
    public void onMatchEnd(MatchEnd end) {
        this.handler.removeCallbacksAndMessages(null);
        this.roundLaunchScheduled = false;
        this.launchedRound = -1;
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
        Toast.makeText(this, R.string.online_opponent_reconnecting, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onOpponentReconnected() {
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
