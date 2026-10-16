package com.cube.memorygames.api.online;

import org.junit.Test;
import static org.junit.Assert.*;
import static com.cube.memorygames.api.online.RoundLaunchGate.Action.*;

public class RoundPreparationTest {
    @Test public void prepareDoesNotTickAndFirstFormalSameRoundStartsClock() {
        RoundClock clock = new RoundClock();
        clock.reset(45000L);
        assertTrue(clock.accept(frame(1, true, 45000), true, 100L));
        assertTrue(clock.isPreparing());
        assertFalse(clock.hasStarted());
        assertEquals(0L, clock.remainingMs(9000L));
        assertTrue(clock.accept(frame(1, true, 45000), true, 9000L));
        OnlineMessages.RoundStart formal = frame(1, false, 45000);
        assertTrue(clock.accept(formal, true, 10000L));
        assertFalse(formal.resuming);
        assertFalse(clock.isPreparing());
        assertEquals(45000L, clock.remainingMs(10000L));
        assertEquals(44000L, clock.remainingMs(11000L));
        assertFalse(clock.accept(frame(1, true, 45000), true, 12000L));
    }

    @Test public void reconnectReanchorsShortenedDurationWithoutDoubleDeductionOrExtension() {
        RoundClock clock = new RoundClock();
        clock.reset(45000L);
        clock.accept(frame(1, false, 45000), true, 100L);
        OnlineMessages.RoundStart replay = frame(1, false, 35000);
        assertTrue(clock.accept(replay, true, 10100L));
        assertTrue(replay.resuming);
        assertEquals(35000L, clock.remainingMs(10100L));
        clock.accept(frame(1, false, 45000), true, 11100L);
        assertEquals(34000L, clock.remainingMs(11100L));
        clock.accept(frame(1, false, 30000), true, 12100L);
        assertEquals(30000L, clock.remainingMs(12100L));
        assertEquals(0L, clock.remainingMs(42100L));
    }

    @Test public void oldServerAndNonPracticeNeverWaitForReady() {
        OnlineMessages.RoundStart old = OnlineMessages.payloadOf(
                "{\"t\":\"round_start\",\"d\":{\"matchId\":\"m1\",\"round\":1,\"durationMs\":45000}}",
                OnlineMessages.RoundStart.class);
        RoundClock clock = new RoundClock();
        assertTrue(clock.accept(old, true, 100L));
        assertTrue(clock.hasStarted());
        assertFalse(old.preparing);
        RoundLaunchGate gate = new RoundLaunchGate();
        assertEquals(PREPARE, gate.accept(old));
        assertEquals(START, gate.complete(old));
        assertTrue(gate.markLaunched(old));
        clock.reset(0L);
        assertTrue(clock.accept(frame(1, true, 45000), false, 100L));
        assertTrue(clock.hasStarted());
        assertFalse(clock.isPreparing());
    }

    @Test public void completedPreparationWaitsThenFormalLaunchesExactlyOnce() {
        RoundLaunchGate gate = new RoundLaunchGate();
        OnlineMessages.RoundStart prepare = frame(1, true, 45000);
        assertEquals(PREPARE, gate.accept(prepare));
        assertEquals(IGNORE, gate.accept(frame(1, true, 45000)));
        assertEquals(READY, gate.complete(prepare));
        assertFalse(gate.markLaunched(prepare));
        assertEquals(IGNORE, gate.complete(prepare));
        // A lost round_ready is retried on the reconnect preparation replay.
        assertEquals(READY, gate.accept(frame(1, true, 45000)));
        OnlineMessages.RoundStart formal = frame(1, false, 45000);
        assertEquals(START, gate.accept(formal));
        assertTrue(gate.markLaunched(formal));
        assertFalse(gate.markLaunched(formal));
        formal.resuming = true;
        assertEquals(IGNORE, gate.accept(formal));
        assertEquals(IGNORE, gate.accept(frame(1, true, 45000)));
    }

    @Test public void formalDuringTutorialOrCountdownDoesNotRepeatPreparation() {
        RoundLaunchGate gate = new RoundLaunchGate();
        OnlineMessages.RoundStart prepare = frame(2, true, 45000);
        assertEquals(PREPARE, gate.accept(prepare));
        OnlineMessages.RoundStart formal = frame(2, false, 44000);
        assertEquals(IGNORE, gate.accept(formal));
        assertTrue(gate.canComplete(prepare));
        assertEquals(START, gate.complete(prepare));
        assertSame(formal, gate.formalFrame());
        assertTrue(gate.markLaunched(formal));
    }

