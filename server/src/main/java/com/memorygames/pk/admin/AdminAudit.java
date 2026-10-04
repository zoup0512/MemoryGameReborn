package com.memorygames.pk.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Audit trail for every mutating admin operation (PRD admin design 4.8). */
@Entity
@Table(name = "pk_admin_audit")
public class AdminAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public long ts;

    /** e.g. CONFIG_PUT, USER_BAN, MATCH_TERMINATE, MAINTENANCE_ON. */
    @Column(nullable = false)
    public String action;

    @Column(nullable = false)
    public String paramsJson;

    @Column(nullable = false)
    public String operatorIp;

    /** ok | error:<message> */
    @Column(nullable = false)
    public String result;
}
