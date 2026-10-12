package com.memorygames.pk.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByUuid(String uuid);

    Optional<UserAccount> findByToken(String token);

    @org.springframework.data.jpa.repository.Query("select u from UserAccount u " +
            "where lower(u.nickname) like lower(concat('%', :q, '%')) or u.uuid = :q " +
            "order by u.id desc")
    List<UserAccount> search(@org.springframework.data.repository.query.Param("q") String q);

    List<UserAccount> findTop5ByOrderByBalanceDesc();

    List<UserAccount> findTop20ByOrderByEloDesc();

    long countByRestrictedMatchesGreaterThan(int matches);

    long countByBannedUntilGreaterThan(long until);
}
