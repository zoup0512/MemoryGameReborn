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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.cube.memorygames.api.online.OnlineMessages.TopEntry;
import com.memory.brain.training.games.R;

import java.util.Locale;

/** Online leaderboard with a top-three podium and a stable ranked list. */
public class OnlineTopActivity extends AppCompatActivity implements OnlineSession.MatchListener {

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
                requestTop();
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
        // Do not clear a listener that a returning lobby has already installed.
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

    private void requestTop() {
        if (OnlineSession.get().getState() != OnlineSession.STATE_IDLE) {
            return;
        }
        this.requested = true;
        OnlineSession.get().requestTop();
    }

    private View buildView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(OnlineUi.color(this, R.color.online_page));
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        OnlineUi.ToolbarBlock toolbar = OnlineUi.toolbar(this, R.string.online_top_title,
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
            this.stateView.showLoading(R.string.online_top_loading);
        }
    }

    private void showError() {
        this.handler.removeCallbacks(this.readinessPoll);
        this.handler.removeCallbacks(this.responseTimeout);
        this.waitingForResponse = false;
        this.scroller.setVisibility(View.GONE);
        this.stateView.showError(R.string.online_top_error, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestWhenReady();
            }
        });
    }

    private void render(TopEntry[] entries) {
        this.handler.removeCallbacks(this.readinessPoll);
        this.handler.removeCallbacks(this.responseTimeout);
        this.waitingForResponse = false;
        this.list.removeAllViews();
        if (entries == null || entries.length == 0) {
            this.scroller.setVisibility(View.GONE);
            this.stateView.showEmpty(R.string.online_top_empty, 0);
            return;
        }

        this.stateView.hideState();
        this.scroller.setAlpha(0.0f);
        this.scroller.setVisibility(View.VISIBLE);
        this.scroller.animate().alpha(1.0f).setDuration(220L).start();
        String currentName = currentName();
        this.list.addView(podiumCard(entries, currentName));

        for (TopEntry entry : entries) {
            if (entry != null && entry.rank > 3) {
                this.list.addView(rankedRow(entry, isCurrentUser(entry, currentName)),
                        OnlineUi.cardParams(this));
            }
        }
        this.scroller.scrollTo(0, 0);
    }

    private View podiumCard(TopEntry[] entries, String currentName) {
        LinearLayout card = OnlineUi.card(this);
        card.addView(OnlineUi.sectionTitle(this, R.string.online_top_podium),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout podium = new LinearLayout(this);
        podium.setOrientation(LinearLayout.HORIZONTAL);
        podium.setGravity(Gravity.BOTTOM);
        LinearLayout.LayoutParams podiumParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        podiumParams.topMargin = dp(4);
        card.addView(podium, podiumParams);

        addPodiumSlot(podium, findRank(entries, 2), currentName, false);
        addPodiumSlot(podium, findRank(entries, 1), currentName, true);
        addPodiumSlot(podium, findRank(entries, 3), currentName, false);
        return card;
    }

    private void addPodiumSlot(LinearLayout podium, TopEntry entry,
                               String currentName, boolean winner) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        params.leftMargin = dp(3);
        params.rightMargin = dp(3);
        if (!winner) {
            params.topMargin = dp(18);
        }

        if (entry == null) {
            View spacer = new View(this);
            spacer.setVisibility(View.INVISIBLE);
            spacer.setMinimumHeight(dp(166));
            podium.addView(spacer, params);
            return;
        }

        boolean current = isCurrentUser(entry, currentName);
        LinearLayout slot = new LinearLayout(this);
        slot.setOrientation(LinearLayout.VERTICAL);
        slot.setGravity(Gravity.CENTER_HORIZONTAL);
        slot.setPadding(dp(5), dp(9), dp(5), dp(10));
        if (current) {
            slot.setBackground(OnlineUi.roundedBackground(this, R.color.online_primary_soft, 16));
        }

        ImageView crown = new ImageView(this);
        crown.setImageResource(R.drawable.ic_crown_new);
        crown.setColorFilter(rankColor(entry.rank));
        int crownSize = winner ? dp(30) : dp(24);
        slot.addView(crown, new LinearLayout.LayoutParams(crownSize, crownSize));

        OnlineUi.AvatarBlock avatar = OnlineUi.avatar(this, winner ? 68 : 58, false);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(
                winner ? dp(68) : dp(58), winner ? dp(68) : dp(58));
        avatarParams.topMargin = dp(5);
        avatarParams.gravity = Gravity.CENTER_HORIZONTAL;
        slot.addView(avatar.view, avatarParams);

        TextView rank = OnlineUi.text(this,
                String.format(Locale.getDefault(), "#%d", entry.rank), 12.0f,
                R.color.online_surface, OnlineUi.bold(this));
        rank.setGravity(Gravity.CENTER);
        rank.setBackground(OnlineUi.roundedBackground(this, rankColorRes(entry.rank), 50));
        rank.setPadding(dp(9), 0, dp(9), 0);
        rank.setMinHeight(dp(23));
        LinearLayout.LayoutParams rankParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(23));
        rankParams.topMargin = dp(-8);
        rankParams.gravity = Gravity.CENTER_HORIZONTAL;
        slot.addView(rank, rankParams);

        TextView name = OnlineUi.text(this, safeName(entry.nickname), 14.0f,
                R.color.online_text_primary, OnlineUi.medium(this));
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setMinHeight(dp(38));
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = dp(6);
        slot.addView(name, nameParams);

        TextView elo = OnlineUi.text(this, String.valueOf(entry.elo), 17.0f,
                R.color.online_accent, OnlineUi.bold(this));
        elo.setGravity(Gravity.CENTER);
        slot.addView(elo, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView marker = OnlineUi.chip(this, getString(R.string.online_current_player));
        marker.setVisibility(current ? View.VISIBLE : View.INVISIBLE);
        LinearLayout.LayoutParams markerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, OnlineUi.dim(this, R.dimen.online_chip_height));
        markerParams.topMargin = dp(5);
        markerParams.gravity = Gravity.CENTER_HORIZONTAL;
        slot.addView(marker, markerParams);
        podium.addView(slot, params);
    }

    private View rankedRow(TopEntry entry, boolean current) {
        LinearLayout row = current ? OnlineUi.emphasisCard(this) : OnlineUi.card(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(72));

        TextView rank = OnlineUi.text(this, String.valueOf(entry.rank), 17.0f,
                current ? R.color.online_primary : R.color.online_text_secondary,
                OnlineUi.bold(this));
        rank.setGravity(Gravity.CENTER);
        row.addView(rank, new LinearLayout.LayoutParams(dp(38), dp(44)));

        OnlineUi.AvatarBlock avatar = OnlineUi.avatar(this, 44, false);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        avatarParams.leftMargin = dp(4);
        avatarParams.rightMargin = dp(12);
        row.addView(avatar.view, avatarParams);

        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.VERTICAL);
        identity.setGravity(Gravity.CENTER_VERTICAL);
        TextView nickname = OnlineUi.text(this, safeName(entry.nickname), 16.0f,
                R.color.online_text_primary, current ? OnlineUi.medium(this) : OnlineUi.regular(this));
        nickname.setMaxLines(2);
        nickname.setEllipsize(TextUtils.TruncateAt.END);
        identity.addView(nickname, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (current) {
            TextView you = OnlineUi.text(this, getString(R.string.online_current_player), 12.0f,
                    R.color.online_primary, OnlineUi.medium(this));
            LinearLayout.LayoutParams youParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            youParams.topMargin = dp(2);
            identity.addView(you, youParams);
        }
        row.addView(identity, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        LinearLayout rating = new LinearLayout(this);
        rating.setOrientation(LinearLayout.VERTICAL);
        rating.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        TextView elo = OnlineUi.text(this, String.valueOf(entry.elo), 18.0f,
                R.color.online_accent, OnlineUi.bold(this));
        elo.setGravity(Gravity.END);
        rating.addView(elo, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView eloLabel = OnlineUi.text(this, getString(R.string.online_elo), 11.0f,
                R.color.online_text_secondary, OnlineUi.medium(this));
        eloLabel.setGravity(Gravity.END);
        rating.addView(eloLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams ratingParams = new LinearLayout.LayoutParams(
                dp(70), ViewGroup.LayoutParams.WRAP_CONTENT);
        ratingParams.leftMargin = dp(8);
        row.addView(rating, ratingParams);
        return row;
    }

    private TopEntry findRank(TopEntry[] entries, int rank) {
        for (TopEntry entry : entries) {
            if (entry != null && entry.rank == rank) {
                return entry;
            }
        }
        return null;
    }

    private boolean isCurrentUser(TopEntry entry, String currentName) {
        OnlineMessages.AuthOk profile=OnlineSession.get().getLastProfile();
        return entry != null && profile != null && entry.userId > 0 && entry.userId == profile.userId;
    }

    private String currentName() {
        OnlineMessages.AuthOk profile = OnlineSession.get().getLastProfile();
        return profile != null ? profile.nickname : OnlineIdentity.getNickname(this);
    }

    private String safeName(String name) {
        return TextUtils.isEmpty(name) ? "—" : name.trim();
    }

    private int rankColor(int rank) {
        return OnlineUi.color(this, rankColorRes(rank));
    }

    private int rankColorRes(int rank) {
        if (rank == 1) {
            return R.color.online_rank_gold;
        }
        if (rank == 2) {
            return R.color.online_rank_silver;
        }
        return R.color.online_rank_bronze;
    }

    // ------------------------------------------------------ listener stubs

    @Override
    public void onTop(final TopEntry[] entries) {
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
            requestTop();
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
    public void onMatchCancelled(boolean acceptedMatch, String mode) {
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
    public void onHistory(OnlineMessages.HistoryEntry[] entries) {
    }

    @Override
    public void onOpponentDisconnected() {
    }

    @Override
    public void onOpponentReconnected() {
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
