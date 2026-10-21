package com.cube.memorygames.api.online;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.cube.memorygames.Games;
import com.cube.memorygames.MainMenuActivity;
import com.cube.memorygames.model.GameInfo;
import com.memory.brain.training.games.R;

/** Persistent 3–6 seat friend lobby, round board and retained final results. */
public final class PartyActivity extends android.support.v7.app.AppCompatActivity implements PartySession.Listener {
    private final PartySession party = PartySession.get();
    private final Handler main = new Handler();
    private LinearLayout box;
    private ScrollView scroll;
    private TextView countdown;
    private boolean resumed, gameOpen, exitRequested, returnRequested;
    private String launchedKey;
    private int rounds = 5;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            PartyState s = party.state();
            if (countdown != null && s != null) {
                long end = s.is("COUNTDOWN") ? s.startAt : s.is("PLAYING") ? s.deadlineAt : s.phaseDeadlineAt;
                countdown.setText(getString(R.string.party_seconds, Math.max(0, (end - party.now() + 999) / 1000)));
            }
            if (resumed) main.postDelayed(this, 200);
        }
    };
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved); OnlineSession.get().init(this);
        if (saved != null) { launchedKey = saved.getString("launched"); rounds = saved.getInt("rounds", 5); }
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.addView(OnlineUi.toolbar(this, R.string.party_title, v -> onBackPressed()).view,
                new LinearLayout.LayoutParams(-1, OnlineUi.dp(this, 56)));
        scroll = new ScrollView(this); scroll.setFillViewport(true);
        box = OnlineUi.page(this); scroll.addView(box); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root); party.addListener(this); render();
    }
    @Override protected void onResume() { super.onResume(); resumed = true; party.sync(); render(); main.post(tick); }
    @Override protected void onPause() { resumed = false; main.removeCallbacks(tick); super.onPause(); }
    @Override protected void onDestroy() { party.removeListener(this); main.removeCallbacksAndMessages(null); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle b) { super.onSaveInstanceState(b); b.putString("launched", launchedKey); b.putInt("rounds", rounds); }
    @Override public void onPartyChanged() {
        if (exitRequested && party.state() == null && !party.occupied()) { finish(); return; }
        render();
    }
    private void text(int res) { box.addView(PartyUi.text(this, getString(res))); }
    private void text(String s) { box.addView(PartyUi.text(this, s)); }
    private TextView action(int res, Runnable run) {
        TextView v = OnlineUi.secondaryButton(this, res); v.setSingleLine(false);
        box.addView(v, OnlineUi.cardParams(this)); v.setOnClickListener(w -> run.run()); return v;
    }
    private void render() {
        if (box == null || isFinishing()) return;
        int y = scroll.getScrollY(); box.removeAllViews(); countdown = null;
        if (!party.connected()) { text(R.string.party_disconnected); action(R.string.party_reconnect, () -> party.sync()); }
        if (party.error() != null) text(PartyUi.error(this, party.error()));
        PartyState s = party.state(); PartyState result = party.finished();
        // Other players may already have returned/reconfigured. Keep this player's result until explicit acknowledgement.
        if (result != null && !returnRequested && (s == null || s.is("FINISHED") || s.is("LOBBY"))) {
            finalBoard(result); scroll.post(() -> scroll.scrollTo(0, y)); return;
        }
        if (s == null) {
            text(R.string.party_rules);
            if (!party.occupied()) {
                action(rounds == 3 ? R.string.party_rounds_three : R.string.party_rounds_five,
                        () -> { rounds = rounds == 3 ? 5 : 3; render(); });
                action(R.string.party_create, () -> { returnRequested = false; party.dismissFinished(); party.create(rounds); });
                EditText code = new EditText(this); code.setHint(R.string.party_code_hint);
                code.setInputType(InputType.TYPE_CLASS_NUMBER); code.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
                box.addView(code, new LinearLayout.LayoutParams(-1, -2));
                action(R.string.party_join, () -> {
                    String value = code.getText().toString().trim();
                    if (!value.matches("[0-9]{6}")) { code.setError(getString(R.string.party_code_invalid)); return; }
                    returnRequested = false; party.dismissFinished(); party.join(value);
                });
            } else text(R.string.party_waiting);
            action(R.string.party_history, () -> startActivity(new Intent(this, PartyHistoryActivity.class)));
            return;
        }
        text(getString(R.string.party_room, s.code, s.members == null ? 0 : s.members.length));
        action(R.string.party_copy, () -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.party_title), s.code));
            Toast.makeText(this, R.string.party_copied, Toast.LENGTH_SHORT).show();
        });
        if (party.leaving()) { text(R.string.party_leaving); action(R.string.party_reconnect, () -> party.sync()); return; }
        if (s.is("LOBBY")) {
            returnRequested = false;
            text(getString(R.string.party_rounds_label, s.roundCount));
            boolean host = s.hostId == party.userId();
            if (host) action(s.roundCount == 3 ? R.string.party_rounds_three : R.string.party_rounds_five,
                    () -> party.config(s.roundCount == 3 ? 5 : 3));
            if (s.members != null) for (PartyState.Member p : s.members) {
                LinearLayout row = OnlineUi.card(this); box.addView(row, OnlineUi.cardParams(this));
                OnlineUi.AvatarBlock avatar = OnlineUi.avatar(this, 40, false); OnlineUi.applyAvatar(this, avatar, p.avatarValue); row.addView(avatar.view);
                row.addView(PartyUi.text(this, getString(R.string.party_member, p.nickname,
                        getString(p.playerId == s.hostId ? R.string.party_host : p.ready ? R.string.party_ready : R.string.party_not_ready),
                        p.connected ? "" : getString(R.string.party_offline))));
                if (host && p.playerId != party.userId()) {
                    TextView kick = OnlineUi.textButton(this, R.string.party_kick); row.addView(kick);
                    kick.setOnClickListener(v -> com.cube.memorygames.ui.DialogUi.builder(this).setTitle(R.string.party_kick)
                            .setMessage(p.nickname).setPositiveButton(R.string.ok, (d, w) -> party.memberAction(p.playerId, false))
                            .setNegativeButton(R.string.cancel, null).show());
                    if (p.connected) {
                        TextView transfer = OnlineUi.textButton(this, R.string.party_transfer); row.addView(transfer);
                        transfer.setOnClickListener(v -> com.cube.memorygames.ui.DialogUi.builder(this).setTitle(R.string.party_transfer)
                                .setMessage(p.nickname).setPositiveButton(R.string.ok, (d, w) -> party.memberAction(p.playerId, true))
                                .setNegativeButton(R.string.cancel, null).show());
                    }
                }
            }
            if (host) { TextView start = action(R.string.party_start, () -> party.start()); start.setEnabled(s.canStart(party.userId()) && party.connected()); text(R.string.party_start_rule); }
            else { PartyState.Member me = s.member(party.userId()); if (me != null) action(me.ready ? R.string.party_unready : R.string.party_ready, () -> party.ready(!me.ready)); }
        } else if (s.is("FINISHED")) finalBoard(s);
        else {
            text(getString(R.string.party_round_header, s.roundIndex + 1, s.roundCount,
                    s.round() == null ? "" : PartyUi.game(this, s.round().gameId)));
            text(s.is("LOADING") ? R.string.party_loading : s.is("COUNTDOWN") ? R.string.party_starting
                    : s.is("ROUND_RESULT") ? R.string.party_round_result : R.string.party_playing);
            countdown = PartyUi.text(this, ""); box.addView(countdown);
            PartyUi.standings(this, box, s);
            if (s.is("ROUND_RESULT")) PartyUi.results(this, box, s);
            if (s.is("LOADING")) tryLaunch(s);
            if (!gameOpen && (s.is("PLAYING") || s.is("COUNTDOWN"))) text(R.string.party_resume_wait);
        }
        if (!s.is("FINISHED")) action(R.string.party_leave, () -> onBackPressed());
        scroll.post(() -> scroll.scrollTo(0, y));
    }
    private void finalBoard(PartyState s) {
        text("COMPLETED".equals(s.status) ? R.string.party_final : R.string.party_interrupted);
        if (s.reason != null) text(PartyUi.error(this, s.reason));
        PartyUi.standings(this, box, s); PartyUi.results(this, box, s);
        action(R.string.party_return, () -> { returnRequested = true; party.dismissFinished(); party.returnToLobby(s); render(); });
        action(R.string.party_history, () -> startActivity(new Intent(this, PartyHistoryActivity.class)));
        action(R.string.party_leave, () -> onBackPressed());
    }
    private void tryLaunch(PartyState s) {
        if (!resumed || gameOpen || PartySession.key(s).equals(launchedKey)) return;
        GameInfo info = s.round() == null ? null : Games.get().getGameBiId(s.round().gameId);
        if (info == null) { text(R.string.party_unsupported); return; }
        String token = party.claim();
        if (token == null) { text(R.string.party_resume_wait); return; }
        launchedKey = PartySession.key(s); gameOpen = true;
        Intent intent = new Intent(this, Games.get().getGameActivity(info));
        intent.putExtra(MainMenuActivity.EXTRA_GAME_INFO, info); intent.putExtra(OnlineSession.EXTRA_ONLINE_GAME, true);
        intent.putExtra(OnlineSession.EXTRA_ONLINE_SEED, s.round().seed); party.tag(intent, token);
        startActivityForResult(intent, 701);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data); if (request == 701) { gameOpen = false; render(); }
    }
    @Override public void onBackPressed() {
        if (party.state() == null) { if (party.occupied()) { text(R.string.party_waiting); return; } finish(); return; }
        com.cube.memorygames.ui.DialogUi.builder(this).setTitle(R.string.party_leave).setMessage(R.string.party_leave_confirm)
                .setPositiveButton(R.string.ok, (d, w) -> { exitRequested = true; party.dismissFinished(); party.leave(); })
                .setNegativeButton(R.string.cancel, null).show();
    }
}