    @Test public void retainedGateRestoresTutorialTokenAndReadyWithoutDeadlock() {
        RoundLaunchGate retained = new RoundLaunchGate();
        OnlineMessages.RoundStart tutorial = frame(1, true, 45000);
        retained.accept(tutorial);
        // New activity reuses the same pure-Java gate and tutorial token.
        assertEquals(IGNORE, retained.accept(frame(1, true, 45000)));
        assertTrue(retained.canComplete(tutorial));
        assertEquals(READY, retained.complete(tutorial));
        assertEquals(READY, retained.accept(frame(1, true, 45000)));
        assertEquals(START, retained.accept(frame(1, false, 45000)));
    }

    @Test public void formalRestoreBypassesCountdownButFreshLegacyRoundDoesNot() {
        RoundLaunchGate gate = new RoundLaunchGate();
        OnlineMessages.RoundStart resumed = frame(2, false, 13000);
        resumed.resuming = true;
        assertEquals(START, gate.accept(resumed));
        assertTrue(gate.markLaunched(resumed));
        assertEquals(IGNORE, gate.accept(resumed));
        gate.clear();
        assertEquals(PREPARE, gate.accept(resumed, false)); // ranked/friend keep their prior flow
        gate.clear();
        assertEquals(PREPARE, gate.accept(frame(3, false, 45000)));
    }

    @Test public void cancelledSupersededAndOldMatchCallbacksCannotCompletePreparation() {
        RoundLaunchGate gate = new RoundLaunchGate();
        OnlineMessages.RoundStart old = frame(1, true, 45000);
        gate.accept(old);
        gate.clear();
        assertEquals(IGNORE, gate.complete(old));
        OnlineMessages.RoundStart next = frame(1, true, 45000);
        next.matchId = "m2";
        assertEquals(PREPARE, gate.accept(next));
        assertEquals(IGNORE, gate.complete(old));
        assertFalse(RoundIntermission.canLaunch(old, "m2", 1, null));
        assertFalse(RoundIntermission.canLaunch(old, null, 1, null));
        gate.accept(frame(2, true, 45000));
        assertEquals(IGNORE, gate.complete(next));
    }

    @Test public void resetClockRemovesRunningOrPreparingState() {
        RoundClock clock = new RoundClock();
        clock.accept(frame(3, true, 45000), true, 100L);
        clock.reset(0L);
        assertFalse(clock.isPreparing());
        assertFalse(clock.hasStarted());
        assertEquals(0L, clock.durationMs());
        OnlineMessages.RoundStart next = frame(1, false, 20000);
        assertTrue(clock.accept(next, true, 200L));
        assertFalse(next.resuming);
        assertFalse(clock.accept(frame(0, false, 20000), true, 200L));
    }

    @Test public void oldMatchHighRoundCannotPassSessionIdentityGuard() {
        RoundClock clock = new RoundClock();
        OnlineMessages.RoundStart active = frame(1, true, 45000);
        assertTrue(RoundClock.belongsToMatch(active, "m1"));
        clock.accept(active, true, 100L);
        OnlineMessages.RoundStart stale = frame(5, false, 45000);
        stale.matchId = "old-match";
        assertFalse(RoundClock.belongsToMatch(stale, "m1"));
        assertFalse(RoundClock.belongsToMatch(stale, null));
        assertFalse(RoundClock.belongsToMatch(null, "m1"));
        assertTrue(clock.isPreparing());
        OnlineMessages.RoundStart formal = frame(1, false, 45000);
        assertTrue(RoundClock.belongsToMatch(formal, "m1"));
        assertTrue(clock.accept(formal, true, 200L));
        assertFalse(formal.resuming);
    }

    @Test public void capabilityAndReadyUseAgreedWirePayloads() {
        assertEquals("{\"v\":1,\"t\":\"match_start\",\"d\":{\"prepareRounds\":true}}",
                OnlineMessages.envelope(OnlineMessages.T_MATCH_START, new OnlineMessages.MatchStart()));
        OnlineMessages.RoundReady ready = new OnlineMessages.RoundReady();
        ready.matchId = "m1";
        ready.round = 2;
        assertEquals("{\"v\":1,\"t\":\"round_ready\",\"d\":{\"matchId\":\"m1\",\"round\":2}}",
                OnlineMessages.envelope(OnlineMessages.T_ROUND_READY, ready));
        OnlineMessages.RoundStart local = frame(1, false, 1000);
        local.resuming = true;
        assertFalse(OnlineMessages.envelope(OnlineMessages.T_ROUND_START, local).contains("resuming"));
    }

    private static OnlineMessages.RoundStart frame(int round, boolean preparing, int durationMs) {
        OnlineMessages.RoundStart frame = new OnlineMessages.RoundStart();
        frame.matchId = "m1";
        frame.gameId = "22";
        frame.round = round;
        frame.preparing = preparing;
        frame.durationMs = durationMs;
        return frame;
    }
}
