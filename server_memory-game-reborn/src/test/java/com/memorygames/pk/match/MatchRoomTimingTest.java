package com.memorygames.pk.match;

import com.memorygames.pk.admin.AdminMetrics;
import com.memorygames.pk.admin.EventRing;
import com.memorygames.pk.config.PkProperties;
import com.memorygames.pk.game.GameCatalog;
import com.memorygames.pk.protocol.ProtocolCodec;
import com.memorygames.pk.rating.Rating;
import com.memorygames.pk.user.UserAccount;
import com.memorygames.pk.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Delayed;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** No sleeps, database, sockets or real timer threads: exercise timer dequeue races explicitly. */
class MatchRoomTimingTest {
    private static final long DURATION = 1000;
    private static final long GRACE = 10000;

    @Test
    void botFinishesAtOfficialDeadlineAndCompletedHumanDoesNotWaitForGrace() {
        try (Fixture f = new Fixture(MatchRoom.MODE_PRACTICE, true, 2)) {
            f.start();
            f.complete(1, 1); // A premature client complete must not truncate the bot.
            f.scheduler.advanceTo(f.startedAt + DURATION - 1);
            assertEquals(0, f.frames(ProtocolCodec.T_ROUND_END).size());
            assertTrue(f.room.adminSnapshot().seats[1].totalScore > 0, "bot gets its normal playing time");
            f.scheduler.advanceTo(f.startedAt + DURATION);
            assertEquals(1, f.frames(ProtocolCodec.T_ROUND_END).size());
            assertEquals(2000, f.lastEnd().nextRoundStartsAt - f.lastEnd().serverNow);
            assertTrue(f.room.adminSnapshot().roundEnded);
        }
    }

    @Test
    void humanCompleteAfterBotDeadlineEndsImmediatelyButMissingCompleteKeepsGrace() {
        try (Fixture f = new Fixture(MatchRoom.MODE_PRACTICE, true, 2)) {
            f.start();
            f.scheduler.advanceTo(f.startedAt + DURATION);
            assertEquals(0, f.frames(ProtocolCodec.T_ROUND_END).size());
            f.clock.addAndGet(17);
            f.complete(1, 1);
            assertEquals(1, f.frames(ProtocolCodec.T_ROUND_END).size());
            assertEquals(f.clock.get(), f.lastEnd().serverNow);
        }
        try (Fixture f = new Fixture(MatchRoom.MODE_PRACTICE, true, 2)) {
            f.start();
            f.scheduler.advanceTo(f.startedAt + DURATION + GRACE - 1);
            assertEquals(0, f.frames(ProtocolCodec.T_ROUND_END).size());
            f.scheduler.advanceTo(f.startedAt + DURATION + GRACE);
            assertEquals(1, f.frames(ProtocolCodec.T_ROUND_END).size());
        }
    }

    @Test
    void overdueBotTickCannotScoreEvenIfDeadlineCallbackHasNotRun() {
        try (Fixture f = new Fixture(MatchRoom.MODE_PRACTICE, true, 2)) {
            f.start();
            ManualTask botAnswer = f.scheduler.pendingBefore(f.startedAt + DURATION);
            f.clock.set(f.startedAt + DURATION); // Simulate an occupied scheduler/room lock.
            botAnswer.command.run();
            assertEquals(0, f.room.adminSnapshot().seats[1].totalScore);
            assertEquals(0, f.frames(ProtocolCodec.T_OPPONENT_SCORE).size());
            f.complete(1, 1); // Tick marked bot done without scoring; no grace wait.
            assertEquals(1, f.frames(ProtocolCodec.T_ROUND_END).size());
            botAnswer.command.run(); // Already dequeued stale answer is also harmless.
            assertEquals(0, f.room.adminSnapshot().seats[1].totalScore);
        }
    }

