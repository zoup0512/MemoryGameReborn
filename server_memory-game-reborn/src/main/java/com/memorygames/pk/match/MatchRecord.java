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

    @Column(nullable = false)
    public String matchId;

    /** RANKED or FRIEND. */
    @Column(nullable = false)
    public String mode;

    @Column(nullable = false)
    public long playerA;

    @Column(nullable = false)
    public String nicknameA;

    /** W / L / D from player A's perspective. */
    @Column(nullable = false)
    public String resultA;

    @Column(nullable = false)
    public int scoreA;

    @Column(nullable = false)
    public long playerB;

    @Column(nullable = false)
    public String nicknameB;

    @Column(nullable = false)
    public int scoreB;

    /** ELO delta applied to player A. */
    @Column(nullable = false)
    public int eloDeltaA;

    @Column(nullable = false)
    public long createdAt;

    @Column(nullable = false)
    public long settledAt;

    /** Bot opponent flag for seat B. */
    @Column(nullable = false)
    public boolean botOpponent;
}
