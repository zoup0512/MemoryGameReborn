package com.cube.memorygames.api.online;

import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.*;

public class RoundIntermissionTest {
    @Test public void countdownUsesServerIntervalAndMonotonicElapsedTime() {
        RoundIntermission state = new RoundIntermission();
        assertTrue(state.accept(end(1, 1792100000000L, 1792100002000L), 1, 100L));
        assertTrue(state.hasCountdown());
        assertEquals(2000L, state.remainingMs(100L));
        assertEquals(1250L, state.remainingMs(850L));
        assertEquals(0L, state.remainingMs(2100L));
        assertEquals(0L, state.remainingMs(999999L));
    }

    @Test public void duplicateFrameDoesNotRestartTheCountdownOrRollbackSnapshot() {
        RoundIntermission state = new RoundIntermission();
        OnlineMessages.RoundEnd end = end(2, 10000L, 12000L);
        assertTrue(state.accept(end, 2, 100L));
        assertFalse(state.accept(end(2, 10000L, 12000L), 2, 1100L));
        assertFalse(state.accept(end(2, 9000L, 12000L), 2, 1100L));
        assertSame(end, state.getRoundEnd());
        assertEquals(1000L, state.remainingMs(1100L));
    }

    @Test public void reconnectRefreshesRemainingIntervalInsteadOfRepeatingFullRest() {
        RoundIntermission state = new RoundIntermission();
        assertTrue(state.accept(end(2, 10000L, 12000L), 2, 100L));
        assertTrue(state.accept(end(2, 11500L, 12000L), 2, 1600L));
        assertEquals(500L, state.remainingMs(1600L));
        assertEquals(0L, state.remainingMs(2100L));
    }

    @Test public void oldServerAndFinalRoundNeverInventACountdown() {
        OnlineMessages.RoundEnd old = new Gson().fromJson(
                "{\"matchId\":\"m1\",\"round\":1,\"myScore\":50,\"oppScore\":30}",
                OnlineMessages.RoundEnd.class);
        RoundIntermission state = new RoundIntermission();
        assertTrue(state.accept(old, 1, 100L));
        assertFalse(state.hasCountdown());
        assertEquals(0L, state.remainingMs(100L));
        assertTrue(state.accept(end(5, 20000L, 0L), 5, 200L));
        assertFalse(state.hasCountdown());
        assertEquals(0L, state.remainingMs(200L));
    }

    @Test public void expiredServerCountdownWaitsAtZero() {
        RoundIntermission state = new RoundIntermission();
        assertTrue(state.accept(end(2, 12001L, 12000L), 2, 100L));
        assertTrue(state.hasCountdown());
        assertEquals(0L, state.remainingMs(100L));
    }

    @Test public void staleRoundsAreRejectedAndClearRemovesPreviousMatch() {
        RoundIntermission state = new RoundIntermission();
        assertFalse(state.accept(null, 1, 100L));
        assertFalse(state.accept(end(0, 10000L, 12000L), -1, 100L));
        assertFalse(state.accept(end(1, 10000L, 12000L), 2, 100L));
        assertTrue(state.accept(end(2, 10000L, 12000L), 2, 100L));
        assertFalse(state.accept(end(1, 11000L, 13000L), -1, 200L));
        state.clear();
        assertNull(state.getRoundEnd());
        assertFalse(state.hasCountdown());
        assertEquals(0L, state.remainingMs(200L));
        assertTrue(state.accept(end(1, 100L, 2100L), -1, 300L));
    }

    @Test public void displayedSecondsRoundUpWithoutShowingZeroPrematurely() {
        assertEquals(2L, RoundIntermission.secondsRemaining(2000L));
        assertEquals(2L, RoundIntermission.secondsRemaining(1001L));
        assertEquals(1L, RoundIntermission.secondsRemaining(1000L));
        assertEquals(1L, RoundIntermission.secondsRemaining(1L));
        assertEquals(0L, RoundIntermission.secondsRemaining(0L));
        assertEquals(0L, RoundIntermission.secondsRemaining(-1L));
    }

    @Test public void newProtocolRoundTripsNextGameAndServerTimes() {
        Gson gson = new Gson();
        OnlineMessages.RoundEnd end = end(1, 10000L, 12000L);
        end.nextGameId = "4";
        OnlineMessages.RoundEnd restored = gson.fromJson(gson.toJson(end), OnlineMessages.RoundEnd.class);
        assertEquals(10000L, restored.serverNow);
        assertEquals(12000L, restored.nextRoundStartsAt);
        assertEquals("4", restored.nextGameId);
    }

    @Test public void endedOrSupersededTutorialAndCountdownCannotLaunchOldRound() {
        OnlineMessages.RoundStart old = new OnlineMessages.RoundStart();
        old.matchId = "m1";
        old.round = 1;
        assertTrue(RoundIntermission.canLaunch(old, "m1", 1, null));
        assertFalse(RoundIntermission.canLaunch(old, "m1", 1, end(1, 10000L, 12000L)));
        assertFalse(RoundIntermission.canLaunch(old, "m1", 2, null));
        assertFalse(RoundIntermission.canLaunch(old, "m2", 1, null));
        assertFalse(RoundIntermission.canLaunch(old, null, 1, null));
        OnlineMessages.RoundStart next = new OnlineMessages.RoundStart();
        next.matchId = "m1";
        next.round = 2;
        assertTrue(RoundIntermission.canLaunch(next, "m1", 2, null));
    }

    private static OnlineMessages.RoundEnd end(int round, long now, long nextStart) {
        OnlineMessages.RoundEnd end = new OnlineMessages.RoundEnd();
        end.round = round;
        end.serverNow = now;
        end.nextRoundStartsAt = nextStart;
        return end;
    }
}
