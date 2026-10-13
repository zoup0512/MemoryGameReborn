package com.memorygames.pk.match;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Persistent match lifecycle row (P3): created in the SAME transaction
 * that charges the entry fees (escrow), flipped to SETTLED by the
 * settlement transaction and to ABORTED by voids/cancels/shutdown. On
 * startup every ACTIVE row belongs to a match that died with a previous
 * process and is refunded idempotently — a crash can no longer swallow
 * coins that were already charged.
 */
@Entity
@Table(name = "pk_match_instance")
public class MatchInstance {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_SETTLED = "SETTLED";
    public static final String STATUS_ABORTED = "ABORTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "match_id", nullable = false, unique = true)
    public String matchId;

    @Column(nullable = false)
    public String mode;

    /** Seat A (null never happens today; the schema tolerates it). */
    @Column(name = "user_a")
    public Long userA;

    /** Seat B; null for bot opponents. */
    @Column(name = "user_b")
    public Long userB;

    @Column(name = "entry_fee", nullable = false)
    public int entryFee;

    /** ACTIVE / SETTLED / ABORTED; transitions are conditional UPDATEs. */
    @Column(nullable = false, length = 16)
    public String status;

    @Column(name = "created_at", nullable = false)
    public long createdAt;

    @Column(name = "settled_at")
    public Long settledAt;
}
