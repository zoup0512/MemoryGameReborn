package com.memorygames.pk;

import com.memorygames.pk.config.PkProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Memory Games online PK server (see docs/online-pk-prd.md).
 *
 * Modules: WebSocket gateway, matchmaker with bot fallback, per-match room
 * state machines, server-authoritative scoring from the level-win stream,
 * ELO rating and the coin ledger. State that must survive restarts (users,
 * balances) lives in JPA; live matches live in memory only and are
 * forfeited on restart, which the client tolerates via its reconnect
 * window.
 */
@SpringBootApplication
@EnableConfigurationProperties(PkProperties.class)
public class PkServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PkServerApplication.class, args);
    }
}
