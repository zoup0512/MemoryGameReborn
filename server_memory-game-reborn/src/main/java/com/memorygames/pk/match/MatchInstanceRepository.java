package com.memorygames.pk.match;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MatchInstanceRepository extends JpaRepository<MatchInstance, Long> {

    Optional<MatchInstance> findByMatchId(String matchId);

    /** Leftovers from a crashed process, scanned at startup (P3 recovery). */
    List<MatchInstance> findByStatus(String status);

    /**
     * Conditional status transition; the row count decides idempotency —
     * the loser of a race (settle vs abort, recovery replay) changes
     * nothing.
     */
    @Modifying
    @Query("update MatchInstance m set m.status = :to, m.settledAt = :at "
            + "where m.matchId = :matchId and m.status = :from")
    int transitionStatus(@Param("matchId") String matchId,
                         @Param("from") String from,
                         @Param("to") String to,
                         @Param("at") long at);
}
