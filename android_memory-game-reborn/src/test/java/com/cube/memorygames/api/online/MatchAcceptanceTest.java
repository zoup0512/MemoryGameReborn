package com.cube.memorygames.api.online;

import org.junit.Test;

import static com.cube.memorygames.api.online.MatchAcceptance.Action.*;
import static org.junit.Assert.*;

public class MatchAcceptanceTest {
    @Test public void pairedButInactiveRankedPlayerReturnsToLobby() {
        MatchAcceptance acceptance = new MatchAcceptance();
        assertFalse(acceptance.isStartRequested());
        assertEquals(RETURN_TO_LOBBY, acceptance.cancel(true, OnlineSession.MODE_RANKED, false));
    }

    @Test public void twoInactivePlayersCannotAutomaticallyPairAgain() {
        MatchAcceptance a = new MatchAcceptance();
        MatchAcceptance b = new MatchAcceptance();
        assertEquals(RETURN_TO_LOBBY, a.cancel(true, OnlineSession.MODE_RANKED, false));
        assertEquals(RETURN_TO_LOBBY, b.cancel(true, OnlineSession.MODE_RANKED, false));
    }

    @Test public void onlyThePlayerWhoPressedStartMayRequeue() {
        MatchAcceptance a = new MatchAcceptance();
        MatchAcceptance b = new MatchAcceptance();
        a.requestStart();
        assertEquals(REQUEUE, a.cancel(true, OnlineSession.MODE_RANKED, false));
        assertEquals(RETURN_TO_LOBBY, b.cancel(true, OnlineSession.MODE_RANKED, false));
        b.requestStart();
        assertEquals(RETURN_TO_LOBBY, a.cancel(true, OnlineSession.MODE_RANKED, false));
        assertEquals(REQUEUE, b.cancel(true, OnlineSession.MODE_RANKED, false));
    }

    @Test public void acceptedRankedMatchConsumesStartIntentWhenCancelled() {
        MatchAcceptance acceptance = new MatchAcceptance();
        acceptance.requestStart();
        assertTrue(acceptance.isStartRequested());
        assertEquals(REQUEUE, acceptance.cancel(true, OnlineSession.MODE_RANKED, false));
        assertFalse(acceptance.isStartRequested());
        assertEquals(RETURN_TO_LOBBY, acceptance.cancel(true, OnlineSession.MODE_RANKED, false));
    }

    @Test public void everyNewPairingRequiresAnotherExplicitStart() {
        MatchAcceptance acceptance = new MatchAcceptance();
        acceptance.requestStart();
        assertEquals(REQUEUE, acceptance.cancel(true, OnlineSession.MODE_RANKED, false));
        acceptance.clear();
        assertEquals(RETURN_TO_LOBBY, acceptance.cancel(true, OnlineSession.MODE_RANKED, false));
        acceptance.requestStart();
        assertEquals(REQUEUE, acceptance.cancel(true, OnlineSession.MODE_RANKED, false));
    }

    @Test public void newMatchSnapshotDoesNotInheritPreviousStart() {
        MatchAcceptance acceptance = new MatchAcceptance();
        acceptance.requestStart();
        acceptance.clear();
        assertFalse(acceptance.isStartRequested());
        assertEquals(RETURN_TO_LOBBY, acceptance.cancel(true, OnlineSession.MODE_RANKED, false));
    }

    @Test public void repeatedStartRequestsPreserveIntentUntilCancellation() {
        MatchAcceptance acceptance = new MatchAcceptance();
        acceptance.requestStart();
        acceptance.requestStart();
        assertTrue(acceptance.isStartRequested());
        assertEquals(REQUEUE, acceptance.cancel(true, OnlineSession.MODE_RANKED, false));
        assertFalse(acceptance.isStartRequested());
    }

    @Test public void friendAndPracticeNeverFallThroughToRankedQueue() {
        for (String mode : new String[] {OnlineSession.MODE_FRIEND, OnlineSession.MODE_PRACTICE}) {
            MatchAcceptance acceptance = new MatchAcceptance();
            assertEquals(RETURN_TO_LOBBY, acceptance.cancel(true, mode, false));
            acceptance.requestStart();
            assertEquals(RETURN_TO_LOBBY, acceptance.cancel(true, mode, false));
            assertFalse(acceptance.isStartRequested());
        }
    }

    @Test public void unknownModeDoesNotAuthorizeRankedRequeue() {
        for (String mode : new String[] {null, "", "UNKNOWN"}) {
            MatchAcceptance acceptance = new MatchAcceptance();
            acceptance.requestStart();
            assertEquals(RETURN_TO_LOBBY, acceptance.cancel(true, mode, false));
        }
    }

    @Test public void leavingAnAcceptedRankedMatchDoesNotRequeue() {
        MatchAcceptance acceptance = new MatchAcceptance();
        acceptance.requestStart();
        assertEquals(RETURN_TO_LOBBY, acceptance.cancel(true, OnlineSession.MODE_RANKED, true));
        assertFalse(acceptance.isStartRequested());
    }

    @Test public void cancellationWithoutMatchIsIgnoredAndClearsOldIntent() {
        MatchAcceptance acceptance = new MatchAcceptance();
        acceptance.requestStart();
        assertEquals(IGNORE, acceptance.cancel(false, OnlineSession.MODE_RANKED, false));
        assertFalse(acceptance.isStartRequested());
    }

    @Test public void duplicateCancellationCannotRepeatAnAutomaticRequeue() {
        MatchAcceptance acceptance = new MatchAcceptance();
        acceptance.requestStart();
        assertEquals(REQUEUE, acceptance.cancel(true, OnlineSession.MODE_RANKED, false));
        assertEquals(IGNORE, acceptance.cancel(false, OnlineSession.MODE_RANKED, false));
    }
}
