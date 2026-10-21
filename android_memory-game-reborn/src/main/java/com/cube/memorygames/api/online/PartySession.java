package com.cube.memorygames.api.online;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Main-thread party coordinator. Transport/auth belong exclusively to OnlineSession. */
public final class PartySession {
    public static final String EXTRA_LOBBY = "partyLobby", EXTRA_MATCH = "partyMatch",
            EXTRA_LEASE = "partyLease";
    public interface Listener { void onPartyChanged(); }
    private static final class Holder { private static final PartySession INSTANCE = new PartySession(); }
    public static PartySession get() { return Holder.INSTANCE; }
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<Listener> listeners = new HashSet<>();
    private final Map<String, String> requests = new HashMap<>();
    private final Map<String, String> requestLobbies = new HashMap<>();
    private final Map<String, String> correlations = new HashMap<>();
    private final Set<String> retiredLobbies = new HashSet<>();
    private Intent loadedIntent;
    private Context context;
    private PartyState state, finished;
    private PartyState.History[] history = new PartyState.History[0];
    private String error, lease, leaseKey, leavingLobby, leavingMatch;
    private String queuedType; private JsonObject queuedData;
    private long serverAt, elapsedAt;
    private int reportedLevel;
    private boolean completed, pendingHistory;
    private PartySession() { }
    public void init(Context c) { context = c.getApplicationContext(); }
    public void addListener(Listener l) { listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }
    private void changed() { for (Listener l : new ArrayList<>(listeners)) l.onPartyChanged(); }
    public PartyState state() { return state; }
    public PartyState finished() { return finished; }
    public void dismissFinished() { finished = null; }
    public PartyState.History[] history() { return history; }
    public String error() { return error; }
    public long userId() { OnlineMessages.AuthOk p = OnlineSession.get().getLastProfile(); return p == null ? -1 : p.userId; }
    public boolean occupied() { return state != null || queuedType != null || leavingLobby != null || hasEntryRequest(); }
    private boolean hasEntryRequest() { return requests.containsValue("party_create") || requests.containsValue("party_join"); }
    public long now() { return serverAt == 0 ? System.currentTimeMillis() : serverAt + SystemClock.elapsedRealtime() - elapsedAt; }
    public long remaining() { return state == null ? 0 : Math.max(0, state.deadlineAt - now()); }
    public boolean connected() { return OnlineSession.get().isAuthenticatedSocket(); }
    public void connectionChanged() { changed(); }
    public void authenticated() {
        send("party_sync", new JsonObject());
        if (pendingHistory) { pendingHistory = false; requestHistory(); }
        if (leavingLobby != null) sendLeave();
        else if (loadedIntent != null && owns(loadedIntent) && state.is("LOADING")) loaded(loadedIntent);
        if (queuedType != null) {
            String t = queuedType; JsonObject d = queuedData; queuedType = null; queuedData = null; send(t, d);
        }
        changed();
    }
    public void sync() {
        if (connected()) {
            send("party_sync", new JsonObject());
            if (leavingLobby != null) sendLeave();
        }
        else if (OnlineSession.get().getState() == OnlineSession.STATE_DISCONNECTED) OnlineSession.get().connect();
    }
    public void create(int rounds) { JsonObject d = new JsonObject(); d.addProperty("roundCount", rounds); enter("party_create", d); }
    public void join(String code) { JsonObject d = new JsonObject(); d.addProperty("code", code); enter("party_join", d); }
    private void enter(String t, JsonObject d) {
        if (occupied()) { error = "party_busy"; changed(); return; }
        if (!OnlineSession.get().canChangeAccount()) { error = "party_busy"; changed(); return; }
        error = null;
        if (connected()) send(t, d);
        else { queuedType = t; queuedData = d; sync(); }
        changed();
    }
    private JsonObject scope() {
        JsonObject d = new JsonObject();
        if (state != null) d.addProperty("lobbyId", state.lobbyId);
        return d;
    }
    public void config(int rounds) { JsonObject d = scope(); d.addProperty("roundCount", rounds); send("party_config", d); }
    public void ready(boolean ready) { JsonObject d = scope(); d.addProperty("ready", ready); send("party_ready", d); }
    public void start() { send("party_start", scope()); }
    public void memberAction(long player, boolean transfer) {
        JsonObject d = scope(); d.addProperty("playerId", player); send(transfer ? "party_transfer" : "party_kick", d);
    }
    public void returnToLobby(PartyState result) {
        if (result == null) return;
        JsonObject d = new JsonObject(); d.addProperty("lobbyId", result.lobbyId); d.addProperty("matchId", result.matchId);
        send("party_return", d);
    }
    public void requestHistory() {
        if (!connected()) { pendingHistory = true; sync(); return; }
        send("party_history", new JsonObject());
    }
    public void leave() {
        queuedType = null; queuedData = null;
        if (state == null) return;
        leavingLobby = state.lobbyId; leavingMatch = state.matchId; lease = null;
        sendLeave(); changed();
    }
    public boolean leaving() { return leavingLobby != null; }
    private void sendLeave() {
        JsonObject d = new JsonObject(); d.addProperty("lobbyId", leavingLobby);
        if (leavingMatch != null) d.addProperty("matchId", leavingMatch);
        send("party_leave", d);
    }
    private void send(String type, JsonObject d) {
        error = null;
        if (!connected()) { error = "party_disconnected"; changed(); return; }
        final String rid = "party-" + UUID.randomUUID(); requests.put(rid, type);
        requestLobbies.put(rid, state == null ? null : state.lobbyId);
        correlations.put(rid, type);
        main.postDelayed(() -> { correlations.remove(rid); requestLobbies.remove(rid); }, 120000);
        OnlineSession.get().sendParty(type, d, rid);
        main.postDelayed(() -> {
            String pending = requests.remove(rid);
            if (pending != null && !pending.equals("party_level") && !pending.equals("party_complete") && !pending.equals("party_round_ready")) {
                error = "party_timeout"; changed();
            }
        }, 8000);
    }
    /** Called before legacy error handling, so correlated party errors cannot mutate duel state. */
    public boolean receive(String type, String json) {
        JsonObject root;
        try { root = new JsonParser().parse(json).getAsJsonObject(); } catch (RuntimeException e) { return false; }
        String rid = root.has("rid") && !root.get("rid").isJsonNull() ? root.get("rid").getAsString() : null;
        boolean ours = rid != null && rid.startsWith("party-");
        if (rid != null) requests.remove(rid);
        String command = rid == null ? null : correlations.remove(rid);
        String requestedLobby = rid == null ? null : requestLobbies.remove(rid);
        if ("error".equals(type)) {
            if (!ours) return false;
            OnlineMessages.ErrorMsg e = OnlineMessages.payloadOf(json, OnlineMessages.ErrorMsg.class);
            if (e != null && e.code == 401) return false; // OnlineSession owns token refresh/terminal auth failure.
            error = e == null ? "party_error" : e.message;
            if ("party_leave".equals(command)) { leavingLobby = null; leavingMatch = null; sync(); }
            changed(); return true;
        }
        if (!type.startsWith("party_")) return false;
        if ("party_state".equals(type)) {
            PartyState incoming = OnlineMessages.payloadOf(json, PartyState.class);
            // A new explicit join may legitimately reuse the same persistent lobby.
            if (incoming != null && commandMayReenter(command)) retiredLobbies.remove(incoming.lobbyId);
            if (accepts(state, incoming) && !retiredLobbies.contains(incoming.lobbyId)
                    && (state == null || state.lobbyId.equals(incoming.lobbyId))) {
                state = incoming; serverAt = incoming.serverTime; elapsedAt = SystemClock.elapsedRealtime();
                if (incoming.is("FINISHED")) finished = incoming;
                if (!key(incoming).equals(leaseKey)) { lease = null; loadedIntent = null; reportedLevel = 0; completed = false; }
                error = null;
            }
        } else if ("party_left".equals(type)) {
            PartyState.Left left = OnlineMessages.payloadOf(json, PartyState.Left.class);
            if (left != null && clearsLobby(state, left.lobbyId, requestedLobby)) {
                if (state != null && left.lobbyId != null) retiredLobbies.add(state.lobbyId);
                state = null; lease = null; loadedIntent = null;
                if (!"not_in_lobby".equals(left.reason) && !"left".equals(left.reason)) error = left.reason;
            }
            if (left != null && (left.lobbyId == null || left.lobbyId.equals(leavingLobby))) { leavingLobby = null; leavingMatch = null; }
        } else if ("party_history".equals(type)) {
            PartyState.HistoryResponse h = OnlineMessages.payloadOf(json, PartyState.HistoryResponse.class);
            history = h == null || h.matches == null ? new PartyState.History[0] : h.matches;
        }
        changed(); return true;
    }
    public static boolean commandMayReenter(String command) { return "party_join".equals(command); }
    public static boolean clearsLobby(PartyState current, String leftLobby, String requestedLobby) {
        if (current == null) return true;
        return leftLobby != null ? leftLobby.equals(current.lobbyId)
                : current.lobbyId.equals(requestedLobby);
    }
    public static boolean accepts(PartyState old, PartyState next) {
        return next != null && next.lobbyId != null && (old == null || !old.lobbyId.equals(next.lobbyId)
                || next.stateVersion > old.stateVersion);
    }
    public static String key(PartyState s) { return s == null ? "" : s.lobbyId + "/" + s.matchId + "/" + s.roundIndex; }
    private SharedPreferences prefs() { return context.getSharedPreferences("friend_party", Context.MODE_PRIVATE); }
    /** Claim only during LOADING. Durable consumed marker forbids process-death question replay. */
    public String claim() {
        if (state == null || !state.is("LOADING") || leaving() || context == null) return null;
        PartyState.Participant p = state.participant(userId());
        if (p == null || p.withdrawn) return null;
        String key = key(state);
        if (key.equals(prefs().getString("consumed-" + userId(), null))) return null;
        if (!prefs().edit().putString("consumed-" + userId(), key).commit()) return null;
        leaseKey = key; lease = UUID.randomUUID().toString(); reportedLevel = 0; completed = false;
        return lease;
    }
    public void tag(Intent i, String token) {
        i.putExtra(EXTRA_LOBBY, state.lobbyId); i.putExtra(EXTRA_MATCH, state.matchId);
        i.putExtra(OnlineSession.EXTRA_ONLINE_ROUND, state.roundIndex); i.putExtra(EXTRA_LEASE, token);
    }
    public static boolean isParty(Intent i) { return i != null && i.hasExtra(EXTRA_LOBBY); }
    public boolean owns(Intent i) {
        return i != null && state != null && !leaving() && lease != null && lease.equals(i.getStringExtra(EXTRA_LEASE))
                && state.ownsRound(i.getStringExtra(EXTRA_LOBBY), i.getStringExtra(EXTRA_MATCH),
                        i.getIntExtra(OnlineSession.EXTRA_ONLINE_ROUND, -2));
    }
    private JsonObject roundScope(Intent i) {
        JsonObject d = new JsonObject(); d.addProperty("lobbyId", i.getStringExtra(EXTRA_LOBBY));
        d.addProperty("matchId", i.getStringExtra(EXTRA_MATCH));
        d.addProperty("roundIndex", i.getIntExtra(OnlineSession.EXTRA_ONLINE_ROUND, -2)); return d;
    }
    public boolean mayPlay(Intent i) {
        if (!owns(i) || !state.activeRound()) return false;
        PartyState.Participant p = state.participant(userId());
        return p != null && !p.withdrawn && (state.is("LOADING") || p.roundEligible);
    }
    public boolean playing(Intent i) { return mayPlay(i) && (state.is("PLAYING") || state.is("COUNTDOWN")) && now() >= state.startAt && remaining() > 0; }
    public void loaded(Intent i) {
        if (owns(i) && state.is("LOADING")) {
            loadedIntent = new Intent(i);
            send("party_round_ready", roundScope(i));
        }
    }
    public void level(Intent i, int level, boolean success) {
        if (!playing(i) || completed || level <= reportedLevel) return;
        reportedLevel = level;
        JsonObject d = roundScope(i); d.addProperty("level", level); d.addProperty("success", success); send("party_level", d);
    }
    public void complete(Intent i) {
        if (!owns(i) || !state.is("PLAYING") || completed) return;
        completed = true; send("party_complete", roundScope(i));
    }
    public void leave(Intent i) { if (owns(i)) leave(); }
    public void reset() {
        state = null; finished = null; lease = null; loadedIntent = null; leavingLobby = null; leavingMatch = null;
        queuedType = null; queuedData = null; pendingHistory = false; serverAt = 0; error = null;
        requests.clear(); correlations.clear(); requestLobbies.clear(); retiredLobbies.clear(); history = new PartyState.History[0]; changed();
    }
}
