package com.cube.memorygames.api.online;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import com.memory.brain.training.games.R;
import java.text.DateFormat;
import java.util.Date;

/** Server-owned independent party history, never mixed with duel/economy records. */
public final class PartyHistoryActivity extends android.support.v7.app.AppCompatActivity implements PartySession.Listener {
    private LinearLayout box;
    private final PartySession party = PartySession.get();
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved); OnlineSession.get().init(this);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.addView(OnlineUi.toolbar(this, R.string.party_history, v -> finish()).view,
                new LinearLayout.LayoutParams(-1, OnlineUi.dp(this, 56)));
        ScrollView scroll = new ScrollView(this); box = OnlineUi.page(this); scroll.addView(box);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); setContentView(root);
        party.addListener(this); render();
    }
    @Override protected void onResume() { super.onResume(); party.sync(); party.requestHistory(); }
    @Override protected void onDestroy() { party.removeListener(this); super.onDestroy(); }
    @Override public void onPartyChanged() { render(); }
    private void render() {
        box.removeAllViews();
        if (party.error() != null) box.addView(PartyUi.text(this, PartyUi.error(this, party.error())));
        android.widget.TextView refresh = OnlineUi.secondaryButton(this, R.string.party_refresh);
        box.addView(refresh); refresh.setOnClickListener(v -> { party.sync(); party.requestHistory(); });
        box.addView(PartyUi.text(this, getString(R.string.party_history_note)));
        PartyState.History[] history = party.history();
        if (history.length == 0) box.addView(PartyUi.text(this, getString(R.string.party_history_empty)));
        for (PartyState.History entry : history) {
            LinearLayout card = OnlineUi.card(this); box.addView(card, OnlineUi.cardParams(this));
            card.addView(PartyUi.text(this, DateFormat.getDateTimeInstance().format(new Date(entry.ts))));
            card.addView(PartyUi.text(this, getString(R.string.party_history_row, entry.participantCount,
                    entry.roundCount, entry.rank > 0 ? Integer.toString(entry.rank) : "—",
                    PartyState.points(entry.points2), entry.firstPlaces,
                    getString("COMPLETED".equals(entry.status) ? R.string.party_completed : R.string.party_interrupted))));
        }
    }
}
