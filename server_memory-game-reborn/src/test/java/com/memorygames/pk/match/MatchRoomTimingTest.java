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

import java.util.ArrayList;
import java.util.List;
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
        final List<Runnable> dbTasks = new ArrayList<>();
        final MatchRoom room;
        final boolean bot;
        long startedAt;

        Fixture(String mode, boolean bot, int rounds) {
            this.bot = bot;
            properties.getMatch().setRoundsPerMatch(rounds);
            properties.setRoundDurationOverrideMs(DURATION);
            properties.setBotLevelIntervalOverrideMs(40);
            properties.getTiming().setMatchFoundDelayMs(0);
            properties.getTiming().setRoundGraceMs(GRACE);
            UserAccount a = user(1);
            room = new MatchRoom(a, endpoint(sent), bot ? null : user(2), new PlayerEndpoint.Bot(),
                    "Bot", mode, 1000, 1.0, 1.0,
                    new GameCatalog(properties), mock(UserService.class), new Rating(properties), properties,
                    MatchTerms.forVersus(properties), scheduler, dbTasks::add, ignored -> {},
                    mock(SettlementService.class), null, mock(EventRing.class), new AdminMetrics()) {
                @Override long now() { return clock.get(); }
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
