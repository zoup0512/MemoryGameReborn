package com.cube.memorygames.api.online;

import org.junit.Test;
import static org.junit.Assert.*;

public class PartyStateTest {
    private PartyState state(String lobby, long version) {
        PartyState s = new PartyState(); s.lobbyId = lobby; s.matchId = "match";
        s.stateVersion = version; s.phase = "LOBBY"; s.hostId = 1; return s;
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
    @Test public void readyGateRequiresThreeOnlinePlayersAndOnlyNonHostsReady() {
        PartyState s = state("room", 1); s.members = new PartyState.Member[3];
        for (int i = 0; i < 3; i++) { s.members[i] = new PartyState.Member(); s.members[i].playerId = i + 1; s.members[i].connected = true; s.members[i].ready = i != 0; }
        assertTrue(s.canStart(1)); assertFalse(s.canStart(2));
        s.members[2].ready = false; assertFalse(s.canStart(1));
        s.members[2].ready = true; s.members[2].connected = false; assertFalse(s.canStart(1));
        s.members[2].connected = true; s.phase = "PLAYING"; assertFalse(s.canStart(1));
    }
    @Test public void allSixParticipantsRemainVisibleAndWithdrawnSortLast() {
        PartyState s = state("room", 1); s.participants = new PartyState.Participant[6];
        for (int i = 0; i < 6; i++) { PartyState.Participant p = new PartyState.Participant(); p.playerId = i; p.points2 = i; s.participants[i] = p; }
        s.participants[5].withdrawn = true;
        assertEquals(6, s.leaderboard().size());
        assertEquals(4, s.leaderboard().get(0).playerId);
        assertEquals(5, s.leaderboard().get(5).playerId);
    }
    @Test public void exactHalfPointsNeverRoundTies() {
        assertEquals("0", PartyState.points(0)); assertEquals("1", PartyState.points(2));
        assertEquals("2.5", PartyState.points(5));
    }
    @Test public void parsesFullAuthoritativeSnapshotIncludingZeroBasedRoundAndSeed() {
        PartyState s = OnlineMessages.payloadOf("{\"t\":\"party_state\",\"d\":{\"lobbyId\":\"l\",\"phase\":\"LOADING\",\"roundIndex\":0,\"rounds\":[{\"gameId\":\"22\",\"durationMs\":45000,\"seed\":-9223372036854775807}],\"participants\":[{\"playerId\":12,\"points2\":5,\"withdrawn\":false}]}}", PartyState.class);
        assertNotNull(s); assertTrue(s.activeRound()); assertEquals("22", s.round().gameId);
        assertEquals(-9223372036854775807L, s.round().seed); assertEquals(5, s.participant(12).points2);
    }
}