    @Test
    void duplicateCompletesAndStaleDeadlinesCannotEndOrScheduleAnotherRound() {
        try (Fixture f = new Fixture(MatchRoom.MODE_PRACTICE, true, 2)) {
            f.start();
            ManualTask oldGameplayDeadline = f.scheduler.at(f.startedAt + DURATION);
            ManualTask oldGraceDeadline = f.scheduler.at(f.startedAt + DURATION + GRACE);
            f.complete(1, 1);
            f.scheduler.advanceTo(f.startedAt + DURATION);
            long nextAt = f.lastEnd().nextRoundStartsAt;
            for (int i = 0; i < 4; i++) {
                f.complete(1, 1);
                oldGameplayDeadline.command.run();
                oldGraceDeadline.command.run();
            }
            assertEquals(1, f.frames(ProtocolCodec.T_ROUND_END).size());
            assertEquals(1, f.scheduler.activeAt(nextAt));
            assertEquals(0, f.dbTasks.size());
            ManualTask nextStart = f.scheduler.at(nextAt);
            f.scheduler.advanceTo(nextAt);
            oldGameplayDeadline.command.run();
            oldGraceDeadline.command.run();
            nextStart.command.run();
            f.complete(1, 1); // Old client message after next round started.
            assertEquals(2, f.frames(ProtocolCodec.T_ROUND_START).size());
            assertEquals(1, f.frames(ProtocolCodec.T_ROUND_END).size());
            assertEquals(2, f.room.adminSnapshot().round);
            assertFalse(f.room.adminSnapshot().roundEnded);
            assertEquals(0, f.dbTasks.size());
        }
    }

    @Test
    void humanLevelWinsAreRejectedAfterForcedRoundEndEvenInsideScoreTolerance() {
        try (Fixture f = new Fixture(MatchRoom.MODE_RANKED, false, 2)) {
            f.properties.getTiming().setRoundGraceMs(0);
            f.properties.getTiming().setScoreAcceptGraceMs(2000);
            f.start();
            f.scheduler.advanceTo(f.startedAt + DURATION);
            assertTrue(f.room.adminSnapshot().roundEnded);
            ProtocolCodec.LevelWin win = new ProtocolCodec.LevelWin();
            win.round = 1;
            win.level = 1;
            f.room.onLevelWin(1, win);
            f.room.onLevelWin(2, win);
            assertEquals(0, f.room.adminSnapshot().seats[0].totalScore);
            assertEquals(0, f.room.adminSnapshot().seats[1].totalScore);
        }
    }

    @Test
    void onlyPracticeBotsUseConfigurableShortGapAndRealPlayersRetainOriginalGap() {
        assertGap(MatchRoom.MODE_PRACTICE, true, null, 2000);
        assertGap(MatchRoom.MODE_PRACTICE, true, 375L, 375);
        assertGap(MatchRoom.MODE_RANKED, true, 375L, 6000);
        assertGap(MatchRoom.MODE_FRIEND, false, 375L, 6000);
        assertGap(MatchRoom.MODE_RANKED, false, 375L, 6000);
        assertGap(MatchRoom.MODE_PRACTICE, false, 375L, 6000);
    }

    private void assertGap(String mode, boolean bot, Long configured, long expected) {
        try (Fixture f = new Fixture(mode, bot, 2)) {
            if (configured != null) f.properties.getTiming().setPracticeRoundIntermissionMs(configured);
            f.start();
            f.complete(1, 1);
            if (bot) f.scheduler.advanceTo(f.startedAt + DURATION);
            else f.complete(2, 1);
            ProtocolCodec.RoundEnd end = f.lastEnd();
            assertEquals(expected, end.nextRoundStartsAt - end.serverNow, mode + " bot=" + bot);
            assertEquals(1, f.scheduler.activeAt(end.nextRoundStartsAt));
            f.scheduler.advanceTo(end.nextRoundStartsAt - 1);
            assertEquals(1, f.room.adminSnapshot().round);
            f.scheduler.advanceTo(end.nextRoundStartsAt);
            assertEquals(2, f.room.adminSnapshot().round);
            assertEquals(end.nextRoundStartsAt.longValue(), f.lastStart().serverNow);
            assertEquals(end.nextGameId, f.lastStart().gameId);
        }
    }

