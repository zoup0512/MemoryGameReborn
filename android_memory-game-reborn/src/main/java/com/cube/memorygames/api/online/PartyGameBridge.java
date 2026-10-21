package com.cube.memorygames.api.online;

import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.cube.memorygames.games.Game1MemoryGridActivity;
import com.memory.brain.training.games.R;

/** Holds initialized six-game grids behind the authoritative loading/countdown barrier. */
public final class PartyGameBridge implements PartySession.Listener {
    private final Game1MemoryGridActivity activity;
    private final PartySession party = PartySession.get();
    private final Handler main = new Handler();
    private TextView veil;
    private boolean detached;
    private final Runnable tick = new Runnable() {
        @Override public void run() { update(); if (!detached) main.postDelayed(this, 100); }
    };
    public PartyGameBridge(Game1MemoryGridActivity activity) { this.activity = activity; }
    public void attach() {
        FrameLayout root = activity.findViewById(android.R.id.content);
        veil = OnlineUi.text(activity, activity.getString(R.string.party_loading), 24,
                R.color.online_text_primary, OnlineUi.medium(activity));
        veil.setGravity(Gravity.CENTER); veil.setBackgroundColor(OnlineUi.color(activity, R.color.online_page));
        veil.setClickable(true); veil.setFocusable(true);
        root.addView(veil, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        party.addListener(this);
        // post after child onCreate and the first layout, including Schulte's deferred grid insertion.
        root.post(() -> root.post(() -> { if (!detached && !activity.isFinishing()) { party.loaded(activity.getIntent()); update(); } }));
        main.post(tick);
    }
    @Override public void onPartyChanged() { update(); }
    private void update() {
        if (detached || activity.isFinishing()) return;
        PartyState s = party.state();
        if (s == null || !party.mayPlay(activity.getIntent()) || (s.is("PLAYING") && party.remaining() <= 0)) {
            activity.finish(); return;
        }
        if (party.playing(activity.getIntent())) {
            veil.setVisibility(View.GONE); activity.startPartyClock();
        } else {
            veil.setVisibility(View.VISIBLE);
            if (s.is("COUNTDOWN")) veil.setText(activity.getString(R.string.party_countdown,
                    Math.max(1, (s.startAt - party.now() + 999) / 1000)));
            else veil.setText(R.string.party_loading);
        }
    }
    public void detach() { detached = true; main.removeCallbacksAndMessages(null); party.removeListener(this); }
}
