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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.cube.memorygames.MemoryApplicationModel;
import com.cube.memorygames.api.local.LocalDataManager;
import com.cube.memorygames.api.online.OnlineMessages.AuthOk;
import com.cube.memorygames.api.online.OnlineMessages.MatchEnd;
import com.cube.memorygames.api.online.OnlineMessages.MatchFound;
import com.cube.memorygames.api.online.OnlineMessages.OpponentScore;
import com.cube.memorygames.api.online.OnlineMessages.RoundEnd;
import com.cube.memorygames.api.online.OnlineMessages.RoundStart;
import com.cube.memorygames.Games;
import com.cube.memorygames.MainMenuActivity;
import com.cube.memorygames.model.GameInfo;
import com.memory.brain.training.games.R;

import java.util.Locale;

/**
 * Orchestrates one online PK match: matchmaking feedback, per-round launch
 * of the game activities, intermission scoreboards and the final settlement.
 *
 * Visuals replicate the surviving original online resources: the light app
 * background, the settlement layout of dialog_online_result.xml (result
 * title in grid_correct, a 110dip doodle, coin and crown rating rows,
 * button_play_online actions) and the original waiting strings.
 */
public class OnlineMatchActivity extends AppCompatActivity implements OnlineSession.MatchListener {

    private static final int REQUEST_ROUND = 1001;
    private static final long COUNTDOWN_MS = 3000L;