    @Test
    void reconnectAndSnapshotRetainActualIntermissionTargetWithoutOldRoundStart() {
        try (Fixture f = new Fixture(MatchRoom.MODE_RANKED, false, 2)) {
            f.start();
            f.complete(1, 1);
            f.complete(2, 1);
            ProtocolCodec.RoundEnd original = f.lastEnd();
            f.room.onDisconnect(1);
            f.clock.addAndGet(500);
            List<String> resumed = new ArrayList<>();
            f.room.onReconnect(1, endpoint(resumed));
            assertEquals(1, resumed.size());
            assertEquals(ProtocolCodec.T_ROUND_END, decode(resumed.get(0)).t);
            ProtocolCodec.RoundEnd replay = decode(resumed.get(0)).payload(ProtocolCodec.RoundEnd.class);
            assertEquals(original.nextRoundStartsAt, replay.nextRoundStartsAt);
            assertEquals(original.nextGameId, replay.nextGameId);
            assertEquals(f.clock.get(), replay.serverNow);
            assertEquals(original.myScore, replay.myScore);
            MatchRoom.Snapshot snapshot = f.room.adminSnapshot();
            assertEquals(replay.nextRoundStartsAt.longValue(), snapshot.nextRoundStartsAt);
            assertEquals(replay.nextGameId, snapshot.nextGameId);
            assertEquals(replay.serverNow.longValue(), snapshot.serverNow);
            assertEquals(0, snapshot.roundEndsInMs);
            assertTrue(snapshot.roundEnded);
            f.scheduler.advanceTo(replay.nextRoundStartsAt);
            assertEquals(2, f.room.adminSnapshot().round);
        }
    }

    @Test
    void slowRoundEndDeliveryDoesNotPushBackScheduledIntermissionTarget() {
        try (Fixture f = new Fixture(MatchRoom.MODE_RANKED, false, 2)) {
            f.start();
            f.room.onReconnect(1, new PlayerEndpoint() {
                @Override public void send(String json) {
                    f.sent.add(json);
                    if (ProtocolCodec.T_ROUND_END.equals(decode(json).t)) f.clock.addAndGet(7000);
                }
                @Override public boolean isOpen() { return true; }
            });
            f.complete(1, 1);
            f.complete(2, 1);
            ProtocolCodec.RoundEnd end = f.lastEnd();
            assertEquals(f.startedAt + 6000, end.nextRoundStartsAt);
            assertTrue(f.clock.get() > end.nextRoundStartsAt);
            assertEquals(1, f.scheduler.activeAt(end.nextRoundStartsAt));
            // The scheduler can start immediately once it obtains the room,
            // rather than sleeping another intermission after the slow send.
            f.scheduler.advanceTo(f.clock.get());
            assertEquals(2, f.room.adminSnapshot().round);
            assertEquals(f.startedAt + 7000, f.lastStart().serverNow);
        }
    }

    @Test
    void activeReconnectOnlyReceivesRemainingOfficialTime() {
        try (Fixture f = new Fixture(MatchRoom.MODE_PRACTICE, true, 2)) {
            f.start();
            f.scheduler.advanceTo(f.startedAt + 250);
            List<String> resumed = new ArrayList<>();
            f.room.onReconnect(1, endpoint(resumed));
            ProtocolCodec.RoundStart start = decode(resumed.get(0)).payload(ProtocolCodec.RoundStart.class);
            assertEquals(750, start.durationMs);
            assertEquals(f.clock.get(), start.serverNow);
            assertEquals(f.startedAt + DURATION, start.serverNow + start.durationMs);
        }
    }

    @Test
    void finalRoundHasZeroTargetNoNextGameAndExactlyOneSettlementDispatch() {
        try (Fixture f = new Fixture(MatchRoom.MODE_PRACTICE, true, 1)) {
            f.start();
            ManualTask gameplay = f.scheduler.at(f.startedAt + DURATION);
            ManualTask grace = f.scheduler.at(f.startedAt + DURATION + GRACE);
            f.complete(1, 1);
            f.scheduler.advanceTo(f.startedAt + DURATION);
            ProtocolCodec.RoundEnd end = f.lastEnd();
            assertEquals(0L, end.nextRoundStartsAt);
            assertNull(end.nextGameId);
            String wire = ProtocolCodec.encode(ProtocolCodec.T_ROUND_END, end);
            assertFalse(wire.contains("nextGameId"));
            assertEquals(1, f.dbTasks.size());
            f.complete(1, 1);
            gameplay.command.run();
            grace.command.run();
            f.scheduler.advanceTo(f.clock.get() + 100000);
            assertEquals(1, f.frames(ProtocolCodec.T_ROUND_END).size());
            assertEquals(1, f.frames(ProtocolCodec.T_ROUND_START).size());
            assertEquals(1, f.dbTasks.size());
            assertEquals(0, f.room.adminSnapshot().nextRoundStartsAt);
        }
    }

