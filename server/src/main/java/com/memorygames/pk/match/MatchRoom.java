package com.memorygames.pk.match;

import com.memorygames.pk.config.PkProperties;
import com.memorygames.pk.game.GameCatalog;
import com.memorygames.pk.protocol.ProtocolCodec;
import com.memorygames.pk.rating.Rating;
import com.memorygames.pk.user.UserAccount;
import com.memorygames.pk.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One PK match: 5 rounds of seeded games, server-authoritative scoring
 * recomputed from the level-win stream (client parity with
 * Game1MemoryGridActivity.updateOnlineScore), bot opponents, disconnect
 * forfeits and the ELO/coin settlement.
 *
 * All state transitions are synchronized on the room; the settle callback
 * into {@link MatchManager} is invoked outside the lock to keep the lock
 * order manager -> room one-directional.
 */
public class MatchRoom {

    private static final Logger log = LoggerFactory.getLogger(MatchRoom.class);
    private static final int MAX_ANOMALIES = 3;

    public interface SettlementCallback {

        void onSettled(MatchRoom room);
    }

    /** Per-seat state inside the match. */
    public class Seat {

        public final UserAccount user;
        public final boolean isBot;
        public volatile PlayerEndpoint endpoint;
        public final int[] roundScores;
        public boolean roundDone;
        public boolean disconnected;
        /** Bot pacing cursor (bot brain only). */
        public int expectedLevel;
        /** Highest level this human actually reported (fails skip levels). */
        public int lastReportedLevel;
        public long lastLevelAt;
        public int anomalies;
        public boolean frozen;
        public ScheduledFuture<?> forfeitTask;

        Seat(UserAccount user, boolean isBot, PlayerEndpoint endpoint) {
            this.user = user;
            this.isBot = isBot;
            this.endpoint = endpoint;
            this.roundScores = new int[MatchRoom.this.rounds.size()];
        }

        public String nickname() {
            return this.user != null ? this.user.nickname : MatchRoom.this.botNickname;
        }

        public int elo() {
            return this.user != null ? this.user.elo : MatchRoom.this.botElo;
        }
    }

    public final String matchId;
    private final Seat[] seats = new Seat[2];
    private final List<GameCatalog.ProtocolRound> rounds;
    private final long matchSeed;
    private final String botNickname;
    private final int botElo;

    private final GameCatalog catalog;
    private final UserService users;
    private final Rating rating;
    private final PkProperties properties;
    private final ScheduledThreadPoolExecutor scheduler;
    private final SettlementCallback settlementCallback;

    private int currentRound = 0;
    private long roundStartedAt;
    private final AtomicBoolean settledOnce = new AtomicBoolean(false);
    private ScheduledFuture<?> roundDeadlineTask;
    private final List<ScheduledFuture<?>> botTasks = new ArrayList<>();

    public MatchRoom(UserAccount userA, PlayerEndpoint endpointA,
                     UserAccount userB, PlayerEndpoint endpointB,
                     String botNickname, GameCatalog catalog, UserService users,
                     Rating rating, PkProperties properties,
                     ScheduledThreadPoolExecutor scheduler,
                     SettlementCallback settlementCallback) {
        this.matchId = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        this.matchSeed = new Random().nextLong();
        this.rounds = catalog.planRounds(this.matchSeed, properties.getMatch().getRoundsPerMatch());
        this.botNickname = botNickname;
        this.botElo = userA.elo;
        this.catalog = catalog;
        this.users = users;
        this.rating = rating;
        this.properties = properties;
        this.scheduler = scheduler;
        this.settlementCallback = settlementCallback;
        this.seats[0] = new Seat(userA, false, endpointA);
        this.seats[1] = new Seat(userB, userB == null, userB == null ? new PlayerEndpoint.Bot() : endpointB);
    }

    // ------------------------------------------------------- match setup

