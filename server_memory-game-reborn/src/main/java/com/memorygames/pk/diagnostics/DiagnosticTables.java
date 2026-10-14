package com.memorygames.pk.diagnostics;

import jakarta.persistence.*;

/** TEXT, not LOB: identical storage semantics on H2 and PostgreSQL. */
public final class DiagnosticTables {
    private DiagnosticTables() {}
    @Entity @Table(name="pk_diagnostic", uniqueConstraints=@UniqueConstraint(columnNames={"user_id","upload_id"}))
    public static class Report {
        @Id @Column(length=36) public String id;
        @Column(nullable=false) public long userId;
        @Column(nullable=false,length=36) public String uploadId;
        @Column(nullable=false) public int schemaVersion;
        @Column(nullable=false,length=80) public String appVersion;
        @Column(nullable=false,length=80) public String buildTime;
        @Column(nullable=false) public long startedAt;
        @Column(nullable=false) public long endedAt;
        @Column(length=80) public String matchId;
        @Column(nullable=false) public long receivedAt;
        @Column(nullable=false) public int bytes;
        @Column(nullable=false,length=64) public String fingerprint;
        @Column(nullable=false,columnDefinition="TEXT") public String content;
    }
    @Entity @Table(name="pk_diagnostic_lock") public static class QuotaLock {
        @Id public int id;
    }
}