    @Test
    void oldRoundEndPayloadStillDecodesWithAbsentOptionalFields() {
        ProtocolCodec.RoundEnd old = decode("{\"v\":1,\"t\":\"round_end\",\"d\":{\"round\":1,\"myScore\":12,\"oppScore\":8}}")
                .payload(ProtocolCodec.RoundEnd.class);
        assertEquals(12, old.myScore);
        assertNull(old.nextRoundStartsAt);
        assertNull(old.serverNow);
        assertNull(old.nextGameId);
    }

    @Test
    void practicePreparationKeepsClocksAndBotStoppedUntilMatchingReady() {
        try (Fixture f = new Fixture(MatchRoom.MODE_PRACTICE, true, 2)) {
            f.room.start();
            f.room.onMatchStart(1, true);
            f.scheduler.advanceTo(f.clock.get());
            assertTrue(f.lastStart().preparing);
            assertEquals(0, f.room.adminSnapshot().round);
            f.scheduler.advanceTo(f.clock.get() + 20000);
            assertEquals(0, f.room.adminSnapshot().seats[1].totalScore);
            assertTrue(f.frames(ProtocolCodec.T_ROUND_END).isEmpty());
            ProtocolCodec.RoundReady ready = new ProtocolCodec.RoundReady();
            ready.matchId = "stale";
            ready.round = 1;
            f.room.onRoundReady(1, ready);
            ready.matchId = f.room.matchId;
            f.room.onRoundReady(2, ready);
            ready.round = 2;
            f.room.onRoundReady(1, ready);
            assertEquals(1, f.frames(ProtocolCodec.T_ROUND_START).size());
            ready.round = 1;
            long startedAt = f.clock.get();
            f.room.onRoundReady(1, ready);
            assertFalse(f.lastStart().preparing);
            assertEquals(startedAt, f.lastStart().serverNow);
            assertEquals(DURATION, f.lastStart().durationMs);
            f.room.onRoundReady(1, ready);
            assertEquals(2, f.frames(ProtocolCodec.T_ROUND_START).size());
            f.scheduler.advanceTo(startedAt + DURATION - 1);
            assertTrue(f.room.adminSnapshot().seats[1].totalScore > 0);
            f.complete(1, 1);
            f.scheduler.advanceTo(startedAt + DURATION);
            long nextAt = f.lastEnd().nextRoundStartsAt;
            f.scheduler.advanceTo(nextAt);
            assertTrue(f.lastStart().preparing);
            assertEquals(2, f.lastStart().round);
            f.room.onRoundReady(1, ready); // Stale round-1 callback cannot start round 2.
            assertEquals(1, f.room.adminSnapshot().round);
            ready.round = 2;
            f.room.onRoundReady(1, ready);
            assertFalse(f.lastStart().preparing);
            assertEquals(2, f.room.adminSnapshot().round);
        }
    }

    @Test
    void preparationReconnectReplaysPreparationAndTimeoutOrResignCannotStartLater() {
        for (boolean resign : new boolean[]{false, true}) {
            try (Fixture f = new Fixture(MatchRoom.MODE_PRACTICE, true, 2)) {
                f.room.start();
                f.room.onMatchStart(1, true);
                f.scheduler.advanceTo(f.clock.get());
                List<String> resumed = new ArrayList<>();
                f.room.onDisconnect(1);
                f.room.onReconnect(1, endpoint(resumed));
                assertTrue(decode(resumed.get(0)).payload(ProtocolCodec.RoundStart.class).preparing);
                long time = f.clock.get();
                if (resign) f.room.onResign(1);
                f.scheduler.advanceTo(time + 120000);
                assertEquals(1, f.dbTasks.size());
                f.dbTasks.get(0).run();
                assertTrue(resumed.stream().anyMatch(frame -> ProtocolCodec.T_MATCH_CANCELLED.equals(decode(frame).t)));
                ProtocolCodec.RoundReady ready = new ProtocolCodec.RoundReady();
                ready.matchId = f.room.matchId;
                ready.round = 1;
                f.room.onRoundReady(1, ready);
                assertEquals(0, f.room.adminSnapshot().round);
            }
        }
    }

