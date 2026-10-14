package com.memorygames.pk.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByUuid(String uuid);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from UserAccount u where u.uuid = :uuid")
    Optional<UserAccount> findByUuidForUpdate(@org.springframework.data.repository.query.Param("uuid") String uuid);

    Optional<UserAccount> findByToken(String token);

    /** Row-level lock for settlement transactions (deadlock-free: callers lock in id order). */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from UserAccount u where u.id = :id")
    Optional<UserAccount> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Query("select u from UserAccount u " +
            "where lower(u.nickname) like lower(concat('%', :q, '%')) or u.uuid = :q " +
            "order by u.id desc")
    List<UserAccount> search(@org.springframework.data.repository.query.Param("q") String q);

    List<UserAccount> findTop5ByOrderByBalanceDesc();

    List<UserAccount> findTop20ByOrderByEloDesc();

    List<UserAccount> findTop20ByDeletedFalseAndDeleteRequestedAtAndBannedUntilLessThanEqualOrderByEloDesc(
            long deleteRequestedAt, long now);

    long countByRestrictedMatchesGreaterThan(int matches);

    long countByBannedUntilGreaterThan(long until);

    /**
     * Season soft reset in one statement (P2-3): halves each account's
     * distance to the initial rating, floored at 100. Works on H2 and
     * PostgreSQL (both truncate integer division toward zero).
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = "update pk_user set elo = "
            + "case when :initial + (elo - :initial) / 2 < 100 then 100 "
            + "else :initial + (elo - :initial) / 2 end, "
            + "updated_at = :now", nativeQuery = true)
    int softResetElo(@org.springframework.data.repository.query.Param("initial") int initial,
                     @org.springframework.data.repository.query.Param("now") long now);
}
