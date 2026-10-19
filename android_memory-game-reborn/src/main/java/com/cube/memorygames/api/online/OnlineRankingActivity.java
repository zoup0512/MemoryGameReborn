package com.cube.memorygames.api.online;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.memory.brain.training.games.R;

/** Personal ranking overview; detail screens own their online requests and listeners. */
public final class OnlineRankingActivity extends AppCompatActivity {

    private LinearLayout summaryCard;
    private TextView nickname;
    private TextView rating;
    private TextView ratingLabel;
    private TextView tier;
    private TextView season;
    private TextView snapshot;
    private LinearLayout stats;
    private TextView wins;
    private TextView losses;
    private TextView draws;
    private TextView winRate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildView());
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderSummary();
        AccountManager.get(this).cachedProfile(new AccountManager.Callback<AccountManager.UserProfile>() {
            @Override
            public void onSuccess(AccountManager.UserProfile profile) {
                refreshIfVisible();
            }

            @Override
            public void onError(String message) {
                refreshIfVisible();
            }
        });
    }

    private void refreshIfVisible() {
        if (!isFinishing() && !isDestroyed()) {
            renderSummary();
        }
    }

    private View buildView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(OnlineUi.color(this, R.color.online_page));

        OnlineUi.ToolbarBlock toolbar = OnlineUi.toolbar(this, R.string.rank, v -> finish());
        root.addView(toolbar.view, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, OnlineUi.dim(this, R.dimen.online_toolbar_height)));
        root.addView(OnlineUi.divider(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.setClipToPadding(false);
        root.addView(scroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        FrameLayout wrapper = new FrameLayout(this);
        LinearLayout page = OnlineUi.page(this);
        int contentWidth = Math.min(getResources().getDisplayMetrics().widthPixels,
                OnlineUi.dim(this, R.dimen.online_content_max_width));
        wrapper.addView(page, new FrameLayout.LayoutParams(contentWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        scroller.addView(wrapper, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        page.addView(OnlineUi.body(this, R.string.online_ranking_subtitle), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        buildSummary(page);

        TextView heading = OnlineUi.sectionTitle(this, R.string.online_shortcuts_title);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headingParams.topMargin = dp(24);
        page.addView(heading, headingParams);
        page.addView(navigationCard(R.id.online_top_link, R.drawable.ic_crown_new,
                R.string.online_ranking_top_title, R.string.online_ranking_top_hint,
                R.color.online_primary, R.color.online_primary_soft, OnlineTopActivity.class),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(navigationCard(R.id.online_history_link, R.drawable.ic_ranking_history,
                R.string.online_ranking_history_title, R.string.online_ranking_history_hint,
                R.color.online_accent, R.color.online_surface_alt, OnlineHistoryActivity.class),
                OnlineUi.cardParams(this));
        return root;
    }

    private void buildSummary(LinearLayout page) {
        this.summaryCard = OnlineUi.emphasisCard(this);
        this.summaryCard.setId(R.id.online_ranking_summary);
        this.summaryCard.addView(OnlineUi.sectionTitle(this, R.string.online_overview_title));

        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.HORIZONTAL);
        identity.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout values = new LinearLayout(this);
        values.setOrientation(LinearLayout.VERTICAL);
        this.nickname = OnlineUi.text(this, "", 14.0f,
                R.color.online_text_secondary, OnlineUi.medium(this));
        this.nickname.setSingleLine(true);
        this.nickname.setEllipsize(TextUtils.TruncateAt.END);
        values.addView(this.nickname, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout ratingRow = new LinearLayout(this);
        ratingRow.setGravity(Gravity.CENTER_VERTICAL);
        this.rating = OnlineUi.text(this, "—", 38.0f,
                R.color.online_accent, OnlineUi.bold(this));
        this.rating.setId(R.id.online_ranking_elo);
        ratingRow.addView(this.rating, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        this.ratingLabel = OnlineUi.text(this, getString(R.string.online_elo), 14.0f,
                R.color.online_text_secondary, OnlineUi.medium(this));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.leftMargin = dp(8);
        ratingRow.addView(this.ratingLabel, labelParams);
        values.addView(ratingRow);
        identity.addView(values, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        ImageView emblem = new ImageView(this);
        emblem.setImageResource(R.drawable.ic_crown_new);
        emblem.setColorFilter(OnlineUi.color(this, R.color.online_primary));
        emblem.setBackground(OnlineUi.roundedBackground(this, R.color.online_primary_soft, 18));
        emblem.setPadding(dp(14), dp(14), dp(14), dp(14));
        emblem.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams emblemParams = new LinearLayout.LayoutParams(dp(64), dp(64));
        emblemParams.leftMargin = dp(12);
        identity.addView(emblem, emblemParams);
        if (getResources().getConfiguration().fontScale > 1.3f) {
            emblem.setVisibility(View.GONE);
        }
        this.summaryCard.addView(identity);

        this.tier = OnlineUi.chip(this, "");
        this.tier.setId(R.id.online_ranking_tier);
        LinearLayout.LayoutParams tierParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tierParams.topMargin = dp(8);
        this.summaryCard.addView(this.tier, tierParams);

        this.season = OnlineUi.body(this, R.string.online_ranking_season_hint);
        this.season.setTextSize(13.0f);
        LinearLayout.LayoutParams seasonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        seasonParams.topMargin = dp(10);
        this.summaryCard.addView(this.season, seasonParams);

        this.stats = new LinearLayout(this);
        this.stats.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams statsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statsParams.topMargin = dp(14);
        this.summaryCard.addView(this.stats, statsParams);
        this.wins = statValue();
        this.losses = statValue();
        this.draws = statValue();
        this.winRate = statValue();
        arrangeStats(4);
        this.stats.addOnLayoutChangeListener((view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> {
            float fontScale = getResources().getConfiguration().fontScale;
            int count = OnlineRankingSummary.statColumns(right - left, dp(64), fontScale);
            if (this.stats.getChildCount() != 4 / count) {
                arrangeStats(count);
            }
        });

        this.snapshot = OnlineUi.text(this, getString(R.string.online_ranking_snapshot), 12.0f,
                R.color.online_text_secondary, OnlineUi.regular(this));
        LinearLayout.LayoutParams snapshotParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        snapshotParams.topMargin = dp(14);
        this.summaryCard.addView(this.snapshot, snapshotParams);
        page.addView(this.summaryCard, OnlineUi.cardParams(this));
    }

    private TextView statValue() {
        TextView value = OnlineUi.text(this, "—", 21.0f,
                R.color.online_text_primary, OnlineUi.bold(this));
        value.setGravity(Gravity.CENTER);
        return value;
    }

    private void arrangeStats(int columns) {
        this.stats.removeAllViews();
        TextView[] values = {this.wins, this.losses, this.draws, this.winRate};
        int[] labels = {R.string.online_title_wins, R.string.online_title_loses,
                R.string.online_title_draws, R.string.online_win_rate};
        LinearLayout row = null;
        for (int i = 0; i < values.length; i++) {
            if (values[i].getParent() instanceof ViewGroup) {
                ((ViewGroup) values[i].getParent()).removeView(values[i]);
            }
            if (i % columns == 0) {
                row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(12), 0, 0);
                this.stats.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            } else {
                row.addView(OnlineUi.divider(this), new LinearLayout.LayoutParams(dp(1), dp(40)));
            }
            LinearLayout column = new LinearLayout(this);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.CENTER_HORIZONTAL);
            column.addView(values[i], new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView label = OnlineUi.text(this, getString(labels[i]), 11.0f,
                    R.color.online_text_secondary, OnlineUi.regular(this));
            label.setGravity(Gravity.CENTER);
            column.addView(label, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(column, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        }
    }

    private View navigationCard(int id, int iconRes, int titleRes, int detailRes,
                                int colorRes, int backgroundRes, Class<?> destination) {
        FrameLayout action = new FrameLayout(this);
        action.setId(id);
        action.setClickable(true);
        action.setFocusable(true);
        action.setContentDescription(getString(titleRes) + ". " + getString(detailRes));
        TypedValue selectable = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground, selectable, true)) {
            action.setForeground(ContextCompat.getDrawable(this, selectable.resourceId));
        }
        LinearLayout row = OnlineUi.card(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(96));
        row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(OnlineUi.color(this, colorRes));
        icon.setBackground(OnlineUi.roundedBackground(this, backgroundRes, 14));
        icon.setPadding(dp(12), dp(12), dp(12), dp(12));
        row.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView title = OnlineUi.text(this, getString(titleRes), 18.0f,
                R.color.online_text_primary, OnlineUi.medium(this));
        labels.addView(title);
        TextView detail = OnlineUi.body(this, detailRes);
        detail.setTextSize(13.0f);
        detail.setPadding(0, dp(4), 0, 0);
        labels.addView(detail);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        labelsParams.leftMargin = dp(14);
        labelsParams.rightMargin = dp(8);
        row.addView(labels, labelsParams);
        TextView chevron = OnlineUi.text(this, "›", 26.0f,
                R.color.online_text_secondary, OnlineUi.regular(this));
        chevron.setGravity(Gravity.CENTER);
        row.addView(chevron, new LinearLayout.LayoutParams(dp(18), dp(32)));
        action.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        action.setOnClickListener(v -> startActivity(new Intent(this, destination)));
        return action;
    }

    private void renderSummary() {
        OnlineRankingSummary summary = OnlineRankingSummary.from(OnlineSession.get().getLastProfile(),
                AccountManager.get(this).profile());
        boolean available = summary != null;
        this.nickname.setText(available ? summary.nickname : getString(R.string.online_ranking_no_profile));
        this.rating.setText(available ? String.valueOf(summary.elo) : "—");
        this.rating.setContentDescription(available
                ? getString(R.string.online_ranking_rating_accessibility,
                        getString(R.string.online_elo), summary.elo) : null);
        this.ratingLabel.setVisibility(available ? View.VISIBLE : View.GONE);
        this.tier.setVisibility(available ? View.VISIBLE : View.GONE);
        this.stats.setVisibility(available ? View.VISIBLE : View.GONE);
        this.snapshot.setVisibility(available ? View.VISIBLE : View.GONE);
        if (!available) {
            this.season.setText(R.string.online_ranking_no_profile_hint);
            return;
        }
        int[] tiers = {R.string.online_rank_bronze, R.string.online_rank_silver,
                R.string.online_rank_gold, R.string.online_rank_platinum,
                R.string.online_rank_diamond, R.string.online_rank_master};
        this.tier.setText(summary.tier >= 0 && summary.tier < tiers.length
                ? tiers[summary.tier] : R.string.online_ranking_tier_unknown);
        this.season.setText(summary.hasSeason()
                ? getString(R.string.online_season_line, summary.seasonIndex,
                        summary.daysLeft(System.currentTimeMillis()))
                : getString(R.string.online_ranking_season_hint));
        this.wins.setText(String.valueOf(summary.wins));
        this.losses.setText(String.valueOf(summary.losses));
        this.draws.setText(String.valueOf(summary.draws));
        this.winRate.setText(getString(R.string.online_percent, summary.winRate()));
    }

    private int dp(int value) {
        return OnlineUi.dp(this, value);
    }
}