    @ParameterizedTest(name = "accept timeout: acceptedA={0}, acceptedB={1}")
    @CsvSource({"false, false", "true, false", "false, true"})
    void acceptTimeoutCancelsBothHumansOnceAndLateStartsCannotReviveRoom(boolean acceptedA, boolean acceptedB) {
        try (Fixture f = new Fixture(MatchRoom.MODE_RANKED, false, 2)) {
            f.room.start();
            if (acceptedA) f.room.onMatchStart(1);
            if (acceptedB) f.room.onMatchStart(2);
            long deadline = f.clock.get() + f.properties.getTiming().getAcceptTimeoutMs();
            ManualTask acceptTimeout = f.scheduler.at(deadline);
            doAnswer(ignored -> {
                for (List<String> sent : List.of(f.sent, f.sentB)) {
                    assertEquals(List.of(ProtocolCodec.T_MATCH_FOUND),
                            sent.stream().map(json -> decode(json).t).toList(),
                            "release callback must precede either cancellation");
                }
                return null;
            }).when(f.settlementCallback).onSettled(f.room);

            f.scheduler.advanceTo(deadline - 1);
            assertTrue(f.dbTasks.isEmpty(), "accept timeout must not fire early");
            verify(f.settlement, never()).abortMatch(anyString());
            verifyNoInteractions(f.settlementCallback);
            for (List<String> sent : List.of(f.sent, f.sentB)) {
                assertEquals(List.of(ProtocolCodec.T_MATCH_FOUND),
                        sent.stream().map(json -> decode(json).t).toList());
            }

            f.scheduler.advanceTo(deadline);
            assertEquals(1, f.dbTasks.size(), "timeout dispatches exactly one abort");
            verify(f.settlement, never()).abortMatch(anyString());
            verifyNoInteractions(f.settlementCallback);
            // Replayed dequeued timers and late STARTs must be harmless even
            // while the abort is still waiting for the DB executor.
            for (int i = 0; i < 3; i++) {
                acceptTimeout.command.run();
                f.room.onMatchStart(1);
                f.room.onMatchStart(2);
            }
            f.scheduler.advanceTo(deadline);
            assertEquals(1, f.dbTasks.size());
            assertEquals(0, f.room.adminSnapshot().round);
            assertEquals(1, f.scheduler.tasks.size(), "late START must not schedule a round");
            for (List<String> sent : List.of(f.sent, f.sentB)) {
                assertEquals(List.of(ProtocolCodec.T_MATCH_FOUND),
                        sent.stream().map(json -> decode(json).t).toList(),
                        "cancellation must wait for abort and seat release");
            }

            f.dbTasks.get(0).run();
            for (int i = 0; i < 3; i++) {
                acceptTimeout.command.run();
                f.room.onMatchStart(1);
                f.room.onMatchStart(2);
            }
            f.scheduler.advanceTo(deadline + 100000);
            assertEquals(1, f.dbTasks.size(), "replays must not dispatch another abort or settlement");
            assertEquals(0, f.room.adminSnapshot().round);
            assertTrue(f.room.adminSnapshot().roundEnded);
            assertEquals(0, f.room.adminSnapshot().nextRoundStartsAt);
            assertEquals(1, f.scheduler.tasks.size(), "cancelled room must not gain new timers");
            for (List<String> sent : List.of(f.sent, f.sentB)) {
                assertEquals(List.of(ProtocolCodec.T_MATCH_FOUND, ProtocolCodec.T_MATCH_CANCELLED),
                        sent.stream().map(json -> decode(json).t).toList(),
                        "each human gets one cancellation, no opponent_start/round_start/round_end/match_end");
                assertEquals(Map.of(), decode(sent.get(1)).data, "cancellation payload must stay empty");
            }
            InOrder cleanup = inOrder(f.settlement, f.settlementCallback);
            cleanup.verify(f.settlement).openMatch(f.room.matchId, MatchRoom.MODE_RANKED,
                    1L, 2L, f.properties.getEconomy().getEntryFee());
            cleanup.verify(f.settlement).abortMatch(f.room.matchId);
            cleanup.verify(f.settlementCallback).onSettled(f.room);
            verify(f.settlement, never()).settle(any());
            verifyNoMoreInteractions(f.settlement, f.settlementCallback);
            // This fixture observes the release callback, not MatchManager's
            // room/user maps or Matchmaker's queue; it cannot prove requeue behavior.
        }
    }

