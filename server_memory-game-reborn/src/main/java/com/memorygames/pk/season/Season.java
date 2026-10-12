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

    /** Human-facing season number, starts at 1. */
    @Column(nullable = false)
    public int index;

    @Column(nullable = false)
    public long startAt;

    @Column(nullable = false)
    public long endAt;

    @Column(nullable = false)
    public boolean finalized;
}