    public void start() {
        synchronized (this) {
            if (this.settledOnce.get()) {
                return;
            }
            this.users.chargeEntryFee(this.seats[0].user, this.properties.getEconomy().getEntryFee());
            if (this.seats[1].user != null) {
                this.users.chargeEntryFee(this.seats[1].user, this.properties.getEconomy().getEntryFee());
            }
            for (int i = 0; i < 2; i++) {
                sendTo(i, ProtocolCodec.T_MATCH_FOUND, buildMatchFound(i));
            }
            scheduleRoundStart(1, this.properties.getTiming().getMatchFoundDelayMs());
        }
    }

    private ProtocolCodec.MatchFound buildMatchFound(int viewerIndex) {
        ProtocolCodec.MatchFound found = new ProtocolCodec.MatchFound();
        found.matchId = this.matchId;
        found.opponent = opponentInfo(1 - viewerIndex);
        found.rounds = new ProtocolCodec.RoundDesc[this.rounds.size()];
        for (int i = 0; i < this.rounds.size(); i++) {
            GameCatalog.ProtocolRound round = this.rounds.get(i);
            ProtocolCodec.RoundDesc desc = new ProtocolCodec.RoundDesc();
            desc.gameId = round.gameId;
            desc.durationMs = round.durationMs;
            desc.seed = round.seed;
            found.rounds[i] = desc;
        }
        return found;
    }

    private ProtocolCodec.OpponentInfo opponentInfo(int seatIndex) {
        ProtocolCodec.OpponentInfo info = new ProtocolCodec.OpponentInfo();
        Seat seat = this.seats[seatIndex];
        info.userId = seat.user != null ? seat.user.id : -1L;
        info.nickname = seat.nickname();
        info.elo = seat.elo();
        info.rank = seat.user != null ? this.rating.rankIndex(seat.user.elo) : this.rating.rankIndex(this.botElo);
        info.isBot = seat.isBot;
        return info;
    }

    // ------------------------------------------------------ round engine