    @Test
    void dequeuedAcceptTimeoutCannotCancelAlreadyAcceptedPreparation() {
        try (Fixture f = new Fixture(MatchRoom.MODE_PRACTICE, true, 2)) {
            f.room.start();
            ManualTask acceptTimeout = f.scheduler.at(f.clock.get() + f.properties.getTiming().getAcceptTimeoutMs());
            f.room.onMatchStart(1, true);
            f.scheduler.advanceTo(f.clock.get());
            acceptTimeout.command.run();
            assertTrue(f.lastStart().preparing);
            assertEquals(0, f.dbTasks.size());
            ProtocolCodec.RoundReady ready = new ProtocolCodec.RoundReady();
            ready.matchId = f.room.matchId;
            ready.round = 1;
            f.room.onRoundReady(1, ready);
            assertFalse(f.lastStart().preparing);
            assertEquals(1, f.room.adminSnapshot().round);
        }
    }

    @Test
    void stalePreparationTimerCannotForfeitPlayingRoundAndLaterPreparationIsBounded() {
        try (Fixture f = new Fixture(MatchRoom.MODE_PRACTICE, true, 2)) {
            f.room.start();
            f.room.onMatchStart(1, true);
            f.scheduler.advanceTo(f.clock.get());
            ManualTask timeout = f.scheduler.at(f.clock.get() + 120000);
            ProtocolCodec.RoundReady ready = new ProtocolCodec.RoundReady();
            ready.matchId = f.room.matchId;
            ready.round = 1;
            f.room.onRoundReady(1, ready);
            timeout.command.run();
            assertEquals(0, f.dbTasks.size());
            f.complete(1, 1);
            f.scheduler.advanceTo(f.clock.get() + DURATION);
            f.scheduler.advanceTo(f.lastEnd().nextRoundStartsAt);
            assertTrue(f.lastStart().preparing);
            timeout.command.run();
            assertEquals(0, f.dbTasks.size());
            long deadline = f.clock.get() + 120000;
            f.scheduler.advanceTo(deadline);
            assertEquals(1, f.dbTasks.size());
            ready.round = 2;
            f.room.onRoundReady(1, ready);
            assertEquals(1, f.room.adminSnapshot().round);
        }
    }

    @Test
    void preparationCapabilityDoesNotChangeHumanOrRankedBotMatches() {
        for (String mode : new String[]{MatchRoom.MODE_RANKED, MatchRoom.MODE_FRIEND}) {
            for (boolean bot : new boolean[]{false, true}) {
                try (Fixture f = new Fixture(mode, bot, 1)) {
                    f.room.start();
                    f.room.onMatchStart(1, true);
                    if (!bot) f.room.onMatchStart(2, true);
                    f.scheduler.advanceTo(f.clock.get());
                    assertFalse(f.lastStart().preparing);
                    assertEquals(1, f.room.adminSnapshot().round);
                }
            }
        }
    }

    @Test
    void schulteBotStopsAfter48TargetsAndHumanCannotScoreTarget49() {
        try (Fixture f = new Fixture(MatchRoom.MODE_PRACTICE, true, 1,
                "22", 45000, 1, 1.0d, 1.0d)) {
            f.answers = new java.util.Random() {
                @Override public double nextDouble() { return 0.5d; }
            };
            f.start();
            f.scheduler.advanceTo(f.startedAt + 44000);
            int botScore = f.room.adminSnapshot().seats[1].totalScore;
            assertTrue(botScore > 0);
            assertEquals(48 * 49, botScore);
            ProtocolCodec.LevelWin win = new ProtocolCodec.LevelWin();
            win.round = 1;
            win.level = 48;
            f.room.onLevelWin(1, win);
            assertEquals(96, f.room.adminSnapshot().seats[0].totalScore);
            f.scheduler.advanceTo(f.startedAt + 44400);
            win.level = 49;
            f.room.onLevelWin(1, win);
            assertEquals(96, f.room.adminSnapshot().seats[0].totalScore);
            assertEquals(botScore, f.room.adminSnapshot().seats[1].totalScore);
            // Both sides keep their full round window after exhausting the board.
            assertTrue(f.frames(ProtocolCodec.T_ROUND_END).isEmpty());
        }
    }

