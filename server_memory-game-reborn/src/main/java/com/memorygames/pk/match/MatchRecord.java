package com.memorygames.pk.match;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Persisted match record for history and audit (PRD F-11). */
@Entity
@Table(name = "pk_match_record")
public class MatchRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /**
     * Settlement idempotency key: exactly one record may exist per match,
     * enforced by the database, so replays (settle retries, restart
     * recovery) can never double-apply coins/ELO/W-L.
     */
    @Column(nullable = false, unique = true)
    public String matchId;

    /** RANKED or FRIEND. */
    @Column(nullable = false)
    public String mode;

    @Column(name = "player_a", nullable = false)
    public long playerA;

    @Column(name = "nickname_a", nullable = false)
    public String nicknameA;

    /** W / L / D from player A's perspective. */
    @Column(name = "result_a", nullable = false)
    public String resultA;

    @Column(name = "score_a", nullable = false)
    public int scoreA;

    @Column(name = "player_b", nullable = false)
    public long playerB;

    @Column(name = "nickname_b", nullable = false)
    public String nicknameB;

    @Column(name = "score_b", nullable = false)
    public int scoreB;

    /** ELO delta applied to player A. */
    @Column(name = "elo_delta_a", nullable = false)
    public int eloDeltaA;

    @Column(nullable = false)
    public long createdAt;

    @Column(nullable = false)
    public long settledAt;

    /** Bot opponent flag for seat B. */
    @Column(nullable = false)
    public boolean botOpponent;

    /**
     * Admin void / shutdown abort: entry refunded, no ELO or W-L counted;
     * excluded from client-facing history and the practice reward cap.
     */
    @Column(nullable = false)
    public boolean voided;
}
