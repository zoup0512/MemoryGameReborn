package com.memorygames.pk.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Persisted PK account (guest identity, PRD 3.3). */
@Entity
@Table(name = "pk_user")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(unique = true, nullable = false)
    public String uuid;

    @Column(unique = true, nullable = false)
    public String token;

    @Column(nullable = false)
    public String nickname;

    @Column(nullable = false)
    public int elo;

    @Column(nullable = false)
    public int wins;

    @Column(nullable = false)
    public int losses;

    @Column(nullable = false)
    public int draws;

    @Column(nullable = false)
    public int balance;

    @Column(nullable = false)
    public long createdAt;

    @Column(nullable = false)
    public long updatedAt;
}