    @Test
    void schulteBotWrongAnswerAddsTwoSecondRecoveryOnlyToThisGame() {
        for (String game : new String[]{"22", "19"}) {
            try (Fixture f = new Fixture(MatchRoom.MODE_PRACTICE, true, 1,
                    game, 45000, 100, 0.0d, 1.0d)) {
                f.answers = new java.util.Random() {
                    @Override public double nextDouble() { return 0.5d; }
                };
                f.start();
                ManualTask first = f.scheduler.pendingBefore(f.startedAt + 45000);
                f.scheduler.advanceTo(first.at);
                ManualTask second = f.scheduler.pendingBefore(f.startedAt + 45000);
                long delay = second.at - first.at;
                if ("22".equals(game)) assertTrue(delay >= 2091 && delay <= 2208, "delay=" + delay);
                else assertTrue(delay >= 91 && delay <= 208, "delay=" + delay);
                assertEquals(0, f.room.adminSnapshot().seats[1].totalScore);
            }
        }
    }

    @Test
    void schulteDefault45SecondScoresRemainBoundedAndTieredAcross200Seeds() {
        double previous = -1;
        for (PkProperties.BotDifficulty tier : new PkProperties().getPractice().getDifficulties()) {
            long sum = 0;
            for (int seed = 0; seed < 200; seed++) {
                try (Fixture f = new Fixture(MatchRoom.MODE_PRACTICE, true, 1,
                        "22", 45000, 0, tier.getAccuracy(), tier.getPace())) {
                    f.answers = new java.util.Random(seed);
                    f.start();
                    f.scheduler.advanceTo(f.startedAt + 45000);
                    int score = f.room.adminSnapshot().seats[1].totalScore;
                    assertTrue(score >= 0 && score <= 2352);
                    sum += score;
                }
            }
            double mean = sum / 200.0;
            assertTrue(mean > previous, tier.getId() + " mean=" + mean + " previous=" + previous);
            assertTrue(mean < 1800, tier.getId() + " must not routinely exhaust the board");
            System.out.println("Schulte " + tier.getId() + " 200-seed mean score=" + mean);
            previous = mean;
        }
    }

    @Test
    void defaultSchultePaceIsSlowerWithoutChangingOtherGames() {
        GameCatalog catalog = new GameCatalog(new PkProperties());
        assertEquals(1800, catalog.botLevelIntervalOf(catalog.byId("22")));
        assertEquals(1500, catalog.botLevelIntervalOf(catalog.byId("19")));
        assertEquals(4000, catalog.botLevelIntervalOf(catalog.byId("0")));
        assertEquals(48, catalog.byId("22").maxLevel());
        assertEquals(Integer.MAX_VALUE, catalog.byId("0").maxLevel());
    }

    private static PlayerEndpoint endpoint(List<String> frames) {
        return new PlayerEndpoint() {
            @Override public void send(String json) { frames.add(json); }
            @Override public boolean isOpen() { return true; }
        };
    }

    private static Frame decode(String json) {
        ProtocolCodec.Envelope envelope = ProtocolCodec.parse(json);
        assertNotNull(envelope);
        return new Frame(envelope.t, envelope.d);
    }

    private static class Frame {
        final String t;
        final Object data;
        Frame(String t, Object data) { this.t = t; this.data = data; }
        <T> T payload(Class<T> type) { return ProtocolCodec.convert(data, type); }
    }

    private static class Fixture implements AutoCloseable {
        final AtomicLong clock = new AtomicLong(1700000000000L);
        final PkProperties properties = new PkProperties();
        final ManualScheduler scheduler = new ManualScheduler(clock);
        final List<String> sent = new ArrayList<>();
        final List<String> sentB = new ArrayList<>();
        final List<Runnable> dbTasks = new ArrayList<>();
        final SettlementService settlement = mock(SettlementService.class);
        final MatchRoom.SettlementCallback settlementCallback = mock(MatchRoom.SettlementCallback.class);
        final MatchRoom room;
        final boolean bot;
        long startedAt;
        java.util.Random answers;

        Fixture(String mode, boolean bot, int rounds) {
            this(mode, bot, rounds, "0", DURATION, 40, 1.0d, 1.0d);
        }

