package com.memorygames.pk.match;

import com.memorygames.pk.admin.AdminMetrics;
import com.memorygames.pk.admin.EventRing;
import com.memorygames.pk.config.PkProperties;
import com.memorygames.pk.game.GameCatalog;
import com.memorygames.pk.protocol.ProtocolCodec;
import com.memorygames.pk.rating.Rating;
import com.memorygames.pk.season.SeasonService;
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
        /** Pre-start accept gate (origin's manual START button). */
        public boolean accepted;
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

    public static final String MODE_RANKED = ProtocolCodec.MODE_RANKED;
    public static final String MODE_FRIEND = ProtocolCodec.MODE_FRIEND;
    public static final String MODE_PRACTICE = ProtocolCodec.MODE_PRACTICE;

    public final String matchId;
    /** RANKED, FRIEND or PRACTICE (docs/bot-battle-prd.md). */
    public final String mode;
    private final Seat[] seats = new Seat[2];
    private final List<GameCatalog.ProtocolRound> rounds;
    private final long matchSeed;
    private final String botNickname;
    private final int botElo;
    /** Practice bots hold a fixed tier accuracy; ranked fallback bots derive it from the ELO. */
    private final Double botFixedAccuracy;
    private final double botPaceMultiplier;

    private final GameCatalog catalog;
    private final UserService users;
    private final Rating rating;
    private final PkProperties properties;
    /** Economy/rules snapshot captured at room creation (P2-1). */
    private final MatchTerms terms;
    private final ScheduledThreadPoolExecutor scheduler;
    /** Bounded pool for settlement transactions; timers never touch the DB (P2-4). */
    private final java.util.concurrent.Executor dbExecutor;
    private final SettlementCallback settlementCallback;
    private final SettlementService settlement;
    private final SeasonService seasons;
    private final EventRing events;
    private final AdminMetrics metrics;
    /** Set by adminTerminate()/shutdown: zero ELO/coins, refund entry, no W/L count. */
    private boolean adminVoided;
    /** Bot rubber-banding accuracy (PRD M2 bot tuning). */
    private double botAccuracy;

    private int currentRound = 0;
    private long roundStartedAt;
    private long roundDeadlineAt;
    /** End of the scoring window (round start + duration, WITHOUT grace). */
    private long gameplayDeadlineAt;
    /** Explicit phase guard: duplicate messages and already-dequeued timers are harmless. */
    private boolean roundEnded = true;
    private long nextRoundStartsAt;
    private boolean prepareRounds;
    private int preparingRound;
    private ScheduledFuture<?> preparationDeadlineTask;
    private ScheduledFuture<?> nextRoundStartTask;
    private ScheduledFuture<?> gameplayDeadlineTask;
    private long startedAt = System.currentTimeMillis();
    private final AtomicBoolean settledOnce = new AtomicBoolean(false);
    private ScheduledFuture<?> roundDeadlineTask;
    private ScheduledFuture<?> acceptDeadlineTask;
    private final List<ScheduledFuture<?>> botTasks = new ArrayList<>();
    /**
     * Bot v2 brain (docs/bot-battle-prd.md section 5): exactly one answer is
     * in flight at a time; bumping the epoch invalidates a stale one.
     */
    private long botEpoch;
    private int botMistakesLeft;
    private Random botRandom;

    public MatchRoom(UserAccount userA, PlayerEndpoint endpointA,
                     UserAccount userB, PlayerEndpoint endpointB,
                     String botNickname, String mode, int botElo,
                     Double botFixedAccuracy, double botPaceMultiplier,
                     GameCatalog catalog, UserService users,
                     Rating rating, PkProperties properties, MatchTerms terms,
                     ScheduledThreadPoolExecutor scheduler,
                     java.util.concurrent.Executor dbExecutor,
                     SettlementCallback settlementCallback,
                     SettlementService settlement, SeasonService seasons,
                     EventRing events, AdminMetrics metrics) {
        this.matchId = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        this.mode = mode;
        this.terms = terms;
        this.dbExecutor = dbExecutor;
        this.settlement = settlement;
        this.seasons = seasons;
        this.events = events;
        this.metrics = metrics;
        this.botAccuracy = -1.0d; // lazily initialized at the first round
        this.matchSeed = new Random().nextLong();
        this.rounds = catalog.planRounds(this.matchSeed, properties.getMatch().getRoundsPerMatch());
        this.botNickname = botNickname;
        this.botElo = botElo;
        this.botFixedAccuracy = botFixedAccuracy;
        this.botPaceMultiplier = botPaceMultiplier;
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
            // Escrow (P3): the entry fees and the persistent ACTIVE instance
            // row commit atomically, so a crash mid-start can strand neither
            // coins nor a ghost match. Practice duels open with fee 0.
            this.settlement.openMatch(this.matchId, this.mode,
                    this.seats[0].user != null ? this.seats[0].user.id : null,
                    this.seats[1].user != null ? this.seats[1].user.id : null,
                    isPracticeMode() ? 0 : this.terms.entryFee());
            StringBuilder plan = new StringBuilder();
            for (int r = 0; r < this.rounds.size(); r++) {
                GameCatalog.ProtocolRound round = this.rounds.get(r);
                if (r > 0) {
                    plan.append(',');
                }
                plan.append(r + 1).append(':').append(round.gameId)
                        .append(':').append(round.durationMs).append(':').append(round.seed);
            }
            this.events.emit("MATCH_CREATED", this.matchId,
                    "a=" + userIds()[0] + " b=" + userIds()[1] + " bot=" + this.seats[1].isBot
                            + " mode=" + this.mode + " rounds=" + this.rounds.size() + " plan=" + plan);
            this.metrics.matchesStarted.incrementAndGet();
            for (int i = 0; i < 2; i++) {
                sendTo(i, ProtocolCodec.T_MATCH_FOUND, buildMatchFound(i));
            }
            // Origin's flow gates the first round behind both players'
            // START presses; a bot opponent presses instantly.
            this.seats[1].accepted = this.seats[1].isBot;
            if (this.seats[0].accepted && this.seats[1].accepted) {
                scheduleRoundStart(1, this.properties.getTiming().getMatchFoundDelayMs());
            } else {
                this.acceptDeadlineTask = this.scheduler.schedule(new Runnable() {
                    @Override
                    public void run() {
                        cancelUnaccepted("accept_timeout");
                    }
                }, this.properties.getTiming().getAcceptTimeoutMs(), TimeUnit.MILLISECONDS);
            }
        }
    }

    /**
     * A player pressed START. The first round leaves once both seats have
     * accepted; idempotent so a reconnecting client can press again safely.
     */
    public void onMatchStart(long userId) {
        onMatchStart(userId, false);
    }

    public void onMatchStart(long userId, boolean prepareRounds) {
        synchronized (this) {
            int index = seatIndex(userId);
            if (index < 0 || this.settledOnce.get() || this.currentRound > 0 || this.preparingRound > 0) {
                this.events.emit("MATCH_START", this.matchId,
                        "user=" + userId + " round=" + this.currentRound + " accepted=false reason="
                                + (index < 0 ? "unknown_user" : this.settledOnce.get() ? "settled" : "round_mismatch"));
                return;
            }
            this.events.emit("MATCH_START", this.matchId,
                    "user=" + userId + " accepted=true reason=" + (this.seats[index].accepted ? "duplicate" : "ok"));
            if (!this.seats[index].accepted && isPracticeMode() && this.seats[1].isBot) {
                this.prepareRounds = prepareRounds;
            }
            this.seats[index].accepted = true;
            if (this.seats[0].accepted && this.seats[1].accepted) {
                if (this.acceptDeadlineTask != null) {
                    this.acceptDeadlineTask.cancel(false);
                    this.acceptDeadlineTask = null;
                }
                // Origin told both players "your opponent is ready" the
                // moment the second START landed, before the games began.
                for (int i = 0; i < 2; i++) {
                    ProtocolCodec.OpponentStart ready = new ProtocolCodec.OpponentStart();
                    ready.matchId = this.matchId;
                    sendTo(i, ProtocolCodec.T_OPPONENT_START, ready);
                }
                scheduleRoundStart(1, this.properties.getTiming().getMatchFoundDelayMs());
            }
        }
    }

    /**
     * Nobody pressed START in time: refund both entries and dissolve the
     * match without touching W/L or rating (origin refunded and requeued).
     * The refund runs as the idempotent instance abort on the DB pool
     * (instance ACTIVE->ABORTED + REFUND ledger rows in one transaction),
     * so a replay or restart race can never double-refund.
     */
    private void cancelUnaccepted(String reason) {
        synchronized (this) {
            if (this.settledOnce.get() || this.currentRound > 0
                    || ("accept_timeout".equals(reason) && this.seats[0].accepted && this.seats[1].accepted)) {
                return;
            }
            this.settledOnce.set(true);
            cancelPreparation();
            this.events.emit("MATCH_NOT_ACCEPTED", this.matchId,
                    "reason=" + reason + " acceptedA=" + this.seats[0].accepted
                            + " acceptedB=" + this.seats[1].accepted);
        }
        Runnable abort = new Runnable() {
            @Override
            public void run() {
                try {
                    MatchRoom.this.settlement.abortMatch(MatchRoom.this.matchId);
                } catch (RuntimeException e) {
                    MatchRoom.this.events.emit("ABORT_FAILED", MatchRoom.this.matchId,
                            "reason=" + reason + " class=" + e.getClass().getName());
                }
                // Release the seats BEFORE the cancellation frame reaches
                // the client (immediate re-queue must CAS cleanly).
                MatchRoom.this.settlementCallback.onSettled(MatchRoom.this);
                for (int i = 0; i < 2; i++) {
                    sendTo(i, ProtocolCodec.T_MATCH_CANCELLED, new Object());
                }
            }
        };
        try {
            this.dbExecutor.execute(abort);
        } catch (RuntimeException saturated) {
            abort.run(); // degrade to the caller thread rather than leak the room
        }
    }

    private ProtocolCodec.MatchFound buildMatchFound(int viewerIndex) {
        ProtocolCodec.MatchFound found = new ProtocolCodec.MatchFound();
        found.matchId = this.matchId;
        found.mode = this.mode;
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
        info.modeStats = this.users.modeStats(info.userId);
        // Practice bots carry their difficulty tier id for the client badge.
        info.tier = seat.isBot && isPracticeMode() ? this.practiceTierId() : null;
        if (seat.user != null) {
            info.wins = seat.user.wins;
            info.losses = seat.user.losses;
            info.draws = seat.user.draws;
        } else {
            int base = Math.max(4, this.botElo / 40);
            info.wins = base;
            info.losses = Math.max(2, base * 2 / 3);
            info.draws = base / 5;
        }
        return info;
    }

    // ------------------------------------------------------ round engine

    private void scheduleRoundStart(final int roundNumber, long delayMs) {
        // Use one absolute target for the wire and the actual timer; sending
        // round_end must not add its transmission time to the intermission.
        if (this.nextRoundStartTask != null) {
            return;
        }
        this.nextRoundStartsAt = now() + Math.max(0L, delayMs);
        this.nextRoundStartTask = this.scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                prepareOrStartRound(roundNumber);
            }
        }, Math.max(0L, this.nextRoundStartsAt - now()), TimeUnit.MILLISECONDS);
    }

    private void prepareOrStartRound(int roundNumber) {
        synchronized (this) {
            if (this.settledOnce.get() || roundNumber != this.currentRound + 1
                    || this.preparingRound != 0) {
                return;
            }
            if (!this.prepareRounds) {
                roundStart(roundNumber);
                return;
            }
            this.nextRoundStartTask = null;
            this.nextRoundStartsAt = 0L;
            this.preparingRound = roundNumber;
            sendRoundPreparation(0);
            this.events.emit("ROUND_PREPARING", this.matchId, "round=" + roundNumber);
            this.preparationDeadlineTask = this.scheduler.schedule(() -> {
                synchronized (MatchRoom.this) {
                    if (!this.settledOnce.get() && this.preparingRound == roundNumber) {
                        if (this.currentRound == 0) cancelUnaccepted("preparation_timeout");
                        else settle(1, true, "preparation_timeout");
                    }
                }
            }, Math.max(1L, this.properties.getTiming().getPracticePreparationTimeoutMs()), TimeUnit.MILLISECONDS);
        }
    }

    private void sendRoundPreparation(int index) {
        GameCatalog.ProtocolRound round = this.rounds.get(this.preparingRound - 1);
        ProtocolCodec.RoundStart start = new ProtocolCodec.RoundStart();
        start.matchId = this.matchId;
        start.round = this.preparingRound;
        start.gameId = round.gameId;
        start.durationMs = round.durationMs;
        start.serverNow = now();
        start.preparing = true;
        sendTo(index, ProtocolCodec.T_ROUND_START, start);
    }

    public void onRoundReady(long userId, ProtocolCodec.RoundReady ready) {
        synchronized (this) {
            if (this.settledOnce.get() || !this.prepareRounds || seatIndex(userId) != 0
                    || !this.matchId.equals(ready.matchId) || this.preparingRound == 0
                    || ready.round != this.preparingRound) {
                return;
            }
            int roundNumber = this.preparingRound;
            this.preparingRound = 0;
            if (this.preparationDeadlineTask != null) {
                this.preparationDeadlineTask.cancel(false);
                this.preparationDeadlineTask = null;
            }
            roundStart(roundNumber);
        }
    }

    private void cancelPreparation() {
        this.preparingRound = 0;
        if (this.preparationDeadlineTask != null) {
            this.preparationDeadlineTask.cancel(false);
            this.preparationDeadlineTask = null;
        }
    }

    private void roundStart(int roundNumber) {
        synchronized (this) {
            if (this.settledOnce.get() || roundNumber != this.currentRound + 1) {
                this.events.emit("ROUND_START", this.matchId,
                        "round=" + roundNumber + " current=" + this.currentRound
                                + " accepted=false reason="
                                + (this.settledOnce.get() ? "settled" : "round_mismatch"));
                return;
            }
            this.currentRound = roundNumber;
            this.roundEnded = false;
            this.nextRoundStartTask = null;
            this.nextRoundStartsAt = 0L;
            GameCatalog.ProtocolRound round = this.rounds.get(roundNumber - 1);
            GameCatalog.GameSpec spec = this.catalog.byId(round.gameId);
            long now = now();
            this.roundStartedAt = now;
            // All clocks start with round_start, not after sending its frames.
            this.gameplayDeadlineAt = now + round.durationMs;
            this.roundDeadlineAt = this.gameplayDeadlineAt
                    + this.properties.getTiming().getRoundGraceMs();
            this.botAccuracy = -1.0d;
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
                this.gameplayDeadlineTask = this.scheduler.schedule(new Runnable() {
                    @Override
                    public void run() {
                        completeBotAtDeadline(roundNumber);
                    }
                }, Math.max(0L, this.gameplayDeadlineAt - now()), TimeUnit.MILLISECONDS);
            }
            this.events.emit("ROUND_START", this.matchId,
                    "round=" + roundNumber + " game=" + round.gameId + " dur=" + round.durationMs
                            + " accepted=true reason=ok acceptedA=" + this.seats[0].accepted
                            + " acceptedB=" + this.seats[1].accepted);
            this.roundDeadlineTask = this.scheduler.schedule(new Runnable() {
                @Override
                public void run() {
                    endRound(roundNumber, true);
                }
            }, Math.max(0L, this.roundDeadlineAt - now()), TimeUnit.MILLISECONDS);
        }
    }

    public void onLevelWin(long userId, ProtocolCodec.LevelWin msg) {
        synchronized (this) {
            int index = seatIndex(userId);
            if (index < 0 || this.settledOnce.get() || this.roundEnded || msg.round != this.currentRound) {
                return;
            }
            Seat seat = this.seats[index];
            if (seat.roundDone || seat.frozen) {
                return;
            }
            GameCatalog.GameSpec spec = this.catalog.byId(this.rounds.get(this.currentRound - 1).gameId);
            long now = now();
            // Late level_wins (past duration + a small network tolerance)
            // must not score: the grace window is transport slack, not
            // playing time — otherwise a reconnecting client banks extra
            // answering time the opponent never had (P1-5).
            if (now > this.gameplayDeadlineAt + this.properties.getTiming().getScoreAcceptGraceMs()) {
                this.metrics.anomalies.incrementAndGet();
                this.events.emit("LEVEL_WIN_REJECTED", this.matchId,
                        "user=" + userId + " round=" + msg.round + " level=" + msg.level + " reason=late");
                return;
            }
            // Wrong answers advance the level without scoring, so reported
            // levels legitimately contain gaps (1,3,4,6,...). Require strictly
            // increasing levels capped by what the elapsed time plausibly
            // allows, instead of strict succession.
            long floorMs = this.catalog.minLevelIntervalOf(spec);
            long elapsed = now - this.roundStartedAt;
            boolean sequenceOk = msg.level > seat.lastReportedLevel
                    && msg.level <= spec.maxLevel()
                    && msg.level <= (elapsed / floorMs) + 3;
            // The first answer of a round is not interval-checked: the server
            // cannot know when the client's challenge reveal ended, and fast
            // first answers are legitimate. Spam is still capped from the
            // second message on by the per-game human floor.
            boolean intervalOk = seat.lastReportedLevel == 0
                    || now - seat.lastLevelAt >= floorMs;
            if (!sequenceOk || !intervalOk) {
                seat.anomalies++;
                this.metrics.anomalies.incrementAndGet();
                this.events.emit("LEVEL_WIN_REJECTED", this.matchId,
                        "user=" + userId + " round=" + msg.round + " level=" + msg.level
                                + (sequenceOk ? " reason=interval" : " reason=sequence"));
                if (seat.anomalies > MAX_ANOMALIES) {
                    seat.frozen = true;
                    log.warn("match {} user {}: score stream frozen after {} anomalies",
                            this.matchId, userId, seat.anomalies);
                }
                return;
            }
            seat.lastReportedLevel = msg.level;
            seat.lastLevelAt = now;
            this.metrics.levelWins.incrementAndGet();
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
            if (index < 0 || this.settledOnce.get() || this.roundEnded || msg.round != this.currentRound) {
                this.events.emit("ROUND_COMPLETE", this.matchId,
                        "user=" + userId + " round=" + msg.round + " current=" + this.currentRound
                                + " accepted=false reason=" + (index < 0 ? "unknown_user"
                                : this.settledOnce.get() ? "settled" : "round_mismatch"));
                return;
            }
            // Completing early never shortens a bot's authoritative scoring window.
            this.events.emit("ROUND_COMPLETE", this.matchId,
                    "user=" + userId + " round=" + msg.round + " current=" + this.currentRound
                            + " accepted=true reason=" + (this.seats[index].roundDone ? "duplicate" : "ok"));
            this.seats[index].roundDone = true;
            if (this.seats[1].isBot && now() >= this.gameplayDeadlineAt) {
                completeBotAtDeadline(this.currentRound);
            }
            if (this.seats[0].roundDone && this.seats[1].roundDone) {
                endRound(this.currentRound, false);
            }
        }
    }

    private void completeBotAtDeadline(int roundNumber) {
        synchronized (this) {
            if (this.settledOnce.get() || this.roundEnded || roundNumber != this.currentRound
                    || !this.seats[1].isBot || now() < this.gameplayDeadlineAt) {
                return;
            }
            stopBotBrain();
            this.seats[1].roundDone = true;
            if (this.seats[0].roundDone) {
                endRound(roundNumber, false);
            }
        }
    }

    private void endRound(int roundNumber, boolean forced) {
        synchronized (this) {
            if (this.settledOnce.get() || this.roundEnded || roundNumber != this.currentRound
                    || this.currentRound == 0) {
                return;
            }
            this.roundEnded = true;
            if (this.gameplayDeadlineTask != null) {
                this.gameplayDeadlineTask.cancel(false);
                this.gameplayDeadlineTask = null;
            }
            if (this.roundDeadlineTask != null) {
                this.roundDeadlineTask.cancel(false);
                this.roundDeadlineTask = null;
            }
            stopBotBrain();
            this.events.emit("ROUND_END", this.matchId,
                    "round=" + this.currentRound + " forced=" + forced
                            + " reason=" + (forced ? "deadline" : "both_complete")
                            + " doneA=" + this.seats[0].roundDone + " doneB=" + this.seats[1].roundDone
                            + " scores=" + totalScore(0) + "/" + totalScore(1));
            if (this.currentRound < this.rounds.size()) {
                long gap = isPracticeMode() && this.seats[1].isBot
                        ? this.properties.getTiming().getPracticeRoundIntermissionMs()
                        : this.properties.getTiming().getRoundIntermissionMs();
                scheduleRoundStart(this.currentRound + 1, gap);
            } else {
                this.nextRoundStartsAt = 0L;
            }
            for (int i = 0; i < 2; i++) {
                sendTo(i, ProtocolCodec.T_ROUND_END, buildRoundEnd(i));
            }
            if (this.currentRound == this.rounds.size()) {
                // Natural completion: decide by total score (-1), never by seat.
                settle(-1, false, "rounds_complete");
            }
        }
    }

    private ProtocolCodec.RoundEnd buildRoundEnd(int viewerIndex) {
        ProtocolCodec.RoundEnd end = new ProtocolCodec.RoundEnd();
        end.matchId = this.matchId;
        end.round = this.currentRound;
        end.myScore = totalScore(viewerIndex);
        end.oppScore = totalScore(1 - viewerIndex);
        end.nextRoundStartsAt = this.nextRoundStartsAt;
        end.serverNow = now();
        end.nextGameId = this.currentRound < this.rounds.size()
                ? this.rounds.get(this.currentRound).gameId : null;
        return end;
    }

    // ------------------------------------------------------- admin surface

    /** Read-only view for the admin dashboard / match list. */
    public static class Snapshot {

        public String matchId;
        public String mode;
        public int round;
        public int totalRounds;
        public long roundEndsInMs;
        public boolean roundEnded;
        public long nextRoundStartsAt;
        public long serverNow;
        public String nextGameId;
        public SeatView[] seats = new SeatView[2];

        public static class SeatView {

            public long userId;
            public String nickname;
            public boolean bot;
            public boolean disconnected;
            public int roundScore;
            public int totalScore;
        }
    }

    public synchronized Snapshot adminSnapshot() {
        Snapshot snapshot = new Snapshot();
        snapshot.matchId = this.matchId;
        snapshot.mode = this.mode;
        snapshot.round = this.currentRound;
        snapshot.totalRounds = this.rounds.size();
        snapshot.serverNow = now();
        snapshot.roundEnded = this.roundEnded;
        snapshot.nextRoundStartsAt = this.nextRoundStartsAt;
        snapshot.nextGameId = this.roundEnded && this.currentRound > 0
                && this.currentRound < this.rounds.size() ? this.rounds.get(this.currentRound).gameId : null;
        snapshot.roundEndsInMs = this.currentRound > 0 && !this.roundEnded
                ? Math.max(0L, this.roundDeadlineAt - snapshot.serverNow) : 0L;
        for (int i = 0; i < 2; i++) {
            Seat seat = this.seats[i];
            Snapshot.SeatView view = new Snapshot.SeatView();
            view.userId = seat.user != null ? seat.user.id : -1L;
            view.nickname = seat.nickname();
            view.bot = seat.isBot;
            view.disconnected = seat.disconnected;
            view.roundScore = this.currentRound > 0 ? seat.roundScores[this.currentRound - 1] : 0;
            view.totalScore = totalScore(i);
            snapshot.seats[i] = view;
        }
        return snapshot;
    }

    /** Admin force-settle by score; optionally refunds the entry fee. */
    public synchronized boolean adminSettle(boolean refund) {
        if (this.settledOnce.get()) {
            return false;
        }
        if (refund && !isPracticeMode()) {
            // A practice match never charged the fee; refunding it would mint.
            for (Seat seat : this.seats) {
                if (seat.user != null) {
                    this.users.adjustBalance(seat.user, this.terms.entryFee(), "admin-refund");
                }
            }
        }
        this.events.emit("ADMIN_SETTLE", this.matchId, "refund=" + refund);
        settle(-1, false, "admin_settle");
        return true;
    }

    /** Admin void: no ELO/coins/W-L, entry fee refunded to both seats. */
    public synchronized boolean adminTerminate() {
        if (this.settledOnce.get()) {
            return false;
        }
        this.adminVoided = true;
        this.events.emit("ADMIN_TERMINATE", this.matchId, "");
        settle(-1, true, "admin_terminate");
        return true;
    }

    // ------------------------------------------------------------ controls

    public void onResign(long userId) {
        synchronized (this) {
            int index = seatIndex(userId);
            if (index < 0 || this.settledOnce.get()) {
                return;
            }
            this.events.emit("RESIGN", this.matchId, "user=" + userId + " round=" + this.currentRound);
            if (this.currentRound == 0) {
                // Leaving during the START gate costs nothing.
                cancelUnaccepted("resign");
                return;
            }
            settle(1 - index, true, "resign");
        }
    }

    public void onDisconnect(long userId) {
        synchronized (this) {
            int index = seatIndex(userId);
            if (index < 0 || this.settledOnce.get()) {
                return;
            }
            Seat seat = this.seats[index];
            this.events.emit("DISCONNECT", this.matchId,
                    "user=" + userId + " round=" + this.currentRound
                            + " windowMs=" + this.properties.getTiming().getReconnectWindowMs());
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
                            settle(1 - loser, true, "disconnect_timeout");
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
            this.events.emit("RECONNECT", this.matchId,
                    "user=" + userId + " round=" + this.currentRound + " disconnected=" + seat.disconnected);
            seat.endpoint = endpoint;
            if (seat.disconnected) {
                seat.disconnected = false;
                if (seat.forfeitTask != null) {
                    seat.forfeitTask.cancel(false);
                    seat.forfeitTask = null;
                }
                sendTo(1 - index, ProtocolCodec.T_OPPONENT_RECONNECTED, new Object());
            }
            // Catch the returning player up with the current round. The
            // timer must reflect what is actually LEFT of the round: the
            // client restarts its countdown from serverNow with
            // durationMs, so resending the full window would hand the
            // reconnector free playing time (P1-5). During intermission or
            // the end-of-round grace there is nothing to resume — the
            // round_end (or the next round_start) is already in flight.
            long now = now();
            if (this.preparingRound > 0) {
                sendRoundPreparation(index);
            } else if (this.currentRound > 0 && this.roundEnded) {
                sendTo(index, ProtocolCodec.T_ROUND_END, buildRoundEnd(index));
            } else if (this.currentRound > 0 && now < this.gameplayDeadlineAt) {
                GameCatalog.ProtocolRound round = this.rounds.get(this.currentRound - 1);
                ProtocolCodec.RoundStart start = new ProtocolCodec.RoundStart();
                start.matchId = this.matchId;
                start.round = this.currentRound;
                start.gameId = round.gameId;
                start.durationMs = (int) Math.min(Integer.MAX_VALUE,
                        Math.max(1L, this.gameplayDeadlineAt - now));
                start.serverNow = now;
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

    /** Settlement plan plus the immutable payload inputs captured at settle time. */
    private static final class PendingSettlement {

        final SettlementService.Command command;
        final int[][] roundScores = new int[2][];
        final boolean forfeit;

        PendingSettlement(SettlementService.Command command, int rounds, boolean forfeit) {
            this.command = command;
            this.forfeit = forfeit;
            for (int i = 0; i < 2; i++) {
                this.roundScores[i] = new int[rounds];
            }
        }
    }

    private static final int MAX_SETTLE_ATTEMPTS = 5;
    private static final long SETTLE_RETRY_DELAY_MS = 2000L;

    /** winnerIndex 0/1, or -1 for a score-based result (including draws). */
    private String settlementReason;

    private void settle(int winnerIndex, boolean forfeit, String reason) {
        if (!this.settledOnce.compareAndSet(false, true)) {
            return;
        }
        this.settlementReason = reason;
        cancelPreparation();
        this.events.emit("SETTLE_TRIGGER", this.matchId,
                "round=" + this.currentRound + " reason=" + reason
                        + " winner=" + winnerIndex + " forfeit=" + forfeit
                        + " a=" + userIds()[0] + " b=" + userIds()[1]);
        if (this.roundDeadlineTask != null) {
            this.roundDeadlineTask.cancel(false);
        }
        if (this.acceptDeadlineTask != null) {
            this.acceptDeadlineTask.cancel(false);
        }
        if (this.gameplayDeadlineTask != null) {
            this.gameplayDeadlineTask.cancel(false);
        }
        if (this.nextRoundStartTask != null) {
            this.nextRoundStartTask.cancel(false);
            this.nextRoundStartTask = null;
        }
        this.nextRoundStartsAt = 0L;
        stopBotBrain();
        for (Seat seat : this.seats) {
            if (seat.forfeitTask != null) {
                seat.forfeitTask.cancel(false);
            }
        }
        PendingSettlement pending = buildSettlement(winnerIndex, forfeit);
        dispatchSettlement(pending, 0);
    }

    /**
     * Computes the whole settlement as pure data — results, zero-sum ELO
     * deltas from pre-match snapshots, payouts, anti-cheat flag outcomes —
     * without touching the database, so the single-transaction apply in
     * {@link SettlementService} is exactly this plan.
     */
    private PendingSettlement buildSettlement(int winnerIndex, boolean forfeit) {
        int total0 = totalScore(0);
        int total1 = totalScore(1);
        String result0;
        if (this.adminVoided) {
            result0 = ProtocolCodec.RESULT_DRAW;
        } else if (winnerIndex == 0) {
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
        int delta0 = this.rating.delta(elo0Pre, elo1Pre, score0, this.terms.kFactor());
        // Friend rooms move half rating (PRD 3.11), rounded to stay zero-sum.
        // Practice duels never move rating at all (docs/bot-battle-prd.md).
        if (isPracticeMode() || this.adminVoided) {
            delta0 = 0;
        } else if (isFriendMode()) {
            delta0 = delta0 / 2;
        }
        SettlementService.Command command = new SettlementService.Command();
        command.matchId = this.matchId;
        command.mode = this.mode;
        command.practice = isPracticeMode();
        command.voided = this.adminVoided;
        command.entryCharged = !isPracticeMode();
        command.entryFee = this.terms.entryFee();
        command.createdAt = this.startedAt;
        command.botElo = this.botElo;
        command.practiceTierWinCoins = this.terms.practiceTierWinCoins();
        command.practiceDailyCap = this.terms.practiceDailyCap();
        PendingSettlement pending = new PendingSettlement(command, this.rounds.size(), forfeit);
        int delta1 = -delta0;
        for (int i = 0; i < 2; i++) {
            String result = i == 0 ? result0 : mirror(result0);
            boolean win = ProtocolCodec.RESULT_WIN.equals(result);
            boolean draw = ProtocolCodec.RESULT_DRAW.equals(result);
            Seat seat = this.seats[i];
            SettlementService.SeatPlan plan = new SettlementService.SeatPlan();
            plan.userId = seat.user != null ? seat.user.id : null;
            plan.nickname = seat.nickname();
            plan.score = i == 0 ? total0 : total1;
            plan.result = result;
            plan.eloDelta = this.adminVoided ? 0 : (i == 0 ? delta0 : delta1);
            plan.win = win;
            plan.draw = draw;
            // Nominal payout from the terms snapshot: for practice the daily
            // cap is resolved inside the settlement transaction (it queries
            // committed wins).
            plan.coinDelta = isPracticeMode()
                    ? (seat.user != null && (win || draw) ? command.practiceTierWinCoins : 0)
                    : win ? this.terms.winCoins()
                    : draw ? this.terms.drawCoins() : 0;
            command.seats[i] = plan;
            for (int r = 0; r < this.rounds.size(); r++) {
                pending.roundScores[i][r] = seat.roundScores[r];
            }
        }
        planAntiCheat(command);
        return pending;
    }

    /**
     * Anti-cheat v2 (PRD 4.5): per-seat post-match distribution check via
     * {@link AntiCheatPolicy}; outcomes are applied by
     * {@link SettlementService} inside the settlement transaction.
     */
    private void planAntiCheat(SettlementService.Command command) {
        long elapsed = now() - this.roundStartedAt;
        long floor = 0;
        for (GameCatalog.ProtocolRound round : this.rounds) {
            GameCatalog.GameSpec spec = this.catalog.byId(round.gameId);
            floor = Math.max(floor, this.catalog.minLevelIntervalOf(spec));
        }
        for (Seat seat : this.seats) {
            if (seat.user == null || seat.isBot) {
                continue;
            }
            SettlementService.FlagUpdate flag = AntiCheatPolicy.evaluate(
                    seat.lastReportedLevel, elapsed, floor,
                    seat.user.suspicion, seat.user.restrictedMatches,
                    this.properties.getAntiCheat());
            if (flag == null) {
                continue;
            }
            command.flagUpdates.put(seat.user.id, flag);
            if (flag.strike) {
                this.metrics.anticheastStrikes.incrementAndGet();
                this.events.emit("ANTICHEAT_STRIKE", this.matchId,
                        "user=" + seat.user.id + " avg=" + (int) (elapsed / Math.max(1, seat.lastReportedLevel))
                                + " floor=" + floor + " suspicion=" + flag.suspicionAfter);
                if (flag.restrictionAfter != null) {
                    log.warn("user {} restricted to bot matches for {} games (answer-rate anomaly)",
                            seat.user.id, flag.restrictionAfter);
                }
            }
        }
    }

    /**
     * Runs the settlement transaction on the bounded DB pool and, on
     * success, delivers match_end. Timer threads only submit here — the
     * transaction itself never runs on the scheduler (P2-4). Failures retry
     * on a delay — the room stays alive until the plan lands or the retries
     * run out, instead of a permanently-settled-but-half-persisted state.
     */
    private void dispatchSettlement(final PendingSettlement pending, final int attempt) {
        try {
            this.dbExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    runSettlement(pending, attempt);
                }
            });
        } catch (RuntimeException saturated) {
            retrySettlementLater(pending, attempt, "db_pool", saturated);
        }
    }

    private void runSettlement(PendingSettlement pending, int attempt) {
        SettlementService.Result result;
        try {
            result = this.settlement.settle(pending.command);
        } catch (RuntimeException e) {
            retrySettlementLater(pending, attempt, "transaction", e);
            return;
        }
        deliverSettlement(pending, result);
    }

    private void retrySettlementLater(PendingSettlement pending, int attempt, String reason, Throwable failure) {
        String detail = "attempt=" + (attempt + 1) + " reason=" + reason
                + " class=" + failure.getClass().getName();
        if (attempt >= MAX_SETTLE_ATTEMPTS || this.scheduler.isShutdown()) {
            this.metrics.settlementFailures.incrementAndGet();
            this.events.emit("SETTLE_FAILED", this.matchId, detail + " attempts=" + (attempt + 1));
            return;
        }
        this.events.emit("SETTLE_RETRY", this.matchId, detail);
        try {
            this.scheduler.schedule(new Runnable() {
                @Override
                public void run() {
                    dispatchSettlement(pending, attempt + 1);
                }
            }, SETTLE_RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
        } catch (RuntimeException rejected) {
            this.metrics.settlementFailures.incrementAndGet();
            this.events.emit("SETTLE_FAILED", this.matchId,
                    "reason=scheduler_rejected class=" + rejected.getClass().getName());
        }
    }

    /** Builds and sends the match_end frames AFTER the transaction committed. */
    private void deliverSettlement(PendingSettlement pending, SettlementService.Result result) {
        SettlementService.Command command = pending.command;
        this.metrics.matchesSettled.incrementAndGet();
        this.events.emit("SETTLED", this.matchId,
                "mode=" + this.mode
                        + " forfeit=" + pending.forfeit + " voided=" + command.voided
                        + " scores=" + command.seats[0].score + "/" + command.seats[1].score
                        + " round=" + this.currentRound + " reason=" + this.settlementReason
                        + " replayed=" + result.alreadySettled);
        // Release the seats BEFORE the clients learn the match ended: the
        // moment a client reacts to match_end by queueing again, its
        // lifecycle claim must already be free or the CAS will 409 it.
        this.settlementCallback.onSettled(this);
        for (int i = 0; i < 2; i++) {
            SettlementService.SeatPlan plan = command.seats[i];
            SettlementService.SeatOutcome outcome = result.seats[i];
            ProtocolCodec.MatchEnd end = new ProtocolCodec.MatchEnd();
            end.matchId = this.matchId;
            end.mode = command.mode;
            end.modeStats = outcome.modeStats;
            end.result = plan.result;
            end.myScore = plan.score;
            end.oppScore = command.seats[1 - i].score;
            end.eloDelta = plan.eloDelta;
            end.elo = outcome.elo;
            end.coinDelta = outcome.coinDelta;
            end.balance = outcome.balance;
            end.practiceStreak = outcome.practiceStreak;
            end.myWins = outcome.wins;
            end.myLosses = outcome.losses;
            end.myDraws = outcome.draws;
            end.rounds = new ProtocolCodec.RoundScore[this.rounds.size()];
            for (int r = 0; r < this.rounds.size(); r++) {
                ProtocolCodec.RoundScore score = new ProtocolCodec.RoundScore();
                score.me = pending.roundScores[i][r];
                score.opp = pending.roundScores[1 - i][r];
                end.rounds[r] = score;
            }
            sendTo(i, ProtocolCodec.T_MATCH_END, end);
        }
        if (this.seasons != null) {
            try {
                this.seasons.activeSeason(); // cheap rollover check on every settle
            } catch (RuntimeException e) {
                this.events.emit("SEASON_CHECK_FAILED", this.matchId, "class=" + e.getClass().getName());
            }
        }
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
        this.botEpoch++;
        this.botRandom = newBotRandom();
        this.botMistakesLeft = 0;
        this.seats[1].expectedLevel = 1;
        scheduleBotAnswer(spec, durationMs);
    }

    Random newBotRandom() {
        return new Random(this.matchSeed ^ (this.currentRound * 31L));
    }

    /**
     * Schedules the bot's next answer: a truncated-normal draw around the
     * game's nominal pace (σ=0.25μ clamped to [0.7μ, 1.6μ], replacing the
     * old uniform jitter over a fixed cadence), slowed ×1.3 for the first
     * two answers (reading the question) and ×1.1 in the round's second
     * half (fatigue).
     */
    private void scheduleBotAnswer(GameCatalog.GameSpec spec, long roundDurationMs) {
        scheduleBotAnswer(spec, roundDurationMs, 0L);
    }

    private void scheduleBotAnswer(GameCatalog.GameSpec spec, long roundDurationMs, long recoveryMs) {
        final long epoch = this.botEpoch;
        double mu = this.catalog.botLevelIntervalOf(spec) * this.botPaceMultiplier;
        if (this.seats[1].expectedLevel <= 2) {
            mu *= 1.3d;
        }
        long elapsed = now() - this.roundStartedAt;
        if (elapsed > roundDurationMs / 2) {
            mu *= 1.1d;
        }
        long delay = recoveryMs + truncatedNormal(mu, mu * 0.25d, (long) (0.7d * mu), (long) (1.6d * mu));
        this.botTasks.add(this.scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                botTick(epoch, spec, roundDurationMs);
            }
        }, delay, TimeUnit.MILLISECONDS));
    }

    /** Box-Muller normal draw around mu, clamped to [min, max]. */
    private long truncatedNormal(double mu, double sigma, long min, long max) {
        double u1 = Math.max(1e-9d, this.botRandom.nextDouble());
        double u2 = this.botRandom.nextDouble();
        double normal = Math.sqrt(-2.0d * Math.log(u1)) * Math.cos(2.0d * Math.PI * u2);
        return (long) Math.max(min, Math.min(max, mu + sigma * normal));
    }

    private void botTick(long epoch, GameCatalog.GameSpec spec, long roundDurationMs) {
        synchronized (this) {
            if (this.settledOnce.get() || this.roundEnded || this.seats[1].roundDone
                    || epoch != this.botEpoch) {
                return;
            }
            // A queued answer may execute late, even before the deadline task
            // obtains the lock. Bots have no network grace and never score then.
            if (now() >= this.gameplayDeadlineAt) {
                completeBotAtDeadline(this.currentRound);
                return;
            }
            // Rubber-banding (M2 bot tuning): drift accuracy to keep the
            // human's match engaging, clamped around this bot's own base
            // (practice tiers) instead of the old absolute floor.
            if (this.botAccuracy < 0) {
                this.botAccuracy = botAccuracy();
            } else {
                int gap = totalScore(0) - totalScore(1);
                if (gap > 60) {
                    this.botAccuracy = Math.min(botAccuracyCeiling(), this.botAccuracy + 0.02);
                } else if (gap < -60) {
                    this.botAccuracy = Math.max(botAccuracyFloor(), this.botAccuracy - 0.02);
                }
            }
            double accuracy = this.botAccuracy;
            // Quick-perception games run slightly above the tier baseline.
            if ("15".equals(spec.gameId) || "19".equals(spec.gameId)) {
                accuracy = Math.min(0.98d, accuracy + 0.05);
            }
            // Mistake clusters: occasionally the bot sticks for an answer or
            // two, the way a human visibly stalls mid-round.
            if (this.botMistakesLeft > 0) {
                this.botMistakesLeft--;
                accuracy *= 0.3d;
            } else if (this.botRandom.nextDouble() < 0.08d) {
                this.botMistakesLeft = this.botRandom.nextBoolean() ? 1 : 2;
                accuracy *= 0.3d;
            }
            int level = this.seats[1].expectedLevel;
            if (level > spec.maxLevel()) {
                return;
            }
            this.seats[1].expectedLevel = level + 1;
            boolean correct = this.botRandom.nextDouble() < accuracy;
            if (correct) {
                this.seats[1].roundScores[this.currentRound - 1] += spec.levelScore(level);
                ProtocolCodec.OpponentScore score = new ProtocolCodec.OpponentScore();
                score.matchId = this.matchId;
                score.round = this.currentRound;
                score.score = totalScore(1);
                sendTo(0, ProtocolCodec.T_OPPONENT_SCORE, score);
            }
            // Chain the next answer while the round can still fit one.
            long elapsed = now() - this.roundStartedAt;
            if (elapsed < roundDurationMs && this.seats[1].expectedLevel <= spec.maxLevel()) {
                scheduleBotAnswer(spec, roundDurationMs, correct ? 0L : spec.failureDelayMs());
            }
        }
    }

    private double botAccuracyFloor() {
        return this.botFixedAccuracy != null
                ? Math.max(0.05d, this.botFixedAccuracy - 0.10d) : 0.45d;
    }

    /** The practice tier id whose elo matches this bot's rating (null outside practice). */
    private String practiceTierId() {
        if (!isPracticeMode()) {
            return null;
        }
        for (PkProperties.BotDifficulty tier : this.properties.getPractice().getDifficulties()) {
            if (tier.getElo() == this.botElo) {
                return tier.getId();
            }
        }
        return null;
    }

    private double botAccuracyCeiling() {
        return this.botFixedAccuracy != null
                ? Math.min(0.98d, this.botFixedAccuracy + 0.05d) : 0.95d;
    }

    private double botAccuracy() {
        if (this.botFixedAccuracy != null) {
            return this.botFixedAccuracy;
        }
        int elo = this.seats[0].user != null ? this.seats[0].user.elo : this.botElo;
        return Math.max(0.55, Math.min(0.92, 0.55 + (elo - 1000) * 0.0004));
    }

    public boolean isFriendMode() {
        return MODE_FRIEND.equals(this.mode);
    }

    public boolean isPracticeMode() {
        return MODE_PRACTICE.equals(this.mode);
    }

    private void stopBotBrain() {
        this.botEpoch++;
        for (ScheduledFuture<?> task : this.botTasks) {
            task.cancel(false);
        }
        this.botTasks.clear();
    }

    // -------------------------------------------------------------- helpers

    /** Package-visible clock seam for deterministic lifecycle regression tests. */
    long now() {
        return System.currentTimeMillis();
    }

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
                this.events.emit("SEND_FAILED", this.matchId,
                        "seat=" + index + " user=" + userIds()[index] + " frame=" + EventRing.safeFrameType(type)
                                + " class=" + e.getClass().getName());
            }
        }
    }
}