    private void scheduleRoundStart(final int roundNumber, long delayMs) {
        this.scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                roundStart(roundNumber);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private void roundStart(int roundNumber) {
        synchronized (this) {
            if (this.settledOnce.get() || roundNumber != this.currentRound + 1) {
                return;
            }
            this.currentRound = roundNumber;
            GameCatalog.ProtocolRound round = this.rounds.get(roundNumber - 1);
            GameCatalog.GameSpec spec = this.catalog.byId(round.gameId);
            long now = System.currentTimeMillis();
            this.roundStartedAt = now;
            for (Seat seat : this.seats) {
                seat.roundDone = false;
                seat.expectedLevel = 1;
                seat.lastReportedLevel = 0;
                seat.lastLevelAt = now;
                seat.anomalies = 0;
                seat.frozen = false;
            }
            for (int i = 0; i < 2; i++) {
                ProtocolCodec.RoundStart start = new ProtocolCodec.RoundStart();
                start.matchId = this.matchId;
                start.round = roundNumber;
                start.gameId = round.gameId;
                start.durationMs = round.durationMs;
                start.serverNow = now;
                sendTo(i, ProtocolCodec.T_ROUND_START, start);
            }
            if (this.seats[1].isBot) {
                startBotBrain(spec, round.durationMs);
            }
            this.roundDeadlineTask = this.scheduler.schedule(new Runnable() {
                @Override
                public void run() {
                    endRound(true);
                }
            }, round.durationMs + this.properties.getTiming().getRoundGraceMs(), TimeUnit.MILLISECONDS);
        }
    }

    public void onLevelWin(long userId, ProtocolCodec.LevelWin msg) {
        synchronized (this) {
            int index = seatIndex(userId);
            if (index < 0 || this.settledOnce.get() || msg.round != this.currentRound) {
                return;
            }
            Seat seat = this.seats[index];
            if (seat.roundDone || seat.frozen) {
                return;
            }
            GameCatalog.GameSpec spec = this.catalog.byId(this.rounds.get(this.currentRound - 1).gameId);
            long now = System.currentTimeMillis();
            // Wrong answers advance the level without scoring, so reported
            // levels legitimately contain gaps (1,3,4,6,...). Require strictly
            // increasing levels capped by what the elapsed time plausibly
            // allows, instead of strict succession.
            long floorMs = this.catalog.minLevelIntervalOf(spec);
            long elapsed = now - this.roundStartedAt;
            boolean sequenceOk = msg.level > seat.lastReportedLevel
                    && msg.level <= (elapsed / floorMs) + 3;
            // The first answer of a round is not interval-checked: the server
            // cannot know when the client's challenge reveal ended, and fast
            // first answers are legitimate. Spam is still capped from the
            // second message on by the per-game human floor.
            boolean intervalOk = seat.lastReportedLevel == 0
                    || now - seat.lastLevelAt >= floorMs;
            if (!sequenceOk || !intervalOk) {
                seat.anomalies++;
                if (seat.anomalies > MAX_ANOMALIES) {
                    seat.frozen = true;
                    log.warn("match {} user {}: score stream frozen after {} anomalies",
                            this.matchId, userId, seat.anomalies);
                }
                return;
            }
            seat.lastReportedLevel = msg.level;
            seat.lastLevelAt = now;
            seat.roundScores[this.currentRound - 1] += spec.levelScore(msg.level);
            ProtocolCodec.OpponentScore score = new ProtocolCodec.OpponentScore();
            score.matchId = this.matchId;
            score.round = this.currentRound;
            score.score = totalScore(index);
            sendTo(1 - index, ProtocolCodec.T_OPPONENT_SCORE, score);
        }
    }

    public void onRoundComplete(long userId, ProtocolCodec.RoundComplete msg) {
        synchronized (this) {
            int index = seatIndex(userId);
            if (index < 0 || this.settledOnce.get() || msg.round != this.currentRound) {
                return;
            }
            this.seats[index].roundDone = true;
            if (this.seats[0].roundDone && this.seats[1].roundDone) {
                endRound(false);
            }
        }
    }

    private void endRound(boolean forced) {
        synchronized (this) {
            if (this.settledOnce.get() || this.currentRound == 0) {
                return;
            }
            if (this.roundDeadlineTask != null) {
                this.roundDeadlineTask.cancel(false);
                this.roundDeadlineTask = null;
            }
            stopBotBrain();
            for (int i = 0; i < 2; i++) {
                ProtocolCodec.RoundEnd end = new ProtocolCodec.RoundEnd();
                end.matchId = this.matchId;
                end.round = this.currentRound;
                // Cumulative match totals keep every score display (round
                // intermission, in-game HUD) on one consistent semantics.
                end.myScore = totalScore(i);
                end.oppScore = totalScore(1 - i);
                sendTo(i, ProtocolCodec.T_ROUND_END, end);
            }
            if (this.currentRound < this.rounds.size()) {
                scheduleRoundStart(this.currentRound + 1, this.properties.getTiming().getRoundIntermissionMs());
            } else {
                // Natural completion: decide by total score (-1), never by seat.
                settle(-1, false);
            }
        }
    }

    // ------------------------------------------------------------ controls

    public void onResign(long userId) {
        synchronized (this) {
            int index = seatIndex(userId);
            if (index < 0 || this.settledOnce.get()) {
                return;
            }
            settle(1 - index, true);
        }
    }

    public void onDisconnect(long userId) {
        synchronized (this) {
            int index = seatIndex(userId);
            if (index < 0 || this.settledOnce.get()) {
                return;
            }
            Seat seat = this.seats[index];
            seat.disconnected = true;
            sendTo(1 - index, ProtocolCodec.T_OPPONENT_DISCONNECTED, new Object());
            if (seat.forfeitTask != null) {
                seat.forfeitTask.cancel(false);
            }
            final int loser = index;
            seat.forfeitTask = this.scheduler.schedule(new Runnable() {
                @Override
                public void run() {
                    synchronized (MatchRoom.this) {
                        if (!settledOnce.get() && seats[loser].disconnected) {
                            settle(1 - loser, true);
                        }
                    }
                }
            }, this.properties.getTiming().getReconnectWindowMs(), TimeUnit.MILLISECONDS);
        }
    }

    public void onReconnect(long userId, PlayerEndpoint endpoint) {
        synchronized (this) {
            int index = seatIndex(userId);
            if (index < 0 || this.settledOnce.get()) {
                return;
            }
            Seat seat = this.seats[index];
            seat.endpoint = endpoint;
            if (seat.disconnected) {
                seat.disconnected = false;
                if (seat.forfeitTask != null) {
                    seat.forfeitTask.cancel(false);
                    seat.forfeitTask = null;
                }
                sendTo(1 - index, ProtocolCodec.T_OPPONENT_RECONNECTED, new Object());
            }
            // Catch the returning player up with the current round.
            if (this.currentRound > 0) {
                GameCatalog.ProtocolRound round = this.rounds.get(this.currentRound - 1);
                ProtocolCodec.RoundStart start = new ProtocolCodec.RoundStart();
                start.matchId = this.matchId;
                start.round = this.currentRound;
                start.gameId = round.gameId;
                start.durationMs = round.durationMs;
                start.serverNow = System.currentTimeMillis();
                sendTo(index, ProtocolCodec.T_ROUND_START, start);
                ProtocolCodec.OpponentScore score = new ProtocolCodec.OpponentScore();
                score.matchId = this.matchId;
                score.round = this.currentRound;
                score.score = totalScore(1 - index);
                sendTo(index, ProtocolCodec.T_OPPONENT_SCORE, score);
            }
        }
    }

    // ----------------------------------------------------------- settlement

    /** winnerIndex 0/1, or -1 for a score-based result (including draws). */
    private void settle(int winnerIndex, boolean forfeit) {
        if (!this.settledOnce.compareAndSet(false, true)) {
            return;
        }
        if (this.roundDeadlineTask != null) {
            this.roundDeadlineTask.cancel(false);
        }
        stopBotBrain();
        for (Seat seat : this.seats) {
            if (seat.forfeitTask != null) {
                seat.forfeitTask.cancel(false);
            }
        }
        int total0 = totalScore(0);
        int total1 = totalScore(1);
        String result0;
        if (winnerIndex == 0) {
            result0 = ProtocolCodec.RESULT_WIN;
        } else if (winnerIndex == 1) {
            result0 = ProtocolCodec.RESULT_LOSE;
        } else {
            result0 = total0 > total1 ? ProtocolCodec.RESULT_WIN
                    : total0 < total1 ? ProtocolCodec.RESULT_LOSE
                    : ProtocolCodec.RESULT_DRAW;
        }
        // ELO deltas come from pre-match snapshots and are exactly zero-sum:
        // applying the first seat's settlement must not feed into the second.
        int elo0Pre = this.seats[0].elo();
        int elo1Pre = this.seats[1].elo();
        double score0 = ProtocolCodec.RESULT_WIN.equals(result0) ? 1.0
                : ProtocolCodec.RESULT_DRAW.equals(result0) ? 0.5 : 0.0;
        int delta0 = this.rating.delta(elo0Pre, elo1Pre, score0);
        int delta1 = -delta0;
        ProtocolCodec.MatchEnd[] payloads = new ProtocolCodec.MatchEnd[2];
        for (int i = 0; i < 2; i++) {
            String result = i == 0 ? result0 : mirror(result0);
            boolean win = ProtocolCodec.RESULT_WIN.equals(result);
            boolean draw = ProtocolCodec.RESULT_DRAW.equals(result);
            Seat seat = this.seats[i];
            ProtocolCodec.MatchEnd end = new ProtocolCodec.MatchEnd();
            end.matchId = this.matchId;
            end.result = result;
            end.myScore = i == 0 ? total0 : total1;
            end.oppScore = i == 0 ? total1 : total0;
            int coinDelta = win ? this.properties.getEconomy().getWinCoins()
                    : draw ? this.properties.getEconomy().getDrawCoins() : 0;
            end.coinDelta = coinDelta;
            if (seat.user != null) {
                int eloDelta = i == 0 ? delta0 : delta1;
                this.users.applySettlement(seat.user, eloDelta, coinDelta, win, draw);
                end.eloDelta = eloDelta;
                end.elo = seat.user.elo;
                end.balance = seat.user.balance;
            } else {
                end.eloDelta = 0;
                end.elo = this.botElo;
                end.balance = 0;
            }
            payloads[i] = end;
        }
        for (int i = 0; i < 2; i++) {
            sendTo(i, ProtocolCodec.T_MATCH_END, payloads[i]);
        }
        log.info("match {} settled: forfeit={} scores {}/{}",
                this.matchId, forfeit, total0, total1);
        this.settlementCallback.onSettled(this);
    }

    private static String mirror(String result) {
        if (ProtocolCodec.RESULT_WIN.equals(result)) {
            return ProtocolCodec.RESULT_LOSE;
        }
        if (ProtocolCodec.RESULT_LOSE.equals(result)) {
            return ProtocolCodec.RESULT_WIN;
        }
        return ProtocolCodec.RESULT_DRAW;
    }

    private int totalScore(int index) {
        int total = 0;
        for (int score : this.seats[index].roundScores) {
            total += score;
        }
        return total;
    }

    // ------------------------------------------------------------------ bot

    private void startBotBrain(GameCatalog.GameSpec spec, long durationMs) {
        final Random random = new Random(this.matchSeed ^ (this.currentRound * 31L));
        final double accuracy = botAccuracy();
        long interval = Math.max(200L, (long) (this.catalog.botLevelIntervalOf(spec) * (0.8 + random.nextDouble() * 0.4)));
        int ticks = (int) (durationMs / interval) + 1;
        for (int tick = 1; tick <= ticks; tick++) {
            final int level = tick;
            this.botTasks.add(this.scheduler.schedule(new Runnable() {
                @Override
                public void run() {
                    botTick(level, accuracy, random);
                }
            }, tick * interval, TimeUnit.MILLISECONDS));
        }
    }

    private void botTick(int level, double accuracy, Random random) {
        synchronized (this) {
            if (this.settledOnce.get() || this.seats[1].expectedLevel != level) {
                return;
            }
            this.seats[1].expectedLevel = level + 1;
            boolean won = random.nextDouble() < accuracy;
            if (won) {
                GameCatalog.GameSpec spec = this.catalog.byId(this.rounds.get(this.currentRound - 1).gameId);
                this.seats[1].roundScores[this.currentRound - 1] += spec.levelScore(level);
                ProtocolCodec.OpponentScore score = new ProtocolCodec.OpponentScore();
                score.matchId = this.matchId;
                score.round = this.currentRound;
                score.score = totalScore(1);
                sendTo(0, ProtocolCodec.T_OPPONENT_SCORE, score);
            }
        }
    }

    private double botAccuracy() {
        int elo = this.seats[0].user != null ? this.seats[0].user.elo : this.botElo;
        return Math.max(0.55, Math.min(0.92, 0.55 + (elo - 1000) * 0.0004));
    }

    private void stopBotBrain() {
        for (ScheduledFuture<?> task : this.botTasks) {
            task.cancel(false);
        }
        this.botTasks.clear();
    }

    // -------------------------------------------------------------- helpers

    public boolean hasUser(long userId) {
        return seatIndex(userId) >= 0;
    }

    public int seatIndex(long userId) {
        for (int i = 0; i < 2; i++) {
            if (this.seats[i].user != null && this.seats[i].user.id == userId) {
                return i;
            }
        }
        return -1;
    }

    public long[] userIds() {
        long[] ids = new long[2];
        for (int i = 0; i < 2; i++) {
            ids[i] = this.seats[i].user != null ? this.seats[i].user.id : -1L;
        }
        return ids;
    }

    private void sendTo(int index, String type, Object payload) {
        PlayerEndpoint endpoint = this.seats[index].endpoint;
        if (endpoint != null) {
            try {
                endpoint.send(ProtocolCodec.encode(type, payload));
            } catch (RuntimeException e) {
                log.warn("send to seat {} failed: {}", index, e.getMessage());
            }
        }
    }
}
