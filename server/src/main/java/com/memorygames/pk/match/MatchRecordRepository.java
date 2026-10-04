package com.memorygames.pk.match;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MatchRecordRepository extends JpaRepository<MatchRecord, Long> {

    /** Recent matches involving the user (either seat), newest first. */
    @org.springframework.data.jpa.repository.Query("select m from MatchRecord m " +
            "where m.playerA = ?1 or m.playerB = ?1 order by m.settledAt desc")
    List<MatchRecord> findRecentForUser(long userId, Pageable pageable);
}
