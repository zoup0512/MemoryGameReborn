package com.memorygames.pk.user;

import jakarta.persistence.*;

/** Mappings also let the existing create-drop test profile create the account tables. */
public final class AccountTables {
    private AccountTables() {}
    @Entity @Table(name="pk_identity") public static class Identity {
        @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
        @Column(nullable=false,unique=true) public long userId;
        @Column(unique=true) public String email;
        public String passwordHash;
        @Column(unique=true) public String installationId;
        public String guestSecretHash;
        @Column(nullable=false) public boolean emailVerified;
    }
    @Entity @Table(name="pk_device",uniqueConstraints=@UniqueConstraint(columnNames={"user_id","installation_id"})) public static class Device {
        @Id public String id;
        @Column(nullable=false) public long userId;
        @Column(nullable=false) public String installationId;
        @Column(nullable=false) public String deviceName;
        @Column(nullable=false) public long lastActiveAt;
    }
    @Entity @Table(name="pk_session") public static class Session {
        @Id public String id;
        @Column(nullable=false) public long userId;
        @Column(nullable=false) public String deviceId;
        @Column(nullable=false,unique=true) public String accessHash;
        @Column(nullable=false) public long accessExpiresAt;
        @Column(nullable=false) public long refreshExpiresAt;
        @Column(nullable=false) public boolean revoked;
        @Column(nullable=false) public long createdAt;
    }
    @Entity @Table(name="pk_refresh") public static class Refresh {
        @Id public String id;
        @Column(nullable=false) public String sessionId;
        @Column(nullable=false) public boolean used;
    }
    @Entity @Table(name="pk_verification") public static class Verification {
        @Id public String id;
        @Column(nullable=false) public long userId;
        @Column(nullable=false) public String email;
        @Column(nullable=false) public String purpose;
        @Column(nullable=false) public String codeHash;
        @Column(nullable=false) public long expiresAt;
        @Column(nullable=false) public int attempts;
        @Column(nullable=false) public boolean consumed;
    }
    @Entity @Table(name="pk_nickname_audit") public static class NicknameAudit {
        @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
        @Column(nullable=false) public long userId;
        @Column(nullable=false) public long createdAt;
        @Column(nullable=false,length=32) public String result;
        @Column(nullable=false,length=128) public String requestId;
        @Column(nullable=false,length=64) public String digest;
        @Column(nullable=false,length=64) public String policyVersion;
    }
    @Entity @Table(name="pk_account_audit") public static class Audit {
        @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
        @Column(nullable=false) public long userId;
        @Column(nullable=false) public String action;
        @Column(nullable=false) public long createdAt;
    }
}
