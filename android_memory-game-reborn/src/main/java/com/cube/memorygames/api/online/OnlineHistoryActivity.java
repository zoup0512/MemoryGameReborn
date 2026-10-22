package com.cube.memorygames.api.online;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.cube.memorygames.api.online.OnlineMessages.HistoryEntry;
import com.cube.memorygames.utils.DateUtils;
import com.memory.brain.training.games.R;

import java.util.Calendar;
import java.util.Locale;

/** Recent online matches grouped by day. */
public class OnlineHistoryActivity extends AppCompatActivity implements OnlineSession.MatchListener {

    public static final String EXTRA_MODE_FILTER = "historyModeFilter";
    private static final String DATE_FORMAT = "EEEE, dd.MM.yy";
    private static final long RESPONSE_TIMEOUT_MS = 10000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable responseTimeout = new Runnable() {
        @Override
        public void run() {
            if (waitingForResponse) {
                showError();
            }
        }
    };
    private final Runnable readinessPoll = new Runnable() {
        @Override
        public void run() {
            if (!waitingForResponse || requested) {
                return;
            }
            if (OnlineSession.get().getState() == OnlineSession.STATE_IDLE) {
                requestHistory();
            } else {
                handler.postDelayed(this, 500L);
            }
        }
    };
    private LinearLayout list;
    private ScrollView scroller;
    private OnlineStateView stateView;
    private boolean requested;
    private boolean waitingForResponse;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        OnlineSession.get().init(getApplicationContext());
        setContentView(buildView());
        OnlineSession.get().setMatchListener(this);
        this.waitingForResponse = true;
        showLoading();
        requestWhenReady();
    }

    @Override
    protected void onDestroy() {
        this.handler.removeCallbacksAndMessages(null);
        // The lobby may already have reclaimed listener ownership in onResume.
        if (OnlineSession.get().getMatchListener() == this) {
            OnlineSession.get().setMatchListener(null);
        }
        super.onDestroy();
    }

    private void requestWhenReady() {
        this.requested = false;
        this.waitingForResponse = true;
        showLoading();
        this.handler.removeCallbacks(this.readinessPoll);
        this.handler.removeCallbacks(this.responseTimeout);
        this.handler.postDelayed(this.responseTimeout, RESPONSE_TIMEOUT_MS);
        if (OnlineSession.get().getState() == OnlineSession.STATE_DISCONNECTED) {
            OnlineSession.get().connect();
        }
        this.handler.post(this.readinessPoll);
    }

    private void requestHistory() {
        if (OnlineSession.get().getState() != OnlineSession.STATE_IDLE) {
            return;
        }
        this.requested = true;
        OnlineSession.get().requestHistory(getIntent().getStringExtra(EXTRA_MODE_FILTER));
    }

    private View buildView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(OnlineUi.color(this, R.color.online_page));
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        int title = OnlineSession.MODE_FRIEND.equals(getIntent().getStringExtra(EXTRA_MODE_FILTER))
                ? R.string.party_legacy_history : R.string.title_online_history;
        OnlineUi.ToolbarBlock toolbar = OnlineUi.toolbar(this, title,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        finish();
                    }
                });
        root.addView(toolbar.view, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                OnlineUi.dim(this, R.dimen.online_toolbar_height)));
        root.addView(OnlineUi.divider(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        FrameLayout body = new FrameLayout(this);
        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        this.scroller = new ScrollView(this);
        this.scroller.setFillViewport(true);
        this.scroller.setClipToPadding(false);
        FrameLayout listWrapper = new FrameLayout(this);
        this.list = OnlineUi.page(this);
        int contentWidth = Math.min(getResources().getDisplayMetrics().widthPixels,
                OnlineUi.dim(this, R.dimen.online_content_max_width));
        FrameLayout.LayoutParams listParams = new FrameLayout.LayoutParams(
                contentWidth, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        listWrapper.addView(this.list, listParams);
        this.scroller.addView(listWrapper, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        body.addView(this.scroller, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        this.stateView = new OnlineStateView(this);
        body.addView(this.stateView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return root;
    }

    private void showLoading() {
        if (this.scroller != null) {
            this.scroller.setVisibility(View.GONE);
        }
        if (this.stateView != null) {
            this.stateView.showLoading(R.string.online_history_loading);
        }
    }

    private void showError() {
        this.handler.removeCallbacks(this.readinessPoll);
        this.handler.removeCallbacks(this.responseTimeout);
        this.waitingForResponse = false;
        this.scroller.setVisibility(View.GONE);
        this.stateView.showError(R.string.online_history_error, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestWhenReady();
            }
        });
    }

    private void render(HistoryEntry[] entries) {
        this.handler.removeCallbacks(this.readinessPoll);
        this.handler.removeCallbacks(this.responseTimeout);
        this.waitingForResponse = false;
        this.list.removeAllViews();
        if (entries == null || entries.length == 0) {
            this.scroller.setVisibility(View.GONE);
            this.stateView.showEmpty(R.string.online_history_empty, 0);
            return;
        }

        this.stateView.hideState();
        this.scroller.setAlpha(0.0f);
        this.scroller.setVisibility(View.VISIBLE);
        this.scroller.animate().alpha(1.0f).setDuration(220L).start();
        String myName = myName();
        long lastDay = Long.MIN_VALUE;
        Calendar calendar = Calendar.getInstance();
        String modeFilter = getIntent().getStringExtra(EXTRA_MODE_FILTER);
        for (HistoryEntry entry : entries) {
            if (entry == null || (modeFilter != null && !modeFilter.equals(entry.mode))) {
                continue;
            }
            calendar.setTimeInMillis(entry.ts);
            long day = calendar.get(Calendar.YEAR) * 1000L + calendar.get(Calendar.DAY_OF_YEAR);
            if (day != lastDay) {
                this.list.addView(dateHeader(entry.ts));
                lastDay = day;
            }
            this.list.addView(matchCard(entry, myName), OnlineUi.cardParams(this));
        }

        if (this.list.getChildCount() == 0) {
            this.scroller.setVisibility(View.GONE);
            this.stateView.showEmpty(R.string.online_history_empty, 0);
        } else {
            this.scroller.scrollTo(0, 0);
        }
    }

    private TextView dateHeader(long ts) {
        TextView header = OnlineUi.text(this,
                DateUtils.getTodayYesterdayOrFormattedDateString(this, ts, DATE_FORMAT),
                14.0f, R.color.online_text_secondary, OnlineUi.medium(this));
        header.setAllCaps(true);
        header.setLetterSpacing(0.08f);
        header.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        header.setPadding(dp(4), dp(18), dp(4), dp(2));
        return header;
    }

    private View matchCard(HistoryEntry entry, String myName) {
        boolean win = OnlineSession.RESULT_WIN.equals(entry.result);
        boolean draw = OnlineSession.RESULT_DRAW.equals(entry.result);

        LinearLayout card = OnlineUi.card(this);
        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView outcome = outcomeChip(entry.result);
        statusRow.addView(outcome, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, OnlineUi.dim(this, R.dimen.online_chip_height)));

        TextView mode = OnlineUi.chip(this, modeLabel(entry));
        LinearLayout.LayoutParams modeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, OnlineUi.dim(this, R.dimen.online_chip_height));
        modeParams.leftMargin = dp(8);
        statusRow.addView(mode, modeParams);
        statusRow.addView(new View(this), new LinearLayout.LayoutParams(0, dp(1), 1.0f));
        card.addView(statusRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout players = new LinearLayout(this);
        players.setOrientation(LinearLayout.HORIZONTAL);
        players.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams playersParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        playersParams.topMargin = dp(14);
        card.addView(players, playersParams);

        players.addView(playerColumn(safeName(myName), entry.myScore, win && !draw, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView versus = OnlineUi.text(this, getString(R.string.online_vs), 12.0f,
                R.color.online_text_secondary, OnlineUi.medium(this));
        versus.setGravity(Gravity.CENTER);
        versus.setAllCaps(true);
        versus.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams versusParams = new LinearLayout.LayoutParams(
                dp(42), ViewGroup.LayoutParams.WRAP_CONTENT);
        versusParams.leftMargin = dp(4);
        versusParams.rightMargin = dp(4);
        players.addView(versus, versusParams);

        players.addView(playerColumn(safeName(entry.opponent), entry.oppScore, !win && !draw, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        card.addView(new OnlineHistoryMatchIdView(this, entry.matchId));
        return card;
    }

    private TextView outcomeChip(String result) {
        int colorRes;
        int backgroundRes;
        CharSequence label;
        if (OnlineSession.RESULT_WIN.equals(result)) {
            colorRes = R.color.online_success;
            backgroundRes = R.color.online_success_soft;
            label = getString(R.string.online_outcome_win);
        } else if (OnlineSession.RESULT_DRAW.equals(result)) {
            colorRes = R.color.online_warning;
            backgroundRes = R.color.online_warning_soft;
            label = getString(R.string.online_outcome_draw);
        } else {
            colorRes = R.color.online_danger;
            backgroundRes = R.color.online_danger_soft;
            label = getString(R.string.online_outcome_loss);
        }

        TextView chip = OnlineUi.text(this, label, 12.0f, colorRes, OnlineUi.medium(this));
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setBackground(OnlineUi.roundedBackground(this, backgroundRes, 50));
        chip.setMinHeight(OnlineUi.dim(this, R.dimen.online_chip_height));
        chip.setPadding(dp(12), 0, dp(12), 0);
        return chip;
    }

    private View playerColumn(String name, int score, boolean winner, boolean mirrored) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(mirrored ? Gravity.END : Gravity.START);

        TextView nameView = OnlineUi.text(this, name, 15.0f,
                R.color.online_text_primary, winner ? OnlineUi.medium(this) : OnlineUi.regular(this));
        nameView.setGravity((mirrored ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL);
        nameView.setMaxLines(2);
        nameView.setEllipsize(TextUtils.TruncateAt.END);
        nameView.setMinHeight(dp(40));
        column.addView(nameView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView scoreView = OnlineUi.text(this, String.format(Locale.getDefault(), "%d", score),
                28.0f, winner ? R.color.online_primary : R.color.online_text_primary,
                OnlineUi.bold(this));
        scoreView.setGravity(mirrored ? Gravity.END : Gravity.START);
        LinearLayout.LayoutParams scoreParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scoreParams.topMargin = dp(2);
        column.addView(scoreView, scoreParams);
        return column;
    }

    private CharSequence modeLabel(HistoryEntry entry) {
        if (entry.vsBot && OnlineSession.MODE_PRACTICE.equals(entry.mode)) {
            return getString(R.string.online_mode_practice_bot);
        }
        if (entry.vsBot) {
            return getString(R.string.online_mode_bot);
        }
        if (OnlineSession.MODE_PRACTICE.equals(entry.mode)) {
            return getString(R.string.online_mode_practice);
        }
        if (OnlineSession.MODE_FRIEND.equals(entry.mode)) {
            return getString(R.string.online_mode_friend);
        }
        return getString(R.string.online_mode_ranked);
    }

    private String safeName(String name) {
        return TextUtils.isEmpty(name) ? "—" : name.trim();
    }

    private String myName() {
        OnlineMessages.AuthOk profile = OnlineSession.get().getLastProfile();
        return profile != null ? profile.nickname : OnlineIdentity.getNickname(this);
    }

    // ------------------------------------------------------ listener stubs

    @Override
    public void onHistory(final HistoryEntry[] entries) {
        this.handler.post(new Runnable() {
            @Override
            public void run() {
                render(entries);
            }
        });
    }

    @Override
    public void onStateChanged(int state) {
        if (state == OnlineSession.STATE_IDLE && this.waitingForResponse && !this.requested) {
            requestHistory();
        } else if (state == OnlineSession.STATE_DISCONNECTED && this.waitingForResponse) {
            showError();
        }
    }

    @Override
    public void onAuthOk(OnlineMessages.AuthOk profile) {
    }

    @Override
    public void onMatchFound(OnlineMessages.MatchFound match) {
    }

    @Override
    public void onMatchCancelled(MatchAcceptance.Action action, String mode) {
    }

    @Override
    public void onRoundStart(OnlineMessages.RoundStart round) {
    }

    @Override
    public void onOpponentScore(OnlineMessages.OpponentScore score) {
    }

    @Override
    public void onRoundEnd(OnlineMessages.RoundEnd round) {
    }

    @Override
    public void onMatchEnd(OnlineMessages.MatchEnd end) {
    }

    @Override
    public void onRoomCreated(String code) {
    }

    @Override
    public void onOpponentDisconnected() {
    }

    @Override
    public void onOpponentReconnected() {
    }

    @Override
    public void onTop(OnlineMessages.TopEntry[] entries) {
    }

    @Override
    public void onOpponentStart() {
    }

    @Override
    public void onError(int code, String message) {
        if (this.waitingForResponse) {
            this.handler.post(new Runnable() {
                @Override
                public void run() {
                    showError();
                }
            });
        }
    }

    private int dp(int value) {
        return OnlineUi.dp(this, value);
    }
}