    private Typeface fontLight;
    private TextView statusView;
    private TextView detailView;
    private TextView scoreView;
    private ImageView doodleView;
    private LinearLayout coinRow;
    private TextView coinDelta;
    private TextView coinTotal;
    private LinearLayout ratingRow;
    private TextView ratingDelta;
    private TextView ratingTotal;
    private Button actionButton;
    private Button exitButton;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean roundLaunchScheduled;
    private int launchedRound = -1;

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
        OnlineSession.get().setMatchListener(null);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        OnlineSession session = OnlineSession.get();
        if (session.isInMatch()) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.dialog_lose_bet)
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
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.background));
        root.setPadding(dp(24), dp(48), dp(24), dp(24));
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        this.statusView = new TextView(this);
        this.statusView.setTypeface(this.fontLight, Typeface.BOLD);
        this.statusView.setTextSize(25.0f);
        this.statusView.setTextColor(ContextCompat.getColor(this, R.color.grid_correct));
        this.statusView.setGravity(Gravity.CENTER);
        root.addView(this.statusView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        this.doodleView = new ImageView(this);
        this.doodleView.setVisibility(View.GONE);
        LinearLayout.LayoutParams doodleParams = new LinearLayout.LayoutParams(dp(110), dp(110));
        doodleParams.topMargin = dp(24);
        doodleParams.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(this.doodleView, doodleParams);

        this.detailView = new TextView(this);
        this.detailView.setTypeface(this.fontLight);
        this.detailView.setTextSize(18.0f);
        this.detailView.setTextColor(ContextCompat.getColor(this, R.color.onlineStatsColor));
        this.detailView.setGravity(Gravity.CENTER);
        this.detailView.setPadding(0, dp(12), 0, 0);
        root.addView(this.detailView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        this.scoreView = new TextView(this);
        this.scoreView.setTypeface(this.fontLight, Typeface.BOLD);
        this.scoreView.setTextSize(34.0f);
        this.scoreView.setTextColor(Color.BLACK);
        this.scoreView.setGravity(Gravity.CENTER);
        this.scoreView.setPadding(0, dp(10), 0, 0);
        root.addView(this.scoreView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Coin row (+15 [coin] 1298) — dialog_online_result.xml parity.
        this.coinRow = new LinearLayout(this);
        this.coinRow.setGravity(Gravity.CENTER_VERTICAL);
        this.coinRow.setGravity(Gravity.CENTER_HORIZONTAL);
        this.coinRow.setPadding(0, dp(12), 0, 0);
        this.coinDelta = styledRowText();
        this.coinRow.addView(this.coinDelta);
        ImageView coin = new ImageView(this);
        coin.setImageResource(R.drawable.ic_coin);
        LinearLayout.LayoutParams coinParams = new LinearLayout.LayoutParams(dp(24), dp(24));
        coinParams.leftMargin = dp(16);
        coinParams.rightMargin = dp(16);
        this.coinRow.addView(coin, coinParams);
        this.coinTotal = styledRowText();
        this.coinRow.addView(this.coinTotal);
        this.coinRow.setVisibility(View.GONE);
        root.addView(this.coinRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Rating row (-5 [crown] 45) — dialog_online_result.xml parity.
        this.ratingRow = new LinearLayout(this);
        this.ratingRow.setGravity(Gravity.CENTER_HORIZONTAL);
        this.ratingRow.setPadding(0, dp(8), 0, 0);
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
        this.ratingRow.addView(crown, crownParams);
        this.ratingTotal = styledRowText();
        this.ratingRow.addView(this.ratingTotal);
        this.ratingRow.setVisibility(View.GONE);
        root.addView(this.ratingRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        this.actionButton = originalButton();
        this.actionButton.setVisibility(View.GONE);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        actionParams.topMargin = dp(36);
        actionParams.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(this.actionButton, actionParams);

        this.exitButton = originalButton();
        this.exitButton.setVisibility(View.GONE);
        LinearLayout.LayoutParams exitParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        exitParams.topMargin = dp(12);
        exitParams.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(this.exitButton, exitParams);

        return root;
    }

    private TextView styledRowText() {
        TextView text = new TextView(this);
        text.setTypeface(this.fontLight);
        text.setTextSize(20.0f);
        text.setTextColor(Color.BLACK);
        return text;
    }

    /** The original flat blue action button (button_play_online, 42dip high). */
    private Button originalButton() {
        Button button = new Button(this);
        button.setTypeface(this.fontLight);
        button.setTextSize(20.0f);
        button.setTextColor(Color.WHITE);
        button.setBackgroundResource(R.drawable.button_play_online);
        button.setAllCaps(false);
        button.setPadding(dp(48), 0, dp(48), 0);
        return button;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void showSearchUi() {
        this.statusView.setText(R.string.searching_for_opponents);
        this.detailView.setText(android.text.Html.fromHtml(getString(R.string.online_tutorial_1)));
        this.scoreView.setText("");
        this.doodleView.setVisibility(View.VISIBLE);
        this.doodleView.setImageResource(R.drawable.doodle_26);
        this.coinRow.setVisibility(View.GONE);
        this.ratingRow.setVisibility(View.GONE);
        this.actionButton.setVisibility(View.VISIBLE);
        this.actionButton.setText(R.string.cancel);
        this.actionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OnlineSession.get().cancelMatchmaking();
                finish();
            }
        });
        this.exitButton.setVisibility(View.GONE);
    }

    private void showMatchUi() {
        OnlineMessages.OpponentInfo opponent = OnlineSession.get().getOpponent();
        OnlineMessages.RoundDesc[] rounds = OnlineSession.get().getRounds();
        String opponentName = opponent != null ? opponent.nickname : "?";
        int totalRounds = rounds != null ? rounds.length : 0;
        this.statusView.setText(String.format(Locale.US, "%s %s %s",
                OnlineIdentity.getNickname(this), getString(R.string.online_vs), opponentName));
        this.detailView.setText(String.format(Locale.US, "%s %d/%d",
                getString(R.string.online_round_word),
                Math.max(OnlineSession.get().getCurrentRound(), 1), Math.max(totalRounds, 1)));
        this.scoreView.setText("0 : 0");
        this.doodleView.setVisibility(View.VISIBLE);
        this.doodleView.setImageResource(R.drawable.doodle_42);
        this.coinRow.setVisibility(View.GONE);
        this.ratingRow.setVisibility(View.GONE);
        this.actionButton.setVisibility(View.GONE);
        this.exitButton.setVisibility(View.GONE);
    }

    private void showWaitingUi() {
        this.statusView.setText(R.string.opponent_waiting);
        this.doodleView.setVisibility(View.VISIBLE);
        this.doodleView.setImageResource(R.drawable.doodle_42);
    }

    private void showSettlementUi(final MatchEnd end) {
        boolean win = OnlineSession.RESULT_WIN.equals(end.result);
        boolean draw = OnlineSession.RESULT_DRAW.equals(end.result);
        this.statusView.setText(draw ? R.string.online_draw : (win ? R.string.online_win : R.string.online_lose));
        this.detailView.setText("");
        this.scoreView.setText(String.format(Locale.US, "%d : %d", end.myScore, end.oppScore));
        this.doodleView.setVisibility(View.VISIBLE);
        this.doodleView.setImageResource(win ? R.drawable.doodle_33
                : draw ? R.drawable.doodle_26 : R.drawable.doodle_31);
        this.coinRow.setVisibility(View.VISIBLE);
        this.coinDelta.setText(String.format(Locale.US, "%+d", end.coinDelta));
        this.coinTotal.setText(String.valueOf(end.balance));
        this.ratingRow.setVisibility(View.VISIBLE);
        this.ratingDelta.setText(String.format(Locale.US, "%+d", end.eloDelta));
        this.ratingTotal.setText(String.valueOf(end.elo));
        this.actionButton.setVisibility(View.VISIBLE);
        this.actionButton.setText(R.string.online_again);
        this.actionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OnlineSession.get().startMatchmaking();
                showSearchUi();
            }
        });
        this.exitButton.setVisibility(View.VISIBLE);
        this.exitButton.setText(R.string.online_exit);
        this.exitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    // ---------------------------------------------------------- round launch

    private void launchRound(final RoundStart start) {
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
        this.statusView.setText("3");
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

    private void startGameActivity(RoundStart start, GameInfo info) {
        if (isFinishing()) {
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
        if (requestCode == REQUEST_ROUND) {
            // Skip the waiting board when the match already settled while the
            // round activity was in front — showSettlementUi ran earlier and
            // must not be overwritten (real-device race, 2026-10-03).
            if (OnlineSession.get().getMatchId() == null) {
                return;
            }
            showWaitingUi();
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
        this.statusView.setText(R.string.opponent_waiting);
    }

    @Override
    public void onMatchEnd(MatchEnd end) {
        this.handler.removeCallbacksAndMessages(null);
        this.roundLaunchScheduled = false;
        this.launchedRound = -1;
        showSettlementUi(end);
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
