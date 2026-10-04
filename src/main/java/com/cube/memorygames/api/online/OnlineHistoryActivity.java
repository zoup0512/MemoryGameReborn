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
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.cube.memorygames.api.online.OnlineMessages.HistoryEntry;
import com.cube.memorygames.utils.DateUtils;
import com.memory.brain.training.games.R;

import java.util.Calendar;
import java.util.Locale;

/**
 * Online match history styled as the original activity_online_history.xml:
 * the white top bar (back arrow tinted grid_correct, 18sp title) over a
 * 1dip divider, then the item_online_history.xml rows — a date header when
 * the day changes, and each match as two mirrored halves with the 60dip
 * avatars (green ring on the winner, grey otherwise), 12sp names, 24sp
 * scores and the 28dip crown badge on the winning side.
 */
public class OnlineHistoryActivity extends AppCompatActivity implements OnlineSession.MatchListener {

    private static final String DATE_FORMAT = "EEEE, dd.MM.yy";

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
        // Same owner check as OnlineMatchActivity: the lobby's onResume
        // re-registration must survive this destroy.
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
        root.setBackgroundColor(Color.WHITE);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // activity_online_history.xml's white header: back arrow in
        // grid_correct over the 18sp title.
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(Color.WHITE);

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
        title.setText(R.string.title_online_history);
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
        String myName = myName();
        long lastDay = Long.MIN_VALUE;
        Calendar calendar = Calendar.getInstance();
        for (HistoryEntry entry : entries) {
            calendar.setTimeInMillis(entry.ts);
            long day = calendar.get(Calendar.YEAR) * 1000L + calendar.get(Calendar.DAY_OF_YEAR);
            if (day != lastDay) {
                this.list.addView(dateHeader(entry.ts));
                lastDay = day;
            }
            this.list.addView(matchRow(entry, myName));
        }
    }

    private TextView dateHeader(long ts) {
        TextView header = new TextView(this);
        header.setTypeface(this.fontLight);
        header.setTextSize(15.0f);
        header.setTextColor(Color.BLACK);
        header.setGravity(Gravity.CENTER);
        header.setPadding(0, dp(16), 0, dp(16));
        header.setText(DateUtils.getTodayYesterdayOrFormattedDateString(this, ts, DATE_FORMAT));
        return header;
    }

    /** One item_online_history.xml row: my half left, opponent's right. */
    private View matchRow(HistoryEntry entry, String myName) {
        boolean win = OnlineSession.RESULT_WIN.equals(entry.result);
        boolean draw = OnlineSession.RESULT_DRAW.equals(entry.result);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int margin = dp(16);
        int half = dp(8);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(margin, half, margin, half);
        row.setLayoutParams(rowParams);

        String opponentName = entry.opponent
                + (entry.vsBot ? " (bot)"
                        : ("FRIEND".equals(entry.mode) ? "  ·  " + getString(R.string.online_friend_tag) : ""));
        row.addView(playerHalf(myName, entry.myScore, win && !draw, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.5f));
        row.addView(new View(this), new LinearLayout.LayoutParams(dp(5), 1));
        row.addView(playerHalf(opponentName, entry.oppScore, !win && !draw, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.5f));
        return row;
    }

    /**
     * One side of the history row. item_online_history.xml: a 60dip avatar
     * whose ring turns green on the winner, the 12sp name beside it, the 24sp
     * score under the name and — winners only — the 28dip crown badge.
     */
    private RelativeLayout playerHalf(String name, int score, boolean winner, boolean mirrored) {
        RelativeLayout half = new RelativeLayout(this);

        ImageView avatar = new ImageView(this);
        avatar.setId(View.generateViewId());
        avatar.setImageResource(R.drawable.ic_user_placeholder);
        avatar.setBackgroundResource(winner ? R.drawable.drawable_circle_green
                : R.drawable.drawable_circle_grey);
        avatar.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int ring = dp(1);
        avatar.setPadding(ring, ring, ring, ring);
        RelativeLayout.LayoutParams avatarParams = new RelativeLayout.LayoutParams(dp(60), dp(60));
        if (mirrored) {
            avatarParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        }
        half.addView(avatar, avatarParams);

        TextView nameView = new TextView(this);
        nameView.setId(View.generateViewId());
        nameView.setTypeface(this.fontLight);
        nameView.setTextSize(12.0f);
        nameView.setTextColor(Color.BLACK);
        nameView.setMaxLines(1);
        nameView.setText(name);
        RelativeLayout.LayoutParams nameParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = dp(4);
        if (mirrored) {
            nameParams.addRule(RelativeLayout.LEFT_OF, avatar.getId());
            nameParams.rightMargin = dp(8);
        } else {
            nameParams.addRule(RelativeLayout.RIGHT_OF, avatar.getId());
            nameParams.leftMargin = dp(8);
        }
        half.addView(nameView, nameParams);

        TextView scoreView = new TextView(this);
        scoreView.setTypeface(this.fontLight);
        scoreView.setTextSize(24.0f);
        scoreView.setTextColor(ContextCompat.getColor(this, R.color.onlineStatsColor));
        scoreView.setText(String.format(Locale.US, "%d", score));
        RelativeLayout.LayoutParams scoreParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scoreParams.topMargin = dp(8);
        if (mirrored) {
            scoreParams.addRule(RelativeLayout.LEFT_OF, avatar.getId());
            scoreParams.addRule(RelativeLayout.BELOW, nameView.getId());
            scoreParams.rightMargin = dp(8);
        } else {
            scoreParams.addRule(RelativeLayout.RIGHT_OF, avatar.getId());
            scoreParams.addRule(RelativeLayout.BELOW, nameView.getId());
            scoreParams.leftMargin = dp(8);
        }
        half.addView(scoreView, scoreParams);

        if (winner) {
            ImageView badge = new ImageView(this);
            badge.setImageResource(R.drawable.ic_crown_new);
            badge.setColorFilter(Color.WHITE);
            badge.setBackgroundResource(R.drawable.drawable_circle_online_rating);
            int pad5 = dp(5);
            badge.setPadding(pad5, pad5, pad5, pad5);
            badge.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            RelativeLayout.LayoutParams badgeParams = new RelativeLayout.LayoutParams(dp(28), dp(28));
            badgeParams.topMargin = dp(10);
            if (mirrored) {
                badgeParams.addRule(RelativeLayout.LEFT_OF, scoreView.getId());
                badgeParams.addRule(RelativeLayout.BELOW, nameView.getId());
                badgeParams.rightMargin = dp(8);
            } else {
                badgeParams.addRule(RelativeLayout.RIGHT_OF, scoreView.getId());
                badgeParams.addRule(RelativeLayout.BELOW, nameView.getId());
                badgeParams.leftMargin = dp(8);
            }
            half.addView(badge, badgeParams);
        }
        return half;
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
    public void onTop(OnlineMessages.TopEntry[] entries) {
    }

    @Override
    public void onError(int code, String message) {
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
