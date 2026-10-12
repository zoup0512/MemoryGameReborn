package com.cube.memorygames.api.online;

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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.cube.memorygames.api.online.OnlineMessages.TopEntry;
import com.memory.brain.training.games.R;

/**
 * TOP Online leaderboard, styled in the history screen's grammar (the white
 * 45dip top bar with the grid_correct back arrow over a hairline divider):
 * rank number, nickname and ELO per row, the crown badge riding the top
 * three. Fed by the server's top_request/top_response (top 20 by ELO).
 */
public class OnlineTopActivity extends AppCompatActivity implements OnlineSession.MatchListener {

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
        if (OnlineSession.get().getMatchListener() == this) {
            OnlineSession.get().setMatchListener(null);
        }
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
                    OnlineSession.get().requestTop();
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
        root.setBackgroundColor(Color.WHITE);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_arrow_back_white);
        back.setColorFilter(ContextCompat.getColor(this, R.color.grid_correct));
        back.setPadding(dp(8), dp(8), dp(8), dp(8));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(dp(45), dp(45));
        backParams.leftMargin = dp(8);
        backParams.rightMargin = dp(8);
        header.addView(back, backParams);

        TextView title = new TextView(this);
        title.setText(R.string.online_top_title);
        title.setTypeface(this.fontLight);
        title.setTextSize(18.0f);
        title.setTextColor(ContextCompat.getColor(this, R.color.grid_correct));
        title.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(45)));

        View divider = new View(this);
        divider.setBackgroundColor(ContextCompat.getColor(this, R.color.grid_stroke));
        root.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));

        ScrollView scroller = new ScrollView(this);
        this.list = new LinearLayout(this);
        this.list.setOrientation(LinearLayout.VERTICAL);
        scroller.addView(this.list, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        return root;
    }

    private void render(TopEntry[] entries) {
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
        for (TopEntry entry : entries) {
            addRow(entry);
        }
    }

    private void addRow(TopEntry entry) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(12);
        row.setPadding(pad, dp(6), pad, dp(6));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        TextView rank = new TextView(this);
        rank.setTypeface(this.fontLight, Typeface.BOLD);
        rank.setTextSize(18.0f);
        rank.setTextColor(ContextCompat.getColor(this, R.color.onlineStatsColor));
        rank.setGravity(Gravity.CENTER);
        rank.setText(String.valueOf(entry.rank));
        row.addView(rank, new LinearLayout.LayoutParams(dp(40), ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView nickname = new TextView(this);
        nickname.setTypeface(this.fontLight);
        nickname.setTextSize(16.0f);
        nickname.setTextColor(Color.BLACK);
        nickname.setMaxLines(1);
        nickname.setText(entry.nickname);
        row.addView(nickname, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        if (entry.rank <= 3) {
            ImageView crown = new ImageView(this);
            crown.setImageResource(R.drawable.ic_crown_new);
            crown.setColorFilter(Color.WHITE);
            crown.setBackgroundResource(R.drawable.drawable_circle_online_rating);
            int pad4 = dp(4);
            crown.setPadding(pad4, pad4, pad4, pad4);
            LinearLayout.LayoutParams crownParams = new LinearLayout.LayoutParams(dp(26), dp(26));
            crownParams.rightMargin = dp(10);
            row.addView(crown, crownParams);
        }

        TextView elo = new TextView(this);
        elo.setTypeface(this.fontLight, Typeface.BOLD);
        elo.setTextSize(16.0f);
        elo.setTextColor(ContextCompat.getColor(this, R.color.onlineStatsColor));
        elo.setText(String.valueOf(entry.elo));
        row.addView(elo, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        this.list.addView(row, rowParams);
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
        if (state == OnlineSession.STATE_IDLE && !this.requested) {
            OnlineSession.get().requestTop();
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
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
