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
            account.balance = balance;
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
                                boolean win, boolean draw) {
        account.elo = Math.max(100, account.elo + eloDelta);
        account.balance = Math.max(0, Math.min(account.balance + coinDelta, MAX_BALANCE));
        if (win) {
            account.wins++;
        } else if (draw) {
            account.draws++;
        } else {
            account.losses++;
        }
        account.updatedAt = System.currentTimeMillis();
        this.repository.save(account);
    }

    public int rankIndex(UserAccount account) {
        return this.rating.rankIndex(account.elo);
    }
}
