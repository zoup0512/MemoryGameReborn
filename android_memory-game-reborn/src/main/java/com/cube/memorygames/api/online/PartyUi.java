package com.cube.memorygames.api.online;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.cube.memorygames.model.GameInfo;
import com.cube.memorygames.Games;
import com.memory.brain.training.games.R;

/** Scrollable, full-roster party views shared by the lobby, results and in-game HUD. */
final class PartyUi {
    private PartyUi() { }
    static TextView text(Context c, String value) {
        TextView v = OnlineUi.text(c, value, 16, R.color.online_text_primary, OnlineUi.regular(c));
        v.setPadding(0, OnlineUi.dp(c, 8), 0, OnlineUi.dp(c, 8)); return v;
    }
    static String game(Context c, String id) {
        GameInfo info = Games.get().getGameBiId(id);
        return info == null ? id : c.getString(info.getGameNameRes());
    }
    static String name(PartyState s, long id) {
        PartyState.Participant p = s.participant(id);
        return p == null ? Long.toString(id) : p.nickname;
    }
    static void standings(Context c, LinearLayout box, PartyState s) {
        box.addView(OnlineUi.sectionTitle(c, R.string.party_standings));
        for (PartyState.Participant p : s.leaderboard()) {
            String rank = p.rank > 0 ? Integer.toString(p.rank) : "—";
            String suffix = p.withdrawn ? c.getString(R.string.party_withdrawn)
                    : !p.connected ? c.getString(R.string.party_offline) : "";
            box.addView(text(c, c.getString(R.string.party_score_row, rank, p.nickname,
                    p.score, PartyState.points(p.points2), p.firstPlaces, suffix)));
        }
    }
    static void results(Context c, LinearLayout box, PartyState s) {
        if (s.results == null) return;
        for (PartyState.Result r : s.results) {
            box.addView(text(c, c.getString(R.string.party_result_round, r.roundIndex + 1, game(c, r.gameId))));
            if (r.entries != null) for (PartyState.Entry e : r.entries) box.addView(text(c,
                    c.getString(R.string.party_result_entry, name(s, e.playerId), e.score,
                            e.rank > 0 ? Integer.toString(e.rank) : "—", PartyState.points(e.points2),
                            e.eligible ? "" : c.getString(R.string.party_ineligible))));
        }
    }
    static void showStandings(Context c) {
        PartyState s = PartySession.get().state(); if (s == null) return;
        ScrollView scroll = new ScrollView(c); LinearLayout box = OnlineUi.page(c); scroll.addView(box);
        standings(c, box, s);
        PartySession.Listener listener = () -> {
            PartyState current = PartySession.get().state();
            if (current != null) { box.removeAllViews(); standings(c, box, current); }
        };
        android.app.Dialog dialog = com.cube.memorygames.ui.DialogUi.builder(c).setTitle(R.string.party_standings).setView(scroll)
                .setPositiveButton(R.string.ok, null).create();
        PartySession.get().addListener(listener);
        dialog.setOnDismissListener(d -> PartySession.get().removeListener(listener));
        dialog.show();
    }
    static String error(Context c, String raw) {
        if (raw == null) return "";
        int res;
        switch (raw) {
            case "party_busy": res = R.string.party_busy; break;
            case "party_login_required": res = R.string.party_login_required; break;
            case "party_disconnected": res = R.string.party_disconnected; break;
            case "party_timeout": res = R.string.party_timeout; break;
            case "kicked": res = R.string.party_kicked; break;
            case "expired": case "idle_timeout": res = R.string.party_expired; break;
            case "lobby not found": case "room not found": case "invalid code": case "party not found": res = R.string.party_room_missing; break;
            case "room full": case "lobby full": res = R.string.party_full; break;
            case "match in progress": case "not in lobby phase": res = R.string.party_phase_error; break;
            case "host only": case "not host": res = R.string.party_host_only; break;
            case "players not ready": case "not all ready": case "need 3 players": case "need at least 3 players": res = R.string.party_start_rule; break;
            case "not_enough_players": res = R.string.party_not_enough; break;
            case "loading_failed": res = R.string.party_loading_failed; break;
            case "history unavailable": res = R.string.party_history_unavailable; break;
            default: return c.getString(R.string.party_error_detail, raw);
        }
        return c.getString(res);
    }
}
