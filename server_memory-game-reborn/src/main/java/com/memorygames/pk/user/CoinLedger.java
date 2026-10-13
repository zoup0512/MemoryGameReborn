package com.memorygames.pk.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Append-only coin ledger (P3): every balance mutation tied to a match
 * (entry fee, payout, refund) writes exactly one row inside the same
 * transaction, giving audit, replay protection and reconciliation. Rows
 * with a NULL matchId (welcome grant, admin grants) are exempt from the
 * uniqueness rule by design.
 */
@Entity
@Table(name = "pk_coin_ledger", uniqueConstraints = {
        @jakarta.persistence.UniqueConstraint(name = "uk_pk_coin_ledger_entry",
                columnNames = {"user_id", "reason", "match_id"})})
public class CoinLedger {

    public static final String REASON_WELCOME = "WELCOME";
    public static final String REASON_ENTRY_FEE = "ENTRY_FEE";
    public static final String REASON_PAYOUT = "PAYOUT";
    public static final String REASON_REFUND = "REFUND";
    public static final String REASON_ADMIN = "ADMIN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id", nullable = false)
    public long userId;

    /** Owning match; null for non-match movements. */
    @Column(name = "match_id")
    public String matchId;

    /** Signed coin change. */
    @Column(nullable = false)
    public int amount;

    @Column(nullable = false, length = 32)
    public String reason;

    @Column(name = "created_at", nullable = false)
    public long createdAt;
}
