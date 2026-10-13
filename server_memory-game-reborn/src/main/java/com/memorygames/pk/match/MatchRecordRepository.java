package com.memorygames.pk.match;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MatchRecordRepository extends JpaRepository<MatchRecord, Long> {

    boolean existsByMatchId(String matchId);

    /** Recent matches involving the user (either seat), newest first. */
    @org.springframework.data.jpa.repository.Query("select m from MatchRecord m " +
            "where (m.playerA = ?1 or m.playerB = ?1) and m.voided = false " +
            "order by m.settledAt desc")
    List<MatchRecord> findRecentForUser(long userId, Pageable pageable);

    /** Practice wins settled since the given instant (daily reward cap). */
    @org.springframework.data.jpa.repository.Query("select count(m) from MatchRecord m " +
            "where m.playerA = ?1 and m.mode = 'PRACTICE' and m.resultA = 'W' " +
            "and m.voided = false and m.settledAt >= ?2")
    long countPracticeWinsSince(long userId, long sinceMs);
}
