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
    private final PkProperties properties;
    private final Rating rating;

    public UserService(UserRepository repository, PkProperties properties, Rating rating) {
        this.repository = repository;
        this.properties = properties;
        this.rating = rating;
    }

    @Transactional
    public UserAccount register(String uuid, String nickname, int importedBalance) {
        if (nickname == null || nickname.isBlank()) {
            nickname = "Player";
        }
        if (nickname.length() > 24) {
            nickname = nickname.substring(0, 24);
        }
        int balance = Math.max(0, Math.min(importedBalance, MAX_BALANCE));
        UserAccount account = this.repository.findByUuid(uuid).orElse(null);
        if (account == null) {
            account = new UserAccount();
            account.uuid = uuid;
            account.elo = this.properties.getRating().getInitialElo();
            account.wins = 0;
            account.losses = 0;
            account.draws = 0;
            // Server-authoritative welcome grant on first registration; the
            // client no longer gifts itself coins (its grant could never
            // reach this balance and match_join would 402).
            account.balance = Math.min(balance + this.properties.getEconomy().getWelcomeCoins(), MAX_BALANCE);
            account.createdAt = System.currentTimeMillis();
        }
        account.nickname = nickname;
        account.token = UUID.randomUUID().toString().replace("-", "");
        account.updatedAt = System.currentTimeMillis();
        return this.repository.save(account);
    }

    @Transactional(readOnly = true)
    public Optional<UserAccount> byToken(String token) {
        return this.repository.findByToken(token);
    }

    @Transactional
    public void chargeEntryFee(UserAccount account, int fee) {
        account.balance = Math.max(0, account.balance - fee);
        account.updatedAt = System.currentTimeMillis();
        this.repository.save(account);
    }

    @Transactional
    public void applySettlement(UserAccount account, int eloDelta, int coinDelta,
                                boolean win, boolean draw, boolean practice) {
        account.elo = Math.max(100, account.elo + eloDelta);
        account.balance = Math.max(0, Math.min(account.balance + coinDelta, MAX_BALANCE));
        if (win) {
            account.wins++;
        } else if (draw) {
            account.draws++;
        } else {
            account.losses++;
        }
        if (practice) {
            // The practice streak counts consecutive vs-bot wins only; ranked
            // and friend results leave it untouched.
            int streak = account.practiceStreak != null ? account.practiceStreak : 0;
            account.practiceStreak = win ? streak + 1 : 0;
        }
        account.updatedAt = System.currentTimeMillis();
        this.repository.save(account);
    }

    /** Admin balance grant/charge; never negative, capped as registrations. */
    @Transactional
    public UserAccount adjustBalance(UserAccount account, int delta) {
        account.balance = Math.max(0, Math.min(account.balance + delta, MAX_BALANCE));
        account.updatedAt = System.currentTimeMillis();
        return this.repository.save(account);
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

    /** Persists anti-cheat flag changes without touching match counters. */
    @Transactional
    public void saveFlags(UserAccount account) {
        account.updatedAt = System.currentTimeMillis();
        this.repository.save(account);
    }

    /** Top-20 leaderboard by ELO (TOP Online board). */
    public java.util.List<UserAccount> topByElo() {
        return this.repository.findTop20ByOrderByEloDesc();
    }

    public int rankIndex(UserAccount account) {
        return this.rating.rankIndex(account.elo);
    }
}
