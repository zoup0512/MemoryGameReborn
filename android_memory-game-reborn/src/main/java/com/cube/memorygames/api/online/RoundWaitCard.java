package com.cube.memorygames.api.online;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.cube.memorygames.model.GameInfo;
import com.memory.brain.training.games.R;

/** Presentation only; server-backed session clocks own all round transitions. */
final class RoundWaitCard {
    final LinearLayout view;
    private final Context context;
    private final TextView round, title, score, number, status, hint, gameName;
    private final LinearLayout next;
    private final ImageView gameIcon;
    private final ProgressBar busy;
    private int lastStatus;
    private long lastSeconds = Long.MIN_VALUE;

    RoundWaitCard(Context context) {
        this.context = context;
        view = OnlineUi.card(context);
        view.setGravity(Gravity.CENTER_HORIZONTAL);
        round = OnlineUi.chip(context, "");
        add(round, 0);
        title = label(context.getString(R.string.round_wait_title), 24, R.color.online_text_primary);
        title.setTypeface(OnlineUi.bold(context));
        add(title, 14);
        score = label("", 15, R.color.online_text_secondary);
        add(score, 8);

        number = label("", 46, R.color.online_primary);
        number.setTypeface(OnlineUi.bold(context));
        number.setBackgroundResource(R.drawable.online_score_pill);
        number.setPadding(dp(24), dp(8), dp(24), dp(8));
        number.setMinWidth(dp(104));
        add(number, 20);
        busy = new ProgressBar(context);
        LinearLayout.LayoutParams busyParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        busyParams.gravity = Gravity.CENTER_HORIZONTAL;
        busyParams.topMargin = dp(20);
        view.addView(busy, busyParams);
        status = label("", 17, R.color.online_text_primary);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(status, 12);

        next = OnlineUi.emphasisCard(context);
        next.setElevation(0);
        next.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView nextLabel = label(context.getString(R.string.round_wait_next), 12, R.color.online_text_secondary);
        next.addView(nextLabel, new LinearLayout.LayoutParams(-1, -2));
        gameIcon = new ImageView(context);
        gameIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        gameIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        iconParams.topMargin = dp(8);
        next.addView(gameIcon, iconParams);
        gameName = label("", 20, R.color.online_text_primary);
        gameName.setTypeface(OnlineUi.medium(context));
        next.addView(gameName, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(-1, -2);
        nextParams.topMargin = dp(20);
        view.addView(next, nextParams);
        hint = label(context.getString(R.string.round_wait_auto), 13, R.color.online_text_secondary);
        add(hint, 14);
    }

    void bind(int roundNumber, int total, GameInfo nextGame, String scoreSummary) {
        round.setText(context.getString(R.string.round_wait_round, Math.max(1, roundNumber), Math.max(1, total)));
        score.setText(scoreSummary);
        score.setVisibility(scoreSummary == null || scoreSummary.isEmpty() ? View.GONE : View.VISIBLE);
        next.setVisibility(nextGame == null ? View.GONE : View.VISIBLE);
        if (nextGame != null) {
            gameName.setText(nextGame.getGameNameRes());
            gameIcon.setImageResource(nextGame.getGameImageRes());
        }
    }

    void showState(RoundWaitState state) {
        int message = state.phase == RoundWaitState.Phase.CONFIRMING ? R.string.online_round_confirming
                : state.phase == RoundWaitState.Phase.SETTLING ? R.string.online_match_settling
                : state.phase == RoundWaitState.Phase.COUNTDOWN ? R.string.online_round_starts_in
                : R.string.online_round_waiting_start;
        showStatus(message, state.seconds);
    }

    void showStatus(int message, long seconds) {
        if (message == lastStatus && seconds == lastSeconds) return;
        lastStatus = message;
        lastSeconds = seconds;
        boolean counting = seconds > 0;
        number.setVisibility(counting ? View.VISIBLE : View.GONE);
        busy.setVisibility(counting ? View.GONE : View.VISIBLE);
        if (counting) number.setText(context.getString(R.string.round_wait_seconds, seconds));
        status.setText(counting ? context.getString(message, seconds) : context.getString(message));
        boolean settling = message == R.string.online_match_settling;
        title.setText(settling ? R.string.game_finished : R.string.round_wait_title);
        if (settling) next.setVisibility(View.GONE);
        hint.setVisibility(settling ? View.GONE : View.VISIBLE);
        hint.setText(counting ? R.string.round_wait_auto : R.string.round_wait_loading);
    }

    private TextView label(String text, int size, int color) {
        TextView label = OnlineUi.text(context, text, size, color, OnlineUi.regular(context));
        label.setGravity(Gravity.CENTER);
        return label;
    }

    private void add(View child, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.topMargin = dp(topMargin);
        view.addView(child, params);
    }

    private int dp(int value) { return OnlineUi.dp(context, value); }
}
