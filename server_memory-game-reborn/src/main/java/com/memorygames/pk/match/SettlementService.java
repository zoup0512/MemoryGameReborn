package com.memorygames.pk.match;

import com.memorygames.pk.user.CoinLedger;
import com.memorygames.pk.user.CoinLedgerRepository;
import com.memorygames.pk.user.UserAccount;
import com.memorygames.pk.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Single-transaction settlement (P1-1): both seats' ELO/coins/W-L, the
 * anti-cheat flag writes and the match record commit atomically, with the
 * record's unique matchId acting as the persistent idempotency key. A
 * crash between the old per-seat updates could leave a half-settled match
 * that settledOnce would never retry; here either everything lands or the
 * caller retries the identical command safely.
 */
@Service
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);
    private static final int MAX_BALANCE = 100000;

    /** One seat's pre-computed settlement plan (pure data, no entity refs). */
    public static class SeatPlan {

        public Long userId;
        public String nickname;
        public int score;
        /** W / L / D from this seat's perspective. */
        public String result;
        public int eloDelta;
        /** Payout for a non-voided settle; refunds are derived from entryFee. */
        public int coinDelta;
        public boolean win;
        public boolean draw;
    }

    /** Anti-cheat outcome for one seat, applied inside the same transaction. */
    public static class FlagUpdate {

        public boolean strike;
        public int suspicionAfter;
        /** Set when the strike crossed the threshold and a restriction started. */
        public Integer restrictionAfter;
        /** True when an existing restriction must tick down after this match. */
        public boolean decrementRestriction;
    }

    public static class Command {

        public String matchId;
        public String mode;
        public boolean practice;
        /** Admin void / shutdown abort: refund entry, move nothing else. */
        public boolean voided;
        /** Whether the entry fee was actually charged at match start. */
        public boolean entryCharged;
        public int entryFee;
        public SeatPlan[] seats = new SeatPlan[2];
        public long createdAt;
        public int botElo;
        public Map<Long, FlagUpdate> flagUpdates = new HashMap<>();
        /** Practice tier payout inputs; the daily cap query runs in-transaction. */
        public int practiceTierWinCoins;
        public int practiceDailyCap;
    }

    /** Post-commit account state used to build the match_end payloads. */
    public static class SeatOutcome {

        public long userId = -1L;
        public int elo;
        public int balance;
        public int wins;
        public int losses;
        public int draws;
        public int practiceStreak;
        /** Effective coin change actually applied (refund, payout or 0). */
        public int coinDelta;
    }

    public static class Result {

        public boolean alreadySettled;
        public SeatOutcome[] seats = new SeatOutcome[2];
    }

    private final UserRepository users;
    private final MatchRecordRepository records;
    private final MatchInstanceRepository instances;
    private final CoinLedgerRepository ledger;

    public SettlementService(UserRepository users, MatchRecordRepository records,
                             MatchInstanceRepository instances, CoinLedgerRepository ledger) {
        this.users = users;
        this.records = records;
        this.instances = instances;
        this.ledger = ledger;
    }

    // ------------------------------------------------- escrow & abort (P3)

    /**
     * Escrows a new match: the entry fees leave both balances and the
     * MatchInstance(ACTIVE) row appears in ONE transaction, so a crash
     * between "coins taken" and "match registered" is impossible. Practice
     * matches open with fee 0 (the row still exists — recovery marks it
     * ABORTED without paying anything).
     */
    @Transactional
    public void openMatch(String matchId, String mode, Long userA, Long userB, int entryFee) {
        if (this.instances.findByMatchId(matchId).isPresent()) {
            return; // defensive replay guard; matchIds are random 12-hex
        }
        MatchInstance instance = new MatchInstance();
        instance.matchId = matchId;
        instance.mode = mode;
        instance.userA = userA;
        instance.userB = userB;
        instance.entryFee = Math.max(0, entryFee);
        instance.status = MatchInstance.STATUS_ACTIVE;
        instance.createdAt = System.currentTimeMillis();
        this.instances.save(instance);
        if (instance.entryFee == 0) {
            return;
        }
        long now = System.currentTimeMillis();
        for (UserAccount account : lockUsers(userA, userB).values()) {
            account.balance = Math.max(0, account.balance - instance.entryFee);
            account.updatedAt = now;
            this.users.save(account);
            writeLedger(account.id, matchId, -instance.entryFee, CoinLedger.REASON_ENTRY_FEE, now);
        }
    }

    /**
     * Idempotent abort for matches that never played (start-gate timeout,
     * admin void, shutdown, crash recovery): flips ACTIVE -> ABORTED with a
     * conditional UPDATE and refunds the escrowed fee inside the same
     * transaction. Replay calls change nothing.
     *
     * @return true when this call performed the abort
     */
    @Transactional
    public boolean abortMatch(String matchId) {
        MatchInstance instance = this.instances.findByMatchId(matchId).orElse(null);
        if (instance == null
                || this.instances.transitionStatus(matchId, MatchInstance.STATUS_ACTIVE,
                MatchInstance.STATUS_ABORTED, System.currentTimeMillis()) == 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        for (UserAccount account : lockUsers(instance.userA, instance.userB).values()) {
            account.balance = clamp(account.balance + instance.entryFee);
            account.updatedAt = now;
            this.users.save(account);
            writeLedger(account.id, matchId, instance.entryFee, CoinLedger.REASON_REFUND, now);
        }
        return true;
    }

    private void writeLedger(long userId, String matchId, int amount, String reason, long now) {
        if (amount == 0) {
            return;
        }
        CoinLedger row = new CoinLedger();
        row.userId = userId;
        row.matchId = matchId;
        row.amount = amount;
        row.reason = reason;
        row.createdAt = now;
        this.ledger.save(row);
    }

    /**
     * Applies the whole settlement in one transaction. Idempotent: a match
     * whose record already exists returns {@code alreadySettled} with the
     * current account state, and a concurrent duplicate insert loses the
     * unique(matchId) race and surfaces as alreadySettled on retry.
     */
    @Transactional
    public Result settle(Command cmd) {
        if (this.records.existsByMatchId(cmd.matchId)) {
            return snapshotOnly(cmd);
        }
        try {
            return apply(cmd);
        } catch (DataIntegrityViolationException e) {
            // Lost the unique(matchId) race against a replayed command: the
            // other transaction's effects stand.
            log.info("settlement {} raced a duplicate insert; treating as settled", cmd.matchId);
            throw e;
        }
    }

    private Result apply(Command cmd) {
        // The instance status CAS is the primary idempotency gate: a match
        // that already left ACTIVE (settled, aborted, recovered) must not
        // pay or refund a second time, whatever path raced in.
        String target = cmd.voided ? MatchInstance.STATUS_ABORTED : MatchInstance.STATUS_SETTLED;
        if (this.instances.transitionStatus(cmd.matchId, MatchInstance.STATUS_ACTIVE, target,
                System.currentTimeMillis()) == 0) {
            return snapshotOnly(cmd);
        }
        Map<Long, UserAccount> locked = lockHumans(cmd);
        long now = System.currentTimeMillis();
        Result result = new Result();
        for (int i = 0; i < 2; i++) {
            SeatPlan plan = cmd.seats[i];
            SeatOutcome outcome = new SeatOutcome();
            UserAccount account = plan.userId != null ? locked.get(plan.userId) : null;
            if (account != null) {
                if (cmd.voided) {
                    int refund = cmd.entryCharged ? cmd.entryFee : 0;
                    account.balance = clamp(account.balance + refund);
                    outcome.coinDelta = refund;
                } else {
                    int coinDelta = plan.coinDelta;
                    if (cmd.practice && coinDelta > 0) {
                        coinDelta = capPracticePayout(account.id, plan, cmd);
                    }
                    account.elo = Math.max(100, account.elo + plan.eloDelta);
                    account.balance = clamp(account.balance + coinDelta);
                    if (plan.win) {
                        account.wins++;
                    } else if (plan.draw) {
                        account.draws++;
                    } else {
                        account.losses++;
                    }
                    if (cmd.practice) {
                        int streak = account.practiceStreak != null ? account.practiceStreak : 0;
                        account.practiceStreak = plan.win ? streak + 1 : 0;
                    }
                    outcome.coinDelta = coinDelta;
                }
                account.updatedAt = now;
                applyFlags(account, cmd.flagUpdates.get(plan.userId));
                this.users.save(account);
                writeLedger(account.id, cmd.matchId, outcome.coinDelta,
                        cmd.voided ? CoinLedger.REASON_REFUND : CoinLedger.REASON_PAYOUT, now);
                fill(outcome, account);
            } else {
                outcome.userId = plan.userId != null ? plan.userId : -1L;
                outcome.elo = cmd.botElo;
            }
            result.seats[i] = outcome;
        }
        persistRecord(cmd);
        return result;
    }

    /** Read-only fallback when the match was already settled by an earlier attempt. */
    private Result snapshotOnly(Command cmd) {
        Result result = new Result();
        result.alreadySettled = true;
        for (int i = 0; i < 2; i++) {
            SeatPlan plan = cmd.seats[i];
            SeatOutcome outcome = new SeatOutcome();
            outcome.userId = plan.userId != null ? plan.userId : -1L;
            outcome.elo = cmd.botElo;
            if (plan.userId != null) {
                this.users.findById(plan.userId).ifPresent(account -> fill(outcome, account));
            }
            result.seats[i] = outcome;
        }
        return result;
    }

    private void persistRecord(Command cmd) {
        MatchRecord record = new MatchRecord();
        record.matchId = cmd.matchId;
        record.mode = cmd.mode;
        record.playerA = cmd.seats[0].userId != null ? cmd.seats[0].userId : -1L;
        record.nicknameA = cmd.seats[0].nickname;
        record.resultA = cmd.seats[0].result;
        record.scoreA = cmd.seats[0].score;
        record.playerB = cmd.seats[1].userId != null ? cmd.seats[1].userId : -1L;
        record.nicknameB = cmd.seats[1].nickname;
        record.scoreB = cmd.seats[1].score;
        record.eloDeltaA = cmd.seats[0].eloDelta;
        record.createdAt = cmd.createdAt;
        record.settledAt = System.currentTimeMillis();
        record.botOpponent = cmd.seats[1].userId == null;
        record.voided = cmd.voided;
        this.records.save(record);
    }

    private void applyFlags(UserAccount account, FlagUpdate flag) {
        if (flag == null) {
            return;
        }
        if (flag.decrementRestriction && account.restrictedMatches > 0) {
            account.restrictedMatches--;
        }
        if (flag.strike) {
            account.suspicion = flag.suspicionAfter;
            if (flag.restrictionAfter != null) {
                account.restrictedMatches = flag.restrictionAfter;
                account.suspicion = 0;
            }
        }
    }

    private int capPracticePayout(long userId, SeatPlan plan, Command cmd) {
        long dayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long winsToday = this.records.countPracticeWinsSince(userId, dayStart);
        if (winsToday >= cmd.practiceDailyCap) {
            return 0;
        }
        return plan.win ? cmd.practiceTierWinCoins : cmd.practiceTierWinCoins / 2;
    }

    private Map<Long, UserAccount> lockHumans(Command cmd) {
        return lockUsers(cmd.seats[0] != null ? cmd.seats[0].userId : null,
                cmd.seats[1] != null ? cmd.seats[1].userId : null);
    }

    private Map<Long, UserAccount> lockUsers(Long userA, Long userB) {
        List<Long> ids = new ArrayList<>(2);
        if (userA != null) {
            ids.add(userA);
        }
        if (userB != null) {
            ids.add(userB);
        }
        // Deterministic lock order keeps concurrent settlements deadlock-free.
        Collections.sort(ids);
        Map<Long, UserAccount> locked = new HashMap<>();
        for (Long id : ids) {
            this.users.findByIdForUpdate(id).ifPresent(account -> locked.put(id, account));
        }
        return locked;
    }

    private static void fill(SeatOutcome outcome, UserAccount account) {
        outcome.userId = account.id;
        outcome.elo = account.elo;
        outcome.balance = account.balance;
        outcome.wins = account.wins;
        outcome.losses = account.losses;
        outcome.draws = account.draws;
        outcome.practiceStreak = account.practiceStreak != null ? account.practiceStreak : 0;
    }

    private static int clamp(int balance) {
        return Math.max(0, Math.min(balance, MAX_BALANCE));
    }
}
