package com.memorygames.pk.season;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SeasonRepository extends JpaRepository<Season, Long> {

    Optional<Season> findFirstByFinalizedFalseOrderByStartAtDesc();

    Optional<Season> findFirstByFinalizedTrueOrderByIndexDesc();

    /** The single active season (unique(activeFlag) guarantees at most one). */
    Optional<Season> findFirstByActiveFlagOrderByIdDesc(Integer activeFlag);

    /** All active seasons — exactly one after every rollover path (tests). */
    java.util.List<Season> findByActiveFlag(Integer activeFlag);

    /**
     * Finalizes a season only if it is still the active one; the row count
     * tells the loser of a rollover race to re-read instead of resetting
     * every account's ELO a second time (P2-3).
     */
    @Modifying
    @Query("update Season s set s.finalized = true, s.activeFlag = null "
            + "where s.id = :id and s.activeFlag = 1")
    int finalizeIfActive(@Param("id") Long id);
}
