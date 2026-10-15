package com.memorygames.pk.user;

import com.memorygames.pk.config.PkProperties;
import com.memorygames.pk.rating.Rating;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate db;
    private final ApplicationEventPublisher events;
    private final NicknameService nicknames;
    private final org.springframework.transaction.support.TransactionTemplate transactions;

    public UserService(UserRepository repository, CoinLedgerRepository ledger,
                       PkProperties properties, Rating rating, JdbcTemplate db,
                       ApplicationEventPublisher events, NicknameService nicknames,
                       org.springframework.transaction.PlatformTransactionManager manager) {
        this.repository = repository;
        this.ledger = ledger;
        this.properties = properties;
        this.rating = rating;
        this.db = db;
        this.events = events;
        this.nicknames = nicknames;
        this.transactions = new org.springframework.transaction.support.TransactionTemplate(manager);
    }

    /**
     * Guest registration. The balance a client claims is IGNORED (P1-3): a
     * fresh account starts with the server-side welcome grant only, keeping
     * the economy server-authoritative. Re-registering an existing UUID
     * rotates the token only for an unmigrated legacy guest. UUID is not a
     * recovery credential once the account has migrated or been upgraded.
     */
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public UserAccount register(String uuid, String nickname) {
        if(uuid==null || uuid.isBlank() || uuid.length()>128) throw new AccountException(400,"INVALID_INPUT","invalid uuid");
        boolean exists=repository.findByUuid(uuid).isPresent();
        String context="legacy:"+uuid;
        NicknameService.Approval approval=!exists && nickname!=null ? nicknames.review(context,0,context,nickname):null;
        return transactions.execute(status -> registerApproved(uuid,nickname,context,approval));
    }

    /** Internal creation called only inside the enclosing account transaction after external review. */
    UserAccount registerApproved(String uuid,String nickname,String context,NicknameService.Approval approval) {
        UserAccount account = this.repository.findByUuidForUpdate(uuid).orElse(null);
        boolean isNew = account == null;
        if (!isNew) {
            if (account.deleted) {
                throw new AccountException(403, "ACCOUNT_DELETED", "account deleted");
            }
            if (account.deleteRequestedAt > 0) {
                throw new AccountException(403, "ACCOUNT_PENDING_DELETION", "account pending deletion");
            }
            if (account.bannedUntil > System.currentTimeMillis()) {
                throw new AccountException(403, "ACCOUNT_BANNED", "account banned");
            }
            if (account.legacyAuthDisabled || !"GUEST".equals(account.accountType)) {
                throw new AccountException(401, "LEGACY_AUTH_DISABLED", "legacy auth disabled");
            }
        }
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
        // UUID recovery is never a rename endpoint, even before the first profile change.
        if(isNew) {
            if(nickname==null) nicknames.generate(account);
            else nicknames.apply(account,context,context,nickname,approval,false);
        }
        account.token = UUID.randomUUID().toString().replace("-", "");
        account.updatedAt = System.currentTimeMillis();
        account = this.repository.save(account); // assigns the ledger's user id
        if (isNew && account.balance > 0) {
            writeLedger(account, null, account.balance, CoinLedger.REASON_WELCOME);
        }
        return account;
    }

    /** Resolve an unmigrated legacy owner; the gateway must enforce its current ban. */
    @Transactional(readOnly = true)
    public Optional<UserAccount> byToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return this.repository.findByToken(token).filter(account ->
                !account.legacyAuthDisabled && "GUEST".equals(account.accountType)
                        && !account.deleted && account.deleteRequestedAt == 0);
    }

    /** Admin/manual balance grant or charge; never negative, capped. */
    @Transactional
    public UserAccount adjustBalance(UserAccount account, int delta, String reason) {
        account = this.repository.findByIdForUpdate(account.id).orElseThrow();
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
        account = this.repository.findByIdForUpdate(account.id).orElseThrow();
        account.elo = Math.max(100, elo);
        account.updatedAt = System.currentTimeMillis();
        return this.repository.save(account);
    }

    /** Bot-only restriction; 0 lifts it. */
    @Transactional
    public UserAccount setRestriction(UserAccount account, int matches) {
        account = this.repository.findByIdForUpdate(account.id).orElseThrow();
        account.restrictedMatches = Math.max(0, matches);
        account.suspicion = 0;
        account.updatedAt = System.currentTimeMillis();
        return this.repository.save(account);
    }

    /** Ban for a number of hours; 0 unbans. */
    @Transactional
    public UserAccount setBan(UserAccount account, int hours) {
        account = this.repository.findByIdForUpdate(account.id).orElseThrow();
        account.bannedUntil = hours <= 0 ? 0L : System.currentTimeMillis() + hours * 3600000L;
        account.updatedAt = System.currentTimeMillis();
        return this.repository.save(account);
    }

    /** GDPR-style deletion: refuse auth, anonymize, keep match history. */
    @Transactional
    public UserAccount markDeleted(UserAccount account) {
        account = this.repository.findByIdForUpdate(account.id).orElseThrow();
        account.deleted = true;
        account.legacyAuthDisabled = true;
        account.token = AccountService.randomToken();
        nicknames.anonymize(account);
        account.avatarValue = "avatar_1";
        account.updatedAt = System.currentTimeMillis();
        UserAccount saved = this.repository.saveAndFlush(account);
        for (String sessionId : this.db.queryForList(
                "select id from pk_session where user_id=? and revoked=false", String.class, account.id)) {
            this.db.update("update pk_session set revoked=true where id=?", sessionId);
            this.events.publishEvent(new AccountService.Revoked(sessionId));
        }
        this.db.update("insert into pk_account_audit(user_id,action,created_at) values(?,?,?)",
                account.id, "SESSIONS_REVOKED", account.updatedAt);
        this.events.publishEvent(new AccountService.LegacyDisabled(account.id));
        return saved;
    }

    /** Top-20 leaderboard by ELO (TOP Online board). */
    public java.util.List<UserAccount> topByElo() {
        return this.repository.findTop20ByDeletedFalseAndDeleteRequestedAtAndBannedUntilLessThanEqualOrderByEloDesc(
                0L, System.currentTimeMillis());
    }

    public int rankIndex(UserAccount account) {
        return this.rating.rankIndex(account.elo);
    }
}
