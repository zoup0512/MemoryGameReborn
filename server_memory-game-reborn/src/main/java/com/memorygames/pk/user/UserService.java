package com.memorygames.pk.user;

import com.memorygames.pk.config.PkProperties;
import com.memorygames.pk.rating.Rating;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** Account lifecycle: guest registration, token auth, settlement writes. */
@Service
public class UserService {

    private static final int MAX_BALANCE = 100000;

    private final UserRepository repository;
    private final CoinLedgerRepository ledger;
    private final PkProperties properties;
    private final Rating rating;

    public UserService(UserRepository repository, CoinLedgerRepository ledger,
                       PkProperties properties, Rating rating) {
        this.repository = repository;
        this.ledger = ledger;
        this.properties = properties;
        this.rating = rating;
    }

    /**
     * Guest registration. The balance a client claims is IGNORED (P1-3): a
     * fresh account starts with the server-side welcome grant only, keeping
     * the economy server-authoritative. Re-registering an existing UUID
     * rotates the token — that is the guest-recovery path, so the gateway
     * rate-limits registrations instead of requiring the old token.
     */
    @Transactional
    public UserAccount register(String uuid, String nickname) {
        if (nickname == null || nickname.isBlank()) {
            nickname = "Player";
        }
        if (nickname.length() > 24) {
            nickname = nickname.substring(0, 24);
        }
        UserAccount account = this.repository.findByUuid(uuid).orElse(null);
        boolean isNew = account == null;
        if (isNew) {
            account = new UserAccount();
            account.uuid = uuid;
            account.elo = this.properties.getRating().getInitialElo();
            account.wins = 0;
            account.losses = 0;
            account.draws = 0;
            // Server-authoritative welcome grant on first registration; the
            // client no longer gifts itself coins (its grant could never
            // reach this balance and match_join would 402).
            account.balance = Math.min(this.properties.getEconomy().getWelcomeCoins(), MAX_BALANCE);
            account.createdAt = System.currentTimeMillis();
        }
        account.nickname = nickname;
        account.token = UUID.randomUUID().toString().replace("-", "");
        account.updatedAt = System.currentTimeMillis();
        account = this.repository.save(account); // assigns the ledger's user id
        if (isNew && account.balance > 0) {
            writeLedger(account, null, account.balance, CoinLedger.REASON_WELCOME);
        }
        return account;
    }

    @Transactional(readOnly = true)
    public Optional<UserAccount> byToken(String token) {
        return this.repository.findByToken(token);
    }

    /** Admin/manual balance grant or charge; never negative, capped. */
    @Transactional
    public UserAccount adjustBalance(UserAccount account, int delta, String reason) {
        account.balance = Math.max(0, Math.min(account.balance + delta, MAX_BALANCE));
        account.updatedAt = System.currentTimeMillis();
        UserAccount saved = this.repository.save(account);
        writeLedger(account, null, delta, CoinLedger.REASON_ADMIN);
        return saved;
    }

    private void writeLedger(UserAccount account, String matchId, int amount, String reason) {
        if (amount == 0) {
            return;
        }
        CoinLedger row = new CoinLedger();
        row.userId = account.id;
        row.matchId = matchId;
        row.amount = amount;
        row.reason = reason;
        row.createdAt = System.currentTimeMillis();
        this.ledger.save(row);
    }

    /** Admin ELO override. */
    @Transactional
    public UserAccount setElo(UserAccount account, int elo) {
        account.elo = Math.max(100, elo);
        account.updatedAt = System.currentTimeMillis();
        return this.repository.save(account);
    }

    /** Bot-only restriction; 0 lifts it. */
    @Transactional
    public UserAccount setRestriction(UserAccount account, int matches) {
        account.restrictedMatches = Math.max(0, matches);
        account.suspicion = 0;
        account.updatedAt = System.currentTimeMillis();
        return this.repository.save(account);
    }

    /** Ban for a number of hours; 0 unbans. */
    @Transactional
    public UserAccount setBan(UserAccount account, int hours) {
        account.bannedUntil = hours <= 0 ? 0L : System.currentTimeMillis() + hours * 3600000L;
        account.updatedAt = System.currentTimeMillis();
        return this.repository.save(account);
    }

    /** GDPR-style deletion: refuse auth, anonymize, keep match history. */
    @Transactional
    public UserAccount markDeleted(UserAccount account) {
        account.deleted = true;
        account.token = "";
        account.nickname = "deleted-" + account.id;
        account.updatedAt = System.currentTimeMillis();
        return this.repository.save(account);
    }

    /** Top-20 leaderboard by ELO (TOP Online board). */
    public java.util.List<UserAccount> topByElo() {
        return this.repository.findTop20ByOrderByEloDesc();
    }

    public int rankIndex(UserAccount account) {
        return this.rating.rankIndex(account.elo);
    }
}
