package com.memorygames.pk.season;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A ranked season window (PRD F-13); expiry soft-resets ELO. */
@Entity
@Table(name = "pk_season")
public class Season {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** Human-facing season number, starts at 1 ("index" is a reserved word). */
    @Column(name = "season_index", nullable = false)
    public int index;

    @Column(nullable = false)
    public long startAt;

    @Column(nullable = false)
    public long endAt;

    @Column(nullable = false)
    public boolean finalized;

    /**
     * 1 while active, NULL once finalized. The unique constraint admits
     * many NULLs but exactly one 1, so the database itself guarantees a
     * single active season even across instances (P2-3).
     */
    @Column(unique = true)
    public Integer activeFlag;
}
