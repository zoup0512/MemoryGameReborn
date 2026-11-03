package com.cube.memorygames.api.online;

import org.junit.Test;
import static org.junit.Assert.*;

public class PartyStateTest {
    private PartyState state(String lobby, long version) {
        PartyState s = new PartyState(); s.lobbyId = lobby; s.matchId = "match";
        s.stateVersion = version; s.phase = "LOBBY"; s.hostId = 1; return s;
    }
    @Test public void terminalAuthenticationNeverQueuesAnotherCreateOrJoin() {
        PartySession.EntryRequests entries = new PartySession.EntryRequests();
        entries.sending("party_create", "old-entry");
        entries.reset(); // stopAuthorization resets party before disconnecting.
        for (String type : new String[]{"party_create", "party_join"}) {
            assertFalse(entries.queue(type, new com.google.gson.JsonObject(), false, true));
            assertFalse(entries.occupied());
            assertFalse(entries.queue(type, new com.google.gson.JsonObject(), true, true));
            assertFalse(entries.occupied());
            assertFalse(entries.queue(type, new com.google.gson.JsonObject(), false, false));
            assertFalse(entries.occupied());
        }
    }
    @Test public void unsentRoomRequestCanBeCancelledWithoutServerLeave() {
        PartySession.EntryRequests entries = new PartySession.EntryRequests();
        for (String type : new String[]{"party_create", "party_join"}) {
            assertTrue(entries.queue(type, new com.google.gson.JsonObject(), true, false));
            assertTrue(entries.occupied()); assertTrue(entries.canCancel());
            assertTrue(entries.cancel()); assertFalse(entries.occupied());
            assertNull(entries.queuedType); assertNull(entries.queuedData);
        }
    }
    @Test public void sentEntryIsNotReleasedByCancelOrUnrelatedOrOlderSync() {
        PartySession.EntryRequests entries = new PartySession.EntryRequests();
        entries.sending("party_sync", "before-entry");
        entries.sending("party_join", "entry");
        assertFalse(entries.canCancel()); assertFalse(entries.cancel());
        entries.acknowledged("unrelated-ready", false);
        entries.acknowledged("before-entry", true);
        assertTrue(entries.occupied());
        entries.sending("party_sync", "failed-sync");
        entries.acknowledged("failed-sync", false);
        assertTrue(entries.occupied());
        entries.sending("party_sync", "after-entry");
        entries.acknowledged("after-entry", true);
        assertFalse(entries.occupied());
    }
    @Test public void correlatedReplyReleasesOnlyItsOwnSentEntry() {
        PartySession.EntryRequests entries = new PartySession.EntryRequests();
        entries.sending("party_create", "entry");
        entries.acknowledged("entry", false);
        assertFalse(entries.occupied());
    }
    @Test public void normalReconnectRetainsQueuedPayloadUntilItIsSent() {
        PartySession.EntryRequests entries = new PartySession.EntryRequests();
        com.google.gson.JsonObject data = new com.google.gson.JsonObject(); data.addProperty("code", "123456");
        assertTrue(entries.queue("party_join", data, true, false));
        entries.sending("party_sync", "auth-sync");
        assertEquals("party_join", entries.queuedType); assertSame(data, entries.queuedData);
        entries.clearQueued(); entries.sending("party_join", "entry");
        entries.acknowledged("auth-sync", true);
        assertTrue(entries.occupied()); assertFalse(entries.cancel());
        entries.acknowledged("entry", true);
        assertFalse(entries.occupied());
    }
    @Test public void stateVersionsRejectDuplicateAndDelayedUpdates() {
        PartyState s = state("a", 20);
        assertFalse(PartySession.accepts(s, state("a", 19)));
        assertFalse(PartySession.accepts(s, state("a", 20)));
        assertTrue(PartySession.accepts(s, state("a", 21)));
        assertFalse(PartySession.accepts(s, null));
    }
    @Test public void oldLobbyLeaveAckAndDelayedEmptySyncCannotClearNewRoom() {
        PartyState s = state("new", 1);
        assertFalse(PartySession.clearsLobby(s, "old", "old"));
        assertFalse(PartySession.clearsLobby(s, null, "old"));
        assertFalse(PartySession.clearsLobby(s, null, null));
        assertTrue(PartySession.clearsLobby(s, "new", null));
        assertTrue(PartySession.clearsLobby(s, null, "new"));
    }
    @Test public void onlyExplicitCorrelatedJoinCanReenterRetiredLobby() {
        assertTrue(PartySession.commandMayReenter("party_join"));
        assertFalse(PartySession.commandMayReenter("party_sync"));
        assertFalse(PartySession.commandMayReenter(null));
    }
    @Test public void activityCallbacksKeepAllThreeImmutableIdentityFields() {
        PartyState s = state("l", 1); s.roundIndex = 0;
        assertTrue(s.ownsRound("l", "match", 0));
        assertFalse(s.ownsRound("old-lobby", "match", 0));
        assertFalse(s.ownsRound("l", "old-match", 0));
        assertFalse(s.ownsRound("l", "match", 1));
    }
    @Test public void frozenIdentityChangesEveryMatchAndRound() {
        PartyState a = state("lobby", 1); a.roundIndex = 0;
        PartyState b = state("lobby", 2); b.roundIndex = 1;
        assertNotEquals(PartySession.key(a), PartySession.key(b));
        b.roundIndex = 0; b.matchId = "next-match";
        assertNotEquals(PartySession.key(a), PartySession.key(b));
    }
    private PartyState readyRoom(int count) {
        PartyState s = state("room", 1); s.capacity = 0;
        s.members = new PartyState.Member[count];
        for (int i = 0; i < count; i++) {
            s.members[i] = new PartyState.Member(); s.members[i].playerId = i + 1;
            s.members[i].connected = true; s.members[i].ready = i != 0;
        }
        return s;
    }
    @Test public void readyGateAllowsTwoSevenAndTwentyFourWithUnlimitedCapacity() {
        for (int count : new int[]{2, 7, 24}) {
            PartyState s = readyRoom(count);
            assertEquals(0, s.capacity);
            assertTrue(s.canStart(1)); assertFalse(s.canStart(2));
            s.members[count - 1].ready = false; assertFalse(s.canStart(1));
            s.members[count - 1].ready = true;
            s.members[count - 1].connected = false; assertFalse(s.canStart(1));
            s.members[count - 1].connected = true;
            s.phase = "PLAYING"; assertFalse(s.canStart(1));
        }
    }
    @Test public void fewerThanTwoAndDisconnectedHostCannotStart() {
        assertFalse(readyRoom(0).canStart(1)); assertFalse(readyRoom(1).canStart(1));
        PartyState s = readyRoom(2); s.members[0].connected = false;
        assertFalse(s.canStart(1)); s.members = null; assertFalse(s.canStart(1));
    }
    @Test public void allTwentyFourParticipantsRemainVisibleAndLongPointsSortExactly() {
        PartyState s = state("room", 1); s.participants = new PartyState.Participant[24];
        for (int i = 0; i < 24; i++) {
            PartyState.Participant p = new PartyState.Participant(); p.playerId = i;
            p.points2 = (long) Integer.MAX_VALUE + i; s.participants[i] = p;
        }
        s.participants[23].withdrawn = true;
        assertEquals(24, s.leaderboard().size());
        assertEquals(22, s.leaderboard().get(0).playerId);
        assertEquals(23, s.leaderboard().get(23).playerId);
    }
    @Test public void screenshotTiesKeepSnapshotOrderRegardlessOfLastScoreOrConnection() {
        PartyState s = state("room", 1); s.phase = "FINISHED"; s.status = "COMPLETED";
        PartyState.Participant self = participant(1, 15, 3, 106, 1);
        PartyState.Participant other = participant(2, 15, 3, 186, 1);
        PartyState.Participant third = participant(3, 0, 0, 98, 3);
        self.connected = true; other.connected = false;
        s.participants = new PartyState.Participant[]{self, third, other};
        assertArrayEquals(new Object[]{self, other, third}, s.leaderboard().toArray());
        assertTrue(s.tiedRank(1)); assertFalse(s.tiedRank(3));
        assertEquals("7.5", PartyState.points(self.points2));
        assertEquals("7.5", PartyState.points(other.points2));
        assertEquals(1, self.rank); assertEquals(1, other.rank); assertEquals(3, third.rank);
        self.score = 999; self.connected = false; other.connected = true;
        s.participants = new PartyState.Participant[]{other, third, self};
        assertArrayEquals(new Object[]{other, self, third}, s.leaderboard().toArray());
        assertSame(third, s.participants[1]);
    }
    @Test public void standingsComparePointsThenFirstPlacesAndKeepWithdrawnLast() {
        PartyState s = state("room", 1);
        PartyState.Participant lower = participant(1, 14, 9, 999, 3);
        PartyState.Participant fewerFirsts = participant(2, 15, 2, 999, 2);
        PartyState.Participant winner = participant(3, 15, 3, 1, 1);
        PartyState.Participant withdrawn = participant(4, 100, 10, 999, 4);
        withdrawn.withdrawn = true;
        s.participants = new PartyState.Participant[]{withdrawn, lower, fewerFirsts, winner};
        assertArrayEquals(new Object[]{winner, fewerFirsts, lower, withdrawn}, s.leaderboard().toArray());
    }
    @Test public void onlySharedPositiveAuthoritativeRanksAreTies() {
        PartyState s = state("room", 1);
        assertFalse(s.tiedRank(1));
        s.participants = null; assertFalse(s.tiedRank(1));
        PartyState.Participant a = participant(1, 0, 0, 0, 0);
        PartyState.Participant b = participant(2, 0, 0, 0, 0);
        s.participants = new PartyState.Participant[]{null, a, b};
        assertFalse(s.tiedRank(0)); assertFalse(s.tiedRank(-1)); assertFalse(s.tiedRank(1));
        a.rank = 2; assertFalse(s.tiedRank(2));
        b.rank = 2; assertTrue(s.tiedRank(2));
        b.rank = 3; assertFalse(s.tiedRank(2)); assertFalse(s.tiedRank(3));
    }
    @Test public void lastRoundLabelRequiresFinishedAndCompletedSnapshot() {
        PartyState s = state("room", 1);
        for (String phase : new String[]{"LOBBY", "LOADING", "COUNTDOWN", "PLAYING", "ROUND_RESULT", "FINISHED"}) {
            s.phase = phase;
            for (String status : new String[]{null, "IN_PROGRESS", "INTERRUPTED", "COMPLETED"}) {
                s.status = status;
                assertEquals("FINISHED".equals(phase) && "COMPLETED".equals(status), s.completed());
            }
        }
    }
    private PartyState.Participant participant(long id, long points2, int firstPlaces, int score, int rank) {
        PartyState.Participant p = new PartyState.Participant(); p.playerId = id;
        p.points2 = points2; p.firstPlaces = firstPlaces; p.score = score; p.rank = rank; return p;
    }
    @Test public void unifiedCodeValidationKeepsLegacyFourAndNewSixDigitProtocols() {
        assertEquals(4, PartyState.roomCodeDigits("0123"));
        assertEquals(6, PartyState.roomCodeDigits("012345"));
        for (String code : new String[]{null, "", "123", "12345", "1234567", " 1234", "1234 ", "abcd", "１２３４"})
            assertEquals(0, PartyState.roomCodeDigits(code));
    }
    @Test public void unlimitedCapacityAndLongPointsParseAcrossAllWireModels() {
        PartyState s = OnlineMessages.payloadOf("{\"d\":{\"capacity\":0,\"participants\":[{\"playerId\":1,\"points2\":4294967295}],\"results\":[{\"entries\":[{\"playerId\":1,\"points2\":4294967295}]}]}}", PartyState.class);
        assertEquals(0, s.capacity);
        assertEquals(4294967295L, s.participant(1).points2);
        assertEquals(4294967295L, s.results[0].entries[0].points2);
        PartyState.HistoryResponse h = OnlineMessages.payloadOf("{\"d\":{\"matches\":[{\"points2\":4294967295}]}}", PartyState.HistoryResponse.class);
        assertEquals(4294967295L, h.matches[0].points2);
    }
    @Test public void exactHalfPointsNeverRoundTies() {
        assertEquals("0", PartyState.points(0)); assertEquals("1", PartyState.points(2));
        assertEquals("2.5", PartyState.points(5));
        assertEquals("2147483647.5", PartyState.points(4294967295L));
        assertEquals("4611686018427387903.5", PartyState.points(Long.MAX_VALUE));
        assertEquals("4611686018427387903", PartyState.points(Long.MAX_VALUE - 1));
    }
    @Test public void parsesFullAuthoritativeSnapshotIncludingZeroBasedRoundAndSeed() {
        PartyState s = OnlineMessages.payloadOf("{\"t\":\"party_state\",\"d\":{\"lobbyId\":\"l\",\"phase\":\"LOADING\",\"roundIndex\":0,\"rounds\":[{\"gameId\":\"22\",\"durationMs\":45000,\"seed\":-9223372036854775807}],\"participants\":[{\"playerId\":12,\"points2\":5,\"withdrawn\":false}]}}", PartyState.class);
        assertNotNull(s); assertTrue(s.activeRound()); assertEquals("22", s.round().gameId);
        assertEquals(-9223372036854775807L, s.round().seed); assertEquals(5, s.participant(12).points2);
    }
}