        Fixture(String mode, boolean bot, int rounds, String gameId, long duration,
                long botInterval, double accuracy, double pace) {
            this.bot = bot;
            properties.getMatch().setRoundsPerMatch(rounds);
            properties.setRoundDurationOverrideMs(duration);
            properties.setBotLevelIntervalOverrideMs(botInterval);
            properties.getTiming().setMatchFoundDelayMs(0);
            properties.getTiming().setRoundGraceMs(GRACE);
            UserAccount a = user(1);
            room = new MatchRoom(a, endpoint(sent), bot ? null : user(2), endpoint(sentB),
                    "Bot", mode, 1000, accuracy, pace,
                    new GameCatalog(properties) {
                        @Override public List<ProtocolRound> planRounds(long seed, int count) {
                            List<ProtocolRound> plan = new ArrayList<>();
                            for (int i = 0; i < count; i++) plan.add(new ProtocolRound(gameId, duration, seed + i));
                            return plan;
                        }
                    }, mock(UserService.class), new Rating(properties), properties,
                    MatchTerms.forVersus(properties), scheduler, dbTasks::add, settlementCallback,
                    settlement, null, mock(EventRing.class), new AdminMetrics()) {
                @Override long now() { return clock.get(); }
                @Override java.util.Random newBotRandom() {
                    return answers != null ? answers : super.newBotRandom();
                }
            };
        }

        static UserAccount user(long id) {
            UserAccount user = new UserAccount();
            user.id = id;
            user.nickname = "User" + id;
            user.elo = 1000;
            return user;
        }

        void start() {
            room.start();
            room.onMatchStart(1);
            if (!bot) room.onMatchStart(2);
            scheduler.advanceTo(clock.get());
            startedAt = lastStart().serverNow;
        }

        void complete(long user, int round) {
            ProtocolCodec.RoundComplete done = new ProtocolCodec.RoundComplete();
            done.round = round;
            room.onRoundComplete(user, done);
        }

        List<Frame> frames(String type) {
            return sent.stream().map(MatchRoomTimingTest::decode).filter(frame -> type.equals(frame.t)).toList();
        }

        ProtocolCodec.RoundEnd lastEnd() {
            List<Frame> ends = frames(ProtocolCodec.T_ROUND_END);
            return ends.get(ends.size() - 1).payload(ProtocolCodec.RoundEnd.class);
        }

        ProtocolCodec.RoundStart lastStart() {
            List<Frame> starts = frames(ProtocolCodec.T_ROUND_START);
            return starts.get(starts.size() - 1).payload(ProtocolCodec.RoundStart.class);
        }

        @Override public void close() { scheduler.shutdownNow(); }
    }

    private static class ManualScheduler extends ScheduledThreadPoolExecutor {
        final AtomicLong clock;
        final List<ManualTask> tasks = new ArrayList<>();

        ManualScheduler(AtomicLong clock) {
            super(1);
            this.clock = clock;
        }

        @Override public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            ManualTask task = new ManualTask(command, clock.get() + unit.toMillis(delay));
            tasks.add(task);
            return task;
        }

        void advanceTo(long target) {
            while (true) {
                ManualTask next = tasks.stream().filter(task -> !task.isDone() && task.at <= target)
                        .min(java.util.Comparator.comparingLong(task -> task.at)).orElse(null);
                if (next == null) break;
                clock.set(Math.max(clock.get(), next.at));
                // Call directly so exceptions propagate even if a deadline
                // cancels its own Future from inside endRound.
                next.command.run();
                next.cancel(false);
            }
            clock.set(target);
        }

        ManualTask at(long target) {
            return tasks.stream().filter(task -> !task.isDone() && task.at == target).findFirst().orElseThrow();
        }

        int activeAt(long target) {
            return (int) tasks.stream().filter(task -> !task.isDone() && task.at == target).count();
        }

        ManualTask pendingBefore(long target) {
            return tasks.stream().filter(task -> !task.isDone() && task.at < target).findFirst().orElseThrow();
        }
    }

    private static class ManualTask extends FutureTask<Void> implements ScheduledFuture<Void> {
        final Runnable command;
        final long at;

        ManualTask(Runnable command, long at) {
            super(command, null);
            this.command = command;
            this.at = at;
        }

        @Override public long getDelay(TimeUnit unit) { return 0; }
        @Override public int compareTo(Delayed other) { return 0; }
    }
}
