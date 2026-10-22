package com.cube.memorygames.api.online;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.Assert.*;

/** Guard integration seams that plain JVM tests cannot instantiate without an Android runtime. */
public class PartyIntegrationSourceTest {
    private String source(String relative) throws Exception {
        Path p = Paths.get("src/main/java/com/cube/memorygames", relative);
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }
    @Test public void countAllWrongOptionReportsFalseThroughSharedDialogEntry() throws Exception {
        String countAll = source("games/Game5CountAllActivity.java");
        assertTrue(countAll.contains("showFailureDialog()"));
        String base = source("games/Game1MemoryGridActivity.java");
        int dialog = base.indexOf("protected void showFailureDialog()");
        int next = base.indexOf("protected void showAppropriateDialog()", dialog);
        assertTrue(base.substring(dialog, next).contains("reportLevel(this, progression.getLevelNumber(), onlineScore, false)"));
        assertTrue(base.contains("reportLevel(this, level, this.onlineScore, true)"));
    }
    @Test public void terminalAuthenticationDetachesPartyBeforeDisconnect() throws Exception {
        String source = source("api/online/OnlineSession.java");
        int start = source.indexOf("private void stopAuthorization(");
        int end = source.indexOf("private void handleDisconnected", start);
        String stop = source.substring(start, end);
        assertTrue(stop.indexOf("runtime.party().reset()") >= 0);
        assertTrue(stop.indexOf("runtime.party().reset()") < stop.indexOf("disconnect()"));
        assertTrue(stop.contains("reconnectBlocked=true"));
    }
    private static final class Transport implements PartySession.Runtime {
        boolean online = true, login, auto = true, dropAfterSync, rejectEntry;
        long elapsed;
        final java.util.List<com.google.gson.JsonObject> frames = new java.util.ArrayList<>();
        final java.util.Map<Runnable, Long> tasks = new java.util.LinkedHashMap<>();
        public boolean connected() { return online; }
        public boolean canAutoConnect() { return auto; }
        public boolean requiresAccountLogin() { return login; }
        public boolean canChangeAccount() { return true; }
        public void connectIfDisconnected() { }
        public boolean send(String type, com.google.gson.JsonObject data, String rid) {
            if (!online || (rejectEntry && (type.equals("party_join") || type.equals("party_create")))) return false;
            com.google.gson.JsonObject frame = new com.google.gson.JsonObject();
            frame.addProperty("t", type); frame.addProperty("rid", rid); frame.add("d", data); frames.add(frame);
            if (dropAfterSync && type.equals("party_sync")) online = false;
            return true;
        }
        public void postDelayed(Runnable task, long delay) { tasks.put(task, elapsed + delay); }
        public long elapsedRealtime() { return elapsed; }
        void advance(long millis) {
            elapsed += millis;
            for (Runnable task : new java.util.ArrayList<>(tasks.keySet())) {
                if (tasks.get(task) <= elapsed) { tasks.remove(task); task.run(); }
            }
        }
        String rid(String type) {
            for (int i = frames.size() - 1; i >= 0; i--) if (type.equals(frames.get(i).get("t").getAsString())) return frames.get(i).get("rid").getAsString();
            throw new AssertionError("No sent " + type);
        }
        long count(String type) { return frames.stream().filter(f -> type.equals(f.get("t").getAsString())).count(); }
    }
    private void snapshot(PartySession party, String rid, String lobby, long version) {
        assertTrue(party.receive("party_state", "{\"rid\":\"" + rid + "\",\"d\":{\"lobbyId\":\"" + lobby
                + "\",\"phase\":\"LOBBY\",\"stateVersion\":" + version + "}}"));
    }
    private void left(PartySession party, String rid, String lobby) {
        assertTrue(party.receive("party_left", "{\"rid\":\"" + rid + "\",\"d\":{\"lobbyId\":"
                + (lobby == null ? "null" : "\"" + lobby + "\"") + ",\"reason\":\"left\"}}"));
    }
    private void retireA(PartySession party, Transport transport) {
        party.join("123456"); snapshot(party, transport.rid("party_join"), "A", 1);
        party.leave(); left(party, transport.rid("party_leave"), "A");
        assertNull(party.state()); assertFalse(party.occupied());
    }
    @Test public void entryChecksAuthorizationBeforeSendingOrQueueing() {
        Transport transport = new Transport(); PartySession party = new PartySession(transport);
        party.create(3); party.reset(); transport.frames.clear();
        transport.login = true; transport.auto = false; transport.online = false;
        party.create(3); party.join("123456");
        assertFalse(party.occupied()); assertEquals("party_login_required", party.error());
        assertEquals(0, transport.frames.size());
        transport.login = false; party.create(3); assertFalse(party.occupied());
    }
    @Test public void timeoutAndOlderSyncDoNotReleaseSentEntryButLaterSyncDoes() {
        Transport transport = new Transport(); PartySession party = new PartySession(transport);
        party.sync(); String oldSync = transport.rid("party_sync");
        party.join("123456"); transport.advance(8000);
        assertEquals("party_timeout", party.error()); assertTrue(party.occupied()); assertFalse(party.cancelEntry());
        party.leave(); assertTrue(party.occupied());
        left(party, oldSync, null); assertTrue(party.occupied());
        party.sync(); left(party, transport.rid("party_sync"), null); assertFalse(party.occupied());
    }
    @Test public void lostRejoinAckIsReconciledIntoRetiredLobbyByLaterSync() {
        Transport transport = new Transport(); PartySession party = new PartySession(transport);
        retireA(party, transport);
        party.join("123456"); transport.advance(8000);
        party.sync(); snapshot(party, transport.rid("party_sync"), "A", 2);
        assertNotNull(party.state()); assertEquals("A", party.state().lobbyId); assertTrue(party.occupied());
        party.leave(); left(party, transport.rid("party_leave"), "A"); assertFalse(party.occupied());
    }
    @Test public void joinAckAfterCorrelationExpiryStillReentersRetiredLobby() {
        Transport transport = new Transport(); PartySession party = new PartySession(transport);
        retireA(party, transport);
        party.join("123456"); String join = transport.rid("party_join"); transport.advance(120001);
        assertTrue(party.occupied()); snapshot(party, join, "A", 2);
        assertNotNull(party.state()); assertEquals("A", party.state().lobbyId); assertTrue(party.occupied());
    }
    @Test public void syncAfterJoinCorrelationExpiryPreservesReentryAuthority() {
        Transport transport = new Transport(); PartySession party = new PartySession(transport);
        retireA(party, transport); party.join("123456"); transport.advance(120001);
        party.sync(); snapshot(party, transport.rid("party_sync"), "A", 2);
        assertNotNull(party.state()); assertEquals("A", party.state().lobbyId);
        party.leave(); left(party, transport.rid("party_leave"), "A"); assertFalse(party.occupied());
    }
    @Test public void mismatchedSnapshotKeepsEntryPendingUntilCompatibleAuthorityArrives() {
        Transport transport = new Transport(); PartySession party = new PartySession(transport);
        party.join("123456"); String join = transport.rid("party_join");
        snapshot(party, "broadcast", "B", 1);
        snapshot(party, join, "A", 2); assertEquals("B", party.state().lobbyId);
        left(party, "broadcast-left", "B"); assertNull(party.state()); assertTrue(party.occupied());
        party.sync(); snapshot(party, transport.rid("party_sync"), "A", 3);
        assertEquals("A", party.state().lobbyId);
    }
    @Test public void newerBroadcastBeforeEntryReplyDoesNotLeakEntryOccupation() {
        Transport transport = new Transport(); PartySession party = new PartySession(transport);
        party.create(3); String create = transport.rid("party_create");
        snapshot(party, "broadcast", "A", 3); snapshot(party, create, "A", 2);
        assertEquals(3, party.state().stateVersion);
        party.leave(); left(party, transport.rid("party_leave"), "A"); assertFalse(party.occupied());
    }
    @Test public void rejectedSnapshotCannotConsumeEntryOrAuthorizeUnrelatedBroadcast() {
        Transport transport = new Transport(); PartySession party = new PartySession(transport);
        retireA(party, transport);
        party.sync(); String oldSync = transport.rid("party_sync"); party.join("123456");
        snapshot(party, oldSync, "A", 2); assertNull(party.state()); assertTrue(party.occupied());
        snapshot(party, "unsolicited", "A", 3); assertNull(party.state()); assertTrue(party.occupied());
        party.sync(); snapshot(party, transport.rid("party_sync"), "A", 4);
        assertNotNull(party.state()); assertEquals(4, party.state().stateVersion);
    }
    @Test public void queuedEntrySurvivesSocketDropBetweenAuthSyncAndSend() {
        Transport transport = new Transport(); transport.online = false;
        PartySession party = new PartySession(transport); party.join("123456");
        transport.online = true; transport.dropAfterSync = true; party.authenticated();
        assertTrue(party.occupied()); assertTrue(party.canCancelEntry()); assertEquals(0, transport.count("party_join"));
        transport.dropAfterSync = false; transport.online = true; party.authenticated();
        assertEquals(1, transport.count("party_join")); assertFalse(party.canCancelEntry());
        assertEquals("123456", transport.frames.get(transport.frames.size() - 1).getAsJsonObject("d").get("code").getAsString());
        snapshot(party, transport.rid("party_join"), "A", 1); assertNotNull(party.state());
    }
    @Test public void failedTransportHandoffRetainsQueueUntilRetryOrCancel() {
        Transport transport = new Transport(); transport.rejectEntry = true;
        PartySession party = new PartySession(transport); party.create(3);
        assertTrue(party.canCancelEntry()); party.authenticated(); assertTrue(party.canCancelEntry());
        transport.rejectEntry = false; party.authenticated();
        assertEquals(1, transport.count("party_create")); assertFalse(party.canCancelEntry());
        Transport offline = new Transport(); offline.online = false;
        PartySession cancellable = new PartySession(offline); cancellable.join("123456");
        assertTrue(cancellable.cancelEntry()); offline.online = true; cancellable.authenticated();
        assertEquals(0, offline.count("party_join")); assertFalse(cancellable.occupied());
    }
    @Test public void partyScreenOffersAccountRecoveryAndOnlyCancelsUnsentEntry() throws Exception {
        String source = source("api/online/PartyActivity.java");
        int start = source.indexOf("if (OnlineSession.get().requiresAccountLogin())");
        int end = source.indexOf("if (!party.connected())", start);
        assertTrue(source.substring(start, end).contains("AccountActivity.class"));
        assertTrue(source.substring(start, end).contains("return;"));
        assertTrue(source.contains("if (party.canCancelEntry()) party.cancelEntry()"));
        assertTrue(source.contains("if (party.occupied()) { party.sync(); text(R.string.party_waiting); return; }"));
        assertTrue(source.contains("action(R.string.party_cancel_entry, () -> party.cancelEntry())"));
    }
    @Test public void disconnectedHistoryIsReplayedAfterAuthentication() throws Exception {
        String source = source("api/online/PartySession.java");
        assertTrue(source.contains("if (!connected()) { pendingHistory = true; sync(); return; }"));
        assertTrue(source.contains("if (pendingHistory) { pendingHistory = false; requestHistory(); }"));
    }
    @Test public void unifiedLobbyHasOneFriendsCardWithDiscoverableCreateJoinAndHistory() throws Exception {
        String lobby = source("api/online/OnlineLobbyAdapter.java");
        int start = lobby.indexOf("// Old FRIEND W/L/D");
        int end = lobby.indexOf("return new ModePage", start);
        String friends = lobby.substring(start, end);
        assertTrue(friends.contains("R.string.party_create"));
        assertTrue(friends.contains("R.string.party_join"));
        assertTrue(friends.contains("R.string.party_history"));
        assertTrue(friends.contains("PartyActivity.class"));
        assertTrue(friends.contains("PartyHistoryActivity.class"));
        assertTrue(friends.contains("showJoinDialog()"));
        assertTrue(friends.contains("stats.setVisibility(View.GONE)"));
        assertFalse(lobby.contains("party_duel_"));
        assertFalse(lobby.contains("createFriendRoom()"));
        assertFalse(friends.contains("sectionTitle"));
        assertTrue(lobby.contains(".putExtra(PartyActivity.EXTRA_JOIN_CODE, code)"));
        assertTrue(lobby.contains("if (i == 2) {"));
    }
    @Test public void unifiedJoinValidatesFourOrSixAndOwnsLegacyPendingListener() throws Exception {
        String dialogs = source("api/online/OnlineDialogs.java");
        assertTrue(dialogs.contains("InputFilter.LengthFilter(6)"));
        assertTrue(dialogs.contains("PartyState.roomCodeDigits(code) == 0"));
        String activity = source("api/online/PartyActivity.java");
        int start = activity.indexOf("private void joinRoom(String code)");
        int end = activity.indexOf("private void cancelLegacyJoin()", start);
        String join = activity.substring(start, end);
        assertTrue(join.contains("if (digits == 4)"));
        assertTrue(join.indexOf("claimLegacyListener()") < join.indexOf("session.joinFriendRoom(code)"));
        assertTrue(join.indexOf("legacyJoinPending = true") < join.indexOf("session.joinFriendRoom(code)"));
        assertTrue(join.contains("else party.join(code)"));
        assertTrue(activity.contains("implements PartySession.Listener, OnlineSession.MatchListener"));
        assertTrue(activity.contains("if (OnlineSession.get().ownsAcceptedMatch(match)) openAcceptedMatch()"));
        assertTrue(activity.contains("startActivity(new Intent(this, OnlineMatchActivity.class))"));
        assertTrue(activity.contains("getIntent().removeExtra(EXTRA_JOIN_CODE)"));
        assertTrue(activity.contains("if (session.getMatchListener() == this) session.setMatchListener(previousMatchListener)"));
        String session = source("api/online/OnlineSession.java");
        int room = session.indexOf("public void joinFriendRoom(String code)");
        int cancel = session.indexOf("public void cancelFriendRoom()", room);
        String command = session.substring(room, cancel);
        assertTrue(command.indexOf("friendRoomPending = true") < command.indexOf("sendRoomCommand(OnlineMessages.T_ROOM_JOIN, join)"));
        assertTrue(session.contains("&& !this.friendRoomPending && !this.practicePending"));
    }
    @Test public void joinDialogUsesNeutralTitleAndShortPlaceholderInsteadOfLegacyFourDigitTitle() throws Exception {
        String dialogs = source("api/online/OnlineDialogs.java");
        int start = dialogs.indexOf("public static void showJoinRoomCode(");
        int end = dialogs.indexOf("public static void showBotDifficulty(", start);
        String join = dialogs.substring(start, end);
        assertTrue(join.contains("context.getString(R.string.party_enter_code)"));
        assertFalse(join.contains("R.string.online_enter_code"));
        assertTrue(join.contains("input.setHint(R.string.party_code_hint)"));
        assertTrue(join.contains("OnlineUi.body(context, R.string.party_code_note)"));
        for (String locale : new String[]{"values", "values-zh"}) {
            String xml = new String(Files.readAllBytes(Paths.get("src/main/res", locale, "strings_party.xml")), StandardCharsets.UTF_8);
            assertTrue(xml.contains("<string name=\"party_code_hint\">000000</string>"));
            assertTrue(xml.contains("<string name=\"party_enter_code\">"
                    + (locale.equals("values") ? "Enter room code" : "输入房间码") + "</string>"));
        }
    }
    @Test public void createEntrySelectsRoundsAndRequiresConfirmationWithoutIntentReplay() throws Exception {
        String lobby = source("api/online/OnlineLobbyAdapter.java");
        assertTrue(lobby.contains(".putExtra(PartyActivity.EXTRA_ENTRY_ACTION, PartyActivity.ENTRY_CREATE)"));
        String activity = source("api/online/PartyActivity.java");
        assertTrue(activity.contains("ENTRY_CREATE.equals(getIntent().getStringExtra(EXTRA_ENTRY_ACTION))"));
        assertTrue(activity.contains("getIntent().removeExtra(EXTRA_ENTRY_ACTION)"));
        assertTrue(activity.contains("b.putBoolean(\"createSetup\", createSetup)"));
        int setup = activity.indexOf("if (createSetup) {");
        int end = activity.indexOf("action(R.string.party_join", setup);
        String confirm = activity.substring(setup, end);
        assertTrue(confirm.contains("R.string.party_create_setup_note"));
        assertTrue(confirm.contains("rounds == 3 ? R.string.party_rounds_three"));
        assertTrue(confirm.indexOf("action(R.string.party_create_confirm") < confirm.indexOf("party.create(rounds)"));
        assertTrue(confirm.contains("if (!guardOtherOnlineFlow() || party.occupied()) return"));
        assertEquals(1, activity.split("party.create\\(rounds\\)", -1).length - 1);
    }
    @Test public void partyEntryCannotStealPracticeRankedOrUnownedLegacyRequestListener() throws Exception {
        String session = source("api/online/OnlineSession.java");
        int gate = session.indexOf("public boolean hasNonPartyFlow()");
        int endGate = session.indexOf("public boolean ownsAcceptedMatch", gate);
        String busy = session.substring(gate, endGate);
        for (String pending : new String[]{"practicePending", "friendRoomPending", "pendingQueueRejoin",
                "queueRequestPending", "pendingRoomAck != null", "state == STATE_MATCH", "state == STATE_QUEUE"})
            assertTrue(pending, busy.contains(pending));
        String activity = source("api/online/PartyActivity.java");
        int create = activity.indexOf("protected void onCreate(");
        int destroy = activity.indexOf("protected void onDestroy()", create);
        String lifecycle = activity.substring(create, destroy);
        assertFalse(lifecycle.contains("setMatchListener(this)"));
        assertFalse(lifecycle.contains("legacyJoinPending = OnlineSession.get().hasPendingFriendRoom()"));
        assertTrue(lifecycle.contains("if (!guardOtherOnlineFlow()) return"));
        assertTrue(lifecycle.contains("if (isFinishing() || !guardOtherOnlineFlow()) return"));
        assertTrue(activity.contains("if (session.hasNonPartyFlow() && !ownLegacyFlow)"));
        assertTrue(activity.contains("previousMatchListener = current instanceof PartyActivity"));
        assertTrue(activity.contains("if (session.getMatchListener() == this) session.setMatchListener(previousMatchListener)"));
        int found = activity.indexOf("public void onMatchFound(");
        int cancelled = activity.indexOf("public void onMatchCancelled(", found);
        String route = activity.substring(found, cancelled);
        assertTrue(route.contains("ownsAcceptedMatch(match)"));
        assertFalse(route.contains("legacyJoinPending &&"));
        assertFalse(route.contains("MODE_FRIEND.equals(match.mode)"));
        String lobby = source("api/online/OnlineLobbyAdapter.java");
        assertTrue(lobby.contains("if (!canOpenFriendsEntry()) return"));
        assertTrue(lobby.contains("if (!OnlineSession.get().hasNonPartyFlow()) return true"));
    }
    @Test public void onlyAcceptedMatchIdentityRoutesEveryLegitimateModeAndRejectsStaleCallbacks() {
        for (String mode : new String[]{OnlineSession.MODE_FRIEND, OnlineSession.MODE_PRACTICE, OnlineSession.MODE_RANKED}) {
            OnlineMessages.MatchFound found = new OnlineMessages.MatchFound();
            found.matchId = "accepted"; found.mode = mode;
            assertTrue(OnlineSession.acceptedMatchIdentity("accepted", mode, found));
            assertFalse(OnlineSession.acceptedMatchIdentity("newer", mode, found));
            assertFalse(OnlineSession.acceptedMatchIdentity(null, mode, found));
            assertFalse(OnlineSession.acceptedMatchIdentity("accepted", "OTHER", found));
            found.matchId = null;
            assertFalse(OnlineSession.acceptedMatchIdentity("accepted", mode, found));
        }
        assertFalse(OnlineSession.acceptedMatchIdentity("accepted", OnlineSession.MODE_FRIEND, null));
    }
    @Test public void filteredHistoryIsSentToServerWithoutChangingUnfilteredCallers() throws Exception {
        String session = source("api/online/OnlineSession.java");
        int start = session.indexOf("public void requestHistory()");
        int end = session.indexOf("public boolean hasRequestedMatchStart()", start);
        String history = session.substring(start, end);
        assertTrue(history.contains("requestHistory(null)"));
        assertTrue(history.contains("public void requestHistory(String mode)"));
        assertTrue(history.contains("MODE_FRIEND.equals(mode) || MODE_RANKED.equals(mode) || MODE_PRACTICE.equals(mode)"));
        assertTrue(history.contains("request.addProperty(\"mode\", mode)"));
        assertTrue(history.contains("send(OnlineMessages.T_HISTORY_REQUEST, request)"));
    }
    @Test public void partyResourcesHaveNoFixedLimitAndKeepLegacyRulesSeparate() throws Exception {
        for (String locale : new String[]{"values", "values-zh"}) {
            String xml = new String(Files.readAllBytes(Paths.get("src/main/res", locale, "strings_party.xml")), StandardCharsets.UTF_8);
            assertFalse(xml.contains("3–6")); assertFalse(xml.contains("3-6"));
            assertFalse(xml.contains("/6")); assertFalse(xml.contains("多人"));
            if (locale.equals("values"))
                assertTrue(xml.contains("<string name=\"party_room\">Room %1$s · Players: %2$d</string>"));
            assertTrue(xml.contains("name=\"party_legacy_history\""));
            assertTrue(xml.contains("name=\"party_history_current_note\""));
            assertTrue(xml.contains("name=\"party_legacy_join_note\""));
        }
    }
    @Test public void lightweightAckClearsOnlyItsOwnRequestWithoutNotifyingSnapshotListeners() throws Exception {
        Transport transport = new Transport(); PartySession party = new PartySession(transport);
        party.join("123456"); snapshot(party, transport.rid("party_join"), "A", 8);
        java.lang.reflect.Method send = PartySession.class.getDeclaredMethod("send", String.class, com.google.gson.JsonObject.class);
        send.setAccessible(true);
        assertEquals(true, send.invoke(party, "party_level", new com.google.gson.JsonObject()));
        String rid = transport.rid("party_level");
        java.lang.reflect.Field requests = PartySession.class.getDeclaredField("requests");
        requests.setAccessible(true);
        assertTrue(((java.util.Map<?, ?>) requests.get(party)).containsKey(rid));
        final int[] changed = {0}; party.addListener(() -> changed[0]++);
        assertTrue(party.receive("party_ack", "{\"rid\":\"" + rid + "\",\"d\":{\"lobbyId\":\"A\",\"score\":200,\"stateVersion\":9}}"));
        assertFalse(((java.util.Map<?, ?>) requests.get(party)).containsKey(rid));
        assertEquals(0, changed[0]); assertEquals(8, party.state().stateVersion);
    }
    @Test public void delayedLightweightAckDoesNotChangeStateScoresVersionsOrResurrectRoom() {
        Transport transport = new Transport(); PartySession party = new PartySession(transport);
        party.join("123456"); snapshot(party, transport.rid("party_join"), "A", 8);
        PartyState authority = party.state();
        assertTrue(party.receive("party_ack", "{\"rid\":\"party-stale-level\",\"d\":{\"lobbyId\":\"old\",\"matchId\":\"old\",\"roundIndex\":9,\"playerId\":1,\"stateVersion\":99,\"score\":999}}"));
        assertSame(authority, party.state()); assertEquals(8, party.state().stateVersion);
        party.leave(); left(party, transport.rid("party_leave"), "A");
        assertNull(party.state()); assertFalse(party.occupied());
        assertTrue(party.receive("party_ack", "{\"rid\":\"party-stale-level\",\"d\":{\"lobbyId\":\"A\",\"stateVersion\":100}}"));
        assertNull(party.state()); assertFalse(party.occupied());
        transport.advance(8000); assertNull(party.error());
    }
    @Test public void sixRealGamesUseOneHeldFlowAndAllThreePrivateTimersAreGated() throws Exception {
        String base = source("games/Game1MemoryGridActivity.java");
        assertTrue(base.contains("if (partyWaiting) this.stateTimer.setHeld(true)"));
        assertTrue(base.contains("partyBridge.attach()"));
        for (String game : new String[]{"Game16LikePreviousActivity", "Game20ColorsActivity", "Game23TableActivity"}) {
            String s = source("games/" + game + ".java");
            assertTrue(s.contains("!isPartyWaiting() && this.startedTime <= 0"));
            assertTrue(s.contains("protected void onPartyClockStart()"));
            assertTrue(s.contains("PartySession.get().remaining()"));
        }
    }
}
