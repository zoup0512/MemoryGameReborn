package com.cube.memorygames.api.online;

import org.junit.Test;
import static org.junit.Assert.*;

public class RoundWaitStateTest {
    @Test public void missingRoundEndShowsConfirmationNotAnInventedCountdown() {
        RoundWaitState state = RoundWaitState.duel(null, 3, true, 5000);
        assertEquals(RoundWaitState.Phase.CONFIRMING, state.phase);
        assertEquals(0, state.seconds);
    }

    @Test public void finalRoundSettlesEvenIfAnOldDeadlineRemains() {
        OnlineMessages.RoundEnd end = new OnlineMessages.RoundEnd(); end.round = 3;
        RoundWaitState state = RoundWaitState.duel(end, 3, true, 5000);
        assertEquals(RoundWaitState.Phase.SETTLING, state.phase);
        assertEquals(0, state.seconds);
    }

    @Test public void duelCountdownRoundsUpThenWaitsForServer() {
        OnlineMessages.RoundEnd end = new OnlineMessages.RoundEnd(); end.round = 1;
        assertEquals(2, RoundWaitState.duel(end, 3, true, 1001).seconds);
        assertEquals(1, RoundWaitState.duel(end, 3, true, 1).seconds);
        assertEquals(RoundWaitState.Phase.WAITING, RoundWaitState.duel(end, 3, true, 0).phase);
        assertEquals(RoundWaitState.Phase.WAITING, RoundWaitState.duel(end, 3, false, 5000).phase);
    }

    @Test public void partyDeadlineNeverLeavesAZeroSecondCountdown() {
        assertEquals(5, RoundWaitState.party(15000, 10000).seconds);
        assertEquals(1, RoundWaitState.party(15000, 14999).seconds);
        assertEquals(RoundWaitState.Phase.WAITING, RoundWaitState.party(15000, 15000).phase);
        assertEquals(RoundWaitState.Phase.WAITING, RoundWaitState.party(15000, 16000).phase);
        assertEquals(RoundWaitState.Phase.WAITING, RoundWaitState.party(0, 10000).phase);
    }

    @Test public void partyPreviewUsesTheFollowingRoundAndHidesInvalidOrFinalPlans() {
        PartyState state = new PartyState(); state.roundIndex = 0; state.roundCount = 2;
        PartyState.Round first = new PartyState.Round(); first.gameId = "first";
        PartyState.Round next = new PartyState.Round(); next.gameId = "next";
        state.rounds = new PartyState.Round[]{first, next};
        assertEquals("next", RoundWaitState.nextPartyGameId(state));
        state.roundIndex = 1; assertNull(RoundWaitState.nextPartyGameId(state));
        state.roundIndex = -1; assertNull(RoundWaitState.nextPartyGameId(state));
        state.roundIndex = 0; state.roundCount = 1; assertNull(RoundWaitState.nextPartyGameId(state));
        state.roundCount = 2; state.rounds[1] = null; assertNull(RoundWaitState.nextPartyGameId(state));
        state.rounds = null; assertNull(RoundWaitState.nextPartyGameId(state));
        assertNull(RoundWaitState.nextPartyGameId(null));
    }
}
