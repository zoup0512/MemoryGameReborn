package com.cube.memorygames.api.online;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.cube.memorygames.Games;
import com.cube.memorygames.MainMenuActivity;
import com.cube.memorygames.model.GameInfo;
import com.memory.brain.training.games.R;

/** Unified friends room entry, persistent lobby, round board and retained final results. */
public final class PartyActivity extends androidx.appcompat.app.AppCompatActivity
        implements PartySession.Listener, OnlineSession.MatchListener {
    public static final String EXTRA_JOIN_CODE = "friendsJoinCode";
    public static final String EXTRA_ENTRY_ACTION = "friendsEntryAction";
    public static final String ENTRY_CREATE = "create";
    private boolean legacyJoinPending, createSetup;
    private OnlineSession.MatchListener previousMatchListener;
    private String entryError;
    private final PartySession party = PartySession.get();
    private final Handler main = new Handler();
    private LinearLayout box;
    private ScrollView scroll;
    private TextView countdown;
    private RoundWaitCard intermissionCard;
    private boolean resumed, gameOpen, exitRequested, returnRequested;
    private String launchedKey;
    private String renderedIntermissionKey;
    private int rounds = 5;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            PartyState s = party.state();
            if (intermissionCard != null && s != null && s.is("ROUND_RESULT")) {
                intermissionCard.showState(RoundWaitState.party(s.phaseDeadlineAt, party.now()));
            }
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
        setContentView(root); party.addListener(this);
        // A pending request belongs to this screen only when it was saved by this screen.
        legacyJoinPending = saved != null && saved.getBoolean("legacyJoinPending", false);
        createSetup = saved != null ? saved.getBoolean("createSetup", false)
                : ENTRY_CREATE.equals(getIntent().getStringExtra(EXTRA_ENTRY_ACTION));
        String code = getIntent().getStringExtra(EXTRA_JOIN_CODE);
        getIntent().removeExtra(EXTRA_JOIN_CODE); // A recreated Activity must never resend an entry.
        getIntent().removeExtra(EXTRA_ENTRY_ACTION);
        if (!guardOtherOnlineFlow()) return;
        if (legacyJoinPending && OnlineSession.get().hasPendingFriendRoom()) claimLegacyListener();
        if (saved == null && code != null) joinRoom(code);
        render();
    }
    @Override protected void onResume() {
        super.onResume(); resumed = true;
        if (isFinishing() || !guardOtherOnlineFlow()) return;
        OnlineSession session = OnlineSession.get();
        if (legacyJoinPending && session.isInMatch() && session.isFriendMode()) {
            openAcceptedMatch(); return;
        }
        if (legacyJoinPending && session.hasPendingFriendRoom()) claimLegacyListener();
        else { legacyJoinPending = false; releaseLegacyListener(); }
        party.sync(); render(); main.post(tick);
    }
    @Override protected void onPause() { resumed = false; main.removeCallbacks(tick); super.onPause(); }
    @Override protected void onDestroy() {
        party.removeListener(this); main.removeCallbacksAndMessages(null);
        releaseLegacyListener();
        super.onDestroy();
    }
    @Override protected void onSaveInstanceState(Bundle b) {
        super.onSaveInstanceState(b); b.putString("launched", launchedKey); b.putInt("rounds", rounds);
        b.putBoolean("legacyJoinPending", legacyJoinPending);
        b.putBoolean("createSetup", createSetup);
    }
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
        PartyState visible = party.state();
        String intermissionKey = visible != null && visible.is("ROUND_RESULT") ? PartySession.key(visible) : null;
        int y = intermissionKey != null && !intermissionKey.equals(renderedIntermissionKey) ? 0 : scroll.getScrollY();
        renderedIntermissionKey = intermissionKey;
        box.removeAllViews(); countdown = null; intermissionCard = null;
        if (OnlineSession.get().requiresAccountLogin()) {
            text(R.string.party_login_required);
            action(R.string.online_account_title, () -> startActivity(new Intent(this, AccountActivity.class)));
            return;
        }
        if (!party.connected()) { text(R.string.party_disconnected); action(R.string.party_reconnect, () -> party.sync()); }
        if (entryError != null) text(entryError);
        if (party.error() != null) text(PartyUi.error(this, party.error()));
        if (legacyJoinPending) {
            text(R.string.party_waiting);
            text(R.string.party_legacy_join_note);
            action(R.string.party_cancel_entry, () -> { cancelLegacyJoin(); render(); });
            return;
        }
        PartyState s = party.state(); PartyState result = party.finished();
        // Other players may already have returned/reconfigured. Keep this player's result until explicit acknowledgement.
        if (result != null && !returnRequested && !(createSetup && s == null)
                && (s == null || s.is("FINISHED") || s.is("LOBBY"))) {
            finalBoard(result); scroll.post(() -> scroll.scrollTo(0, y)); return;
        }
        if (s == null) {
            text(R.string.party_rules);
            if (!party.occupied()) {
                if (createSetup) {
                    text(R.string.party_create_setup);
                    text(R.string.party_create_setup_note);
                    action(rounds == 3 ? R.string.party_rounds_three : R.string.party_rounds_five,
                            () -> { rounds = rounds == 3 ? 5 : 3; render(); });
                    action(R.string.party_create_confirm, () -> {
                        if (!guardOtherOnlineFlow() || party.occupied()) return;
                        entryError = null; returnRequested = false;
                        party.dismissFinished(); createSetup = false; party.create(rounds);
                    });
                } else action(R.string.party_create, () -> { createSetup = true; render(); });
                action(R.string.party_join, () -> OnlineDialogs.showJoinRoomCode(this, this::joinRoom));
            } else if (party.canCancelEntry()) {
                text(R.string.party_connecting);
                action(R.string.party_cancel_entry, () -> party.cancelEntry());
            } else {
                text(R.string.party_waiting);
                if (party.connected()) action(R.string.party_reconnect, () -> party.sync());
            }
            action(R.string.party_history, () -> startActivity(new Intent(this, PartyHistoryActivity.class)));
            return;
        }
        if (!s.is("ROUND_RESULT")) {
            text(getString(R.string.party_room, s.code, s.members == null ? 0 : s.members.length));
            action(R.string.party_copy, () -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.party_title), s.code));
                Toast.makeText(this, R.string.party_copied, Toast.LENGTH_SHORT).show();
            });
        }
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
        else if (s.is("ROUND_RESULT")) {
            intermissionCard = new RoundWaitCard(this);
            String nextId = RoundWaitState.nextPartyGameId(s);
            GameInfo next = nextId == null ? null : Games.get().getGameBiId(nextId);
            PartyState.Participant me = s.participant(party.userId());
            intermissionCard.bind(s.roundIndex + 1, s.roundCount, next,
                    me == null ? null : getString(R.string.round_wait_party_score, me.score, PartyState.points(me.points2)));
            intermissionCard.showState(RoundWaitState.party(s.phaseDeadlineAt, party.now()));
            box.addView(intermissionCard.view, OnlineUi.cardParams(this));
            LinearLayout standings = OnlineUi.card(this);
            PartyUi.standings(this, standings, s);
            box.addView(standings, OnlineUi.cardParams(this));
            LinearLayout results = OnlineUi.card(this);
            PartyUi.results(this, results, s, true);
            box.addView(results, OnlineUi.cardParams(this));
        } else {
            text(getString(R.string.party_round_header, s.roundIndex + 1, s.roundCount,
                    s.round() == null ? "" : PartyUi.game(this, s.round().gameId)));
            text(s.is("LOADING") ? R.string.party_loading : s.is("COUNTDOWN") ? R.string.party_starting
                    : R.string.party_playing);
            countdown = PartyUi.text(this, ""); box.addView(countdown);
            PartyUi.standings(this, box, s);
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
    private void joinRoom(String code) {
        int digits = PartyState.roomCodeDigits(code);
        if (digits == 0) { entryError = getString(R.string.party_code_invalid); render(); return; }
        OnlineSession session = OnlineSession.get();
        if (session.requiresAccountLogin()) { render(); return; }
        if (party.occupied() || !session.canChangeAccount()) {
            entryError = getString(R.string.party_busy); render(); return;
        }
        entryError = null; returnRequested = false; party.dismissFinished();
        if (digits == 4) {
            // Keep the real room_join pending gate and own its UI listener before sending.
            legacyJoinPending = true;
            claimLegacyListener();
            session.joinFriendRoom(code);
            legacyJoinPending = session.hasPendingFriendRoom();
            if (!legacyJoinPending) releaseLegacyListener();
        } else party.join(code);
        render();
    }
    private boolean guardOtherOnlineFlow() {
        OnlineSession session = OnlineSession.get();
        boolean ownLegacyFlow = legacyJoinPending
                && (session.hasPendingFriendRoom() || (session.isInMatch() && session.isFriendMode()));
        if (session.hasNonPartyFlow() && !ownLegacyFlow) {
            // Do not replace the listener that is about to receive practice/ranked match_found.
            Toast.makeText(this, R.string.party_busy, Toast.LENGTH_SHORT).show();
            finish(); return false;
        }
        return true;
    }
    private void claimLegacyListener() {
        OnlineSession session = OnlineSession.get();
        OnlineSession.MatchListener current = session.getMatchListener();
        if (current == this) return;
        previousMatchListener = current instanceof PartyActivity
                ? ((PartyActivity) current).previousMatchListener : current;
        session.setMatchListener(this);
    }
    private void releaseLegacyListener() {
        OnlineSession session = OnlineSession.get();
        if (session.getMatchListener() == this) session.setMatchListener(previousMatchListener);
        previousMatchListener = null;
    }
    private void cancelLegacyJoin() {
        legacyJoinPending = false;
        OnlineSession.get().cancelFriendRoom();
        releaseLegacyListener();
    }
    private void openAcceptedMatch() {
        if (isFinishing() || !OnlineSession.get().isInMatch()) return;
        legacyJoinPending = false;
        releaseLegacyListener();
        startActivity(new Intent(this, OnlineMatchActivity.class));
        finish();
    }
    @Override public void onStateChanged(int state) { render(); }
    @Override public void onAuthOk(OnlineMessages.AuthOk profile) { render(); }
    @Override public void onMatchFound(OnlineMessages.MatchFound match) {
        // The dispatcher already accepted this identity. Route every legitimate mode;
        // a stale/foreign callback must never navigate just because it names FRIEND.
        if (OnlineSession.get().ownsAcceptedMatch(match)) openAcceptedMatch();
    }
    @Override public void onMatchCancelled(MatchAcceptance.Action action, String mode) {
        legacyJoinPending = false; releaseLegacyListener(); render();
    }
    @Override public void onError(int code, String message) {
        legacyJoinPending = false; releaseLegacyListener();
        entryError = message == null ? getString(R.string.online_error_connection) : message;
        render();
    }
    @Override public void onRoundStart(OnlineMessages.RoundStart round) { }
    @Override public void onOpponentScore(OnlineMessages.OpponentScore score) { }
    @Override public void onRoundEnd(OnlineMessages.RoundEnd round) { }
    @Override public void onMatchEnd(OnlineMessages.MatchEnd end) { }
    @Override public void onOpponentStart() { }
    @Override public void onRoomCreated(String code) { }
    @Override public void onHistory(OnlineMessages.HistoryEntry[] entries) { }
    @Override public void onTop(OnlineMessages.TopEntry[] entries) { }
    @Override public void onOpponentDisconnected() { }
    @Override public void onOpponentReconnected() { }
    @Override public void onBackPressed() {
        if (legacyJoinPending) { cancelLegacyJoin(); finish(); return; }
        if (party.state() == null) {
            if (party.canCancelEntry()) party.cancelEntry();
            if (party.occupied()) { party.sync(); text(R.string.party_waiting); return; }
            finish(); return;
        }
        com.cube.memorygames.ui.DialogUi.builder(this).setTitle(R.string.party_leave).setMessage(R.string.party_leave_confirm)
                .setPositiveButton(R.string.ok, (d, w) -> { exitRequested = true; party.dismissFinished(); party.leave(); })
                .setNegativeButton(R.string.cancel, null).show();
    }
}
