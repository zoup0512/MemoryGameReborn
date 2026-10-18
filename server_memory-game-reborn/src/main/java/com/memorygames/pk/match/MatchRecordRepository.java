package com.memorygames.pk.match;

import com.memorygames.pk.protocol.ProtocolCodec;
import com.memorygames.pk.protocol.ProtocolCodec.MatchStats;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface MatchRecordRepository extends JpaRepository<MatchRecord, Long> {

    boolean existsByMatchId(String matchId);

    interface ModeStatsTotals {
        String getMode();
        long getWins();
        long getLosses();
        long getDraws();
    }

    /** All settled history, grouped in the database and normalized to either seat. */
    @org.springframework.data.jpa.repository.Query("select m.mode as mode, " +
            "sum(case when (m.playerA = ?1 and m.resultA = 'W') " +
            "or (m.playerB = ?1 and m.resultA = 'L') then 1 else 0 end) as wins, " +
            "sum(case when (m.playerA = ?1 and m.resultA = 'L') " +
            "or (m.playerB = ?1 and m.resultA = 'W') then 1 else 0 end) as losses, " +
            "sum(case when m.resultA = 'D' then 1 else 0 end) as draws " +
            "from MatchRecord m where ?1 > 0 and (m.playerA = ?1 or m.playerB = ?1) " +
            "and m.voided = false and m.mode in ('RANKED', 'PRACTICE', 'FRIEND') " +
            "group by m.mode")
    List<ModeStatsTotals> aggregateModeStatsForUser(long userId);

    /** Stable zero-filled wire snapshot; bots never have a shared historical record. */
    @Transactional(readOnly = true)
    default Map<String, MatchStats> modeStatsForUser(long userId) {
        Map<String, MatchStats> stats = new LinkedHashMap<>();
        stats.put(ProtocolCodec.MODE_RANKED, new MatchStats());
        stats.put(ProtocolCodec.MODE_PRACTICE, new MatchStats());
        stats.put(ProtocolCodec.MODE_FRIEND, new MatchStats());
        if (userId <= 0) {
            return stats;
        }
        for (ModeStatsTotals row : aggregateModeStatsForUser(userId)) {
            MatchStats bucket = stats.get(row.getMode());
            if (bucket != null) {
                bucket.wins = row.getWins();
                bucket.losses = row.getLosses();
                bucket.draws = row.getDraws();
            }
        }
        return stats;
    }

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
