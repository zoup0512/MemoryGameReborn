package com.cube.memorygames.api.online;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cube.memorygames.api.online.OnlineMessages.HistoryEntry;
import com.memory.brain.training.games.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Online match history (PRD F-11): the last 20 matches as a simple
 * programmatic list, styled like the rest of the PK surfaces.
 */
public class OnlineHistoryActivity extends AppCompatActivity implements OnlineSession.MatchListener {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout list;
    private Typeface fontLight;
    private boolean requested;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        OnlineSession.get().init(getApplicationContext());
        this.fontLight = Typeface.createFromAsset(getAssets(), "Roboto-Light.ttf");
        setContentView(buildView());
        OnlineSession.get().setMatchListener(this);
        requestWhenReady();
    }

    @Override
    protected void onDestroy() {
        this.handler.removeCallbacksAndMessages(null);
        OnlineSession.get().setMatchListener(null);
        super.onDestroy();
    }

    private void requestWhenReady() {
        if (OnlineSession.get().getState() == OnlineSession.STATE_DISCONNECTED) {
            OnlineSession.get().connect();
        }
        this.handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                int state = OnlineSession.get().getState();
                if (state == OnlineSession.STATE_IDLE) {
                    OnlineSession.get().requestHistory();
                    requested = true;
                } else if (!requested) {
                    handler.postDelayed(this, 1000L);
                }
            }
        }, 800L);
    }

    private View buildView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.background));
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = new TextView(this);
        title.setText(R.string.title_online_history);
        title.setTypeface(this.fontLight, Typeface.BOLD);
        title.setTextSize(25.0f);
        title.setTextColor(ContextCompat.getColor(this, R.color.grid_correct));
        title.setGravity(Gravity.CENTER);
        int pad = dp(16);
        title.setPadding(pad, dp(40), pad, dp(16));
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        this.list = new LinearLayout(this);
        this.list.setOrientation(LinearLayout.VERTICAL);
        this.list.setPadding(pad, 0, pad, dp(16));
        root.addView(this.list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView empty = new TextView(this);
        empty.setId(R.id.online_history_empty);
        empty.setText(R.string.online_history_empty);
        empty.setTypeface(this.fontLight);
        empty.setTextSize(15.0f);
        empty.setTextColor(ContextCompat.getColor(this, R.color.onlineTextHint));
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, dp(30), 0, 0);
        this.list.addView(empty, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }

    private void render(HistoryEntry[] entries) {
        this.list.removeAllViews();
        if (entries == null || entries.length == 0) {
            TextView empty = new TextView(this);
            empty.setText(R.string.online_history_empty);
            empty.setTypeface(this.fontLight);
            empty.setTextSize(15.0f);
            empty.setTextColor(ContextCompat.getColor(this, R.color.onlineTextHint));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(30), 0, 0);
            this.list.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return;
        }
        SimpleDateFormat format = new SimpleDateFormat("MM-dd HH:mm", Locale.US);
        for (HistoryEntry entry : entries) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.drawable_state_cell_empty);
            int pad = dp(10);
            row.setPadding(pad, pad, pad, pad);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.topMargin = dp(6);
            this.list.addView(row, rowParams);

            TextView result = new TextView(this);
            result.setTypeface(this.fontLight, Typeface.BOLD);
            result.setTextSize(20.0f);
            boolean win = OnlineSession.RESULT_WIN.equals(entry.result);
            boolean draw = OnlineSession.RESULT_DRAW.equals(entry.result);
            result.setText(win ? "W" : draw ? "D" : "L");
            result.setTextColor(win ? ContextCompat.getColor(this, R.color.onlineHistoryWin)
                    : draw ? ContextCompat.getColor(this, R.color.onlineTextHint)
                    : ContextCompat.getColor(this, R.color.onlineHistoryLose));
            result.setGravity(Gravity.CENTER);
            row.addView(result, new LinearLayout.LayoutParams(dp(36), ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout middle = new LinearLayout(this);
            middle.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams middleParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            row.addView(middle, middleParams);
            TextView opponent = new TextView(this);
            opponent.setTypeface(this.fontLight, Typeface.BOLD);
            opponent.setTextSize(16.0f);
            opponent.setTextColor(Color.BLACK);
            opponent.setText(entry.vsBot ? entry.opponent + " (bot)"
                    : entry.opponent + ("FRIEND".equals(entry.mode) ? "  ·  " + getString(R.string.online_friend_tag) : ""));
            middle.addView(opponent, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView when = new TextView(this);
            when.setTypeface(this.fontLight);
            when.setTextSize(12.0f);
            when.setTextColor(ContextCompat.getColor(this, R.color.onlineTextHint));
            when.setText(format.format(new Date(entry.ts)));
            middle.addView(when, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView score = new TextView(this);
            score.setTypeface(this.fontLight, Typeface.BOLD);
            score.setTextSize(16.0f);
            score.setTextColor(ContextCompat.getColor(this, R.color.onlineStatsColor));
            score.setText(String.format(Locale.US, "%d : %d", entry.myScore, entry.oppScore));
            row.addView(score, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
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
        if (state == OnlineSession.STATE_IDLE && !this.requested) {
            OnlineSession.get().requestHistory();
            this.requested = true;
        }
    }

    @Override
    public void onAuthOk(OnlineMessages.AuthOk profile) {
    }

    @Override
    public void onMatchFound(OnlineMessages.MatchFound match) {
    }

    @Override
    public void onMatchCancelled() {
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
    public void onError(int code, String message) {
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
