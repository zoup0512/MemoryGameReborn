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
        assertTrue(stop.indexOf("PartySession.get().reset()") < stop.indexOf("disconnect()"));
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
