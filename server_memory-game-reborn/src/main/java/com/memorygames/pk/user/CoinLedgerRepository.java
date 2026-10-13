package com.memorygames.pk.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CoinLedgerRepository extends JpaRepository<CoinLedger, Long> {

    List<CoinLedger> findTop50ByUserIdOrderByCreatedAtDesc(long userId);
}
