package com.memorygames.pk.config;

import com.memorygames.pk.admin.AdminMetrics;
import com.memorygames.pk.admin.SessionStats;
import com.memorygames.pk.match.MatchManager;
import com.memorygames.pk.match.Matchmaker;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Observability wiring (P3-1): readiness reflects the match manager's
 * accept/refuse gate (OUT_OF_SERVICE during graceful shutdown), and the
 * in-process AdminMetrics/registry counters are bridged into Micrometer so
 * /actuator/prometheus scrapes the live room, queue and settlement
 * signals instead of the dashboard-only snapshot.
 */
@Configuration
public class ObservabilityConfig {

    @Bean
    public HealthIndicator matchReadiness(MatchManager manager) {
        return new HealthIndicator() {
            @Override
            public Health health() {
                if (manager.acceptingMatches()) {
                    return Health.up().withDetail("activeRooms", manager.activeRooms()).build();
                }
                return Health.outOfService().withDetail("reason", "shutting down").build();
            }
        };
    }

    @Bean
    public MeterBinder pkMetrics(AdminMetrics metrics, Matchmaker matchmaker,
                                 MatchManager manager, SessionStats stats) {
        return new MeterBinder() {
            @Override
            public void bindTo(MeterRegistry registry) {
                Gauge.builder("pk_rooms_active", manager, MatchManager::activeRooms)
                        .description("Live match rooms").register(registry);
                Gauge.builder("pk_queue_depth", matchmaker, Matchmaker::queueSize)
                        .description("Players waiting in the matchmaker queue").register(registry);
                Gauge.builder("pk_db_queue_depth", manager, MatchManager::dbQueueDepth)
                        .description("Settlement/room-creation tasks waiting on the DB pool")
                        .register(registry);
                Gauge.builder("pk_ws_connections", stats, s -> s.connections.get())
                        .description("Open WebSocket connections").register(registry);
                Gauge.builder("pk_ws_authenticated", stats, s -> s.authenticated.get())
                        .description("Authenticated WebSocket sessions").register(registry);

                Gauge.builder("pk_matches_started_total", metrics, m -> m.matchesStarted.get())
                        .register(registry);
                Gauge.builder("pk_matches_settled_total", metrics, m -> m.matchesSettled.get())
                        .register(registry);
                Gauge.builder("pk_settlement_failures_total", metrics, m -> m.settlementFailures.get())
                        .register(registry);
                Gauge.builder("pk_send_overflow_total", metrics, m -> m.sendOverflow.get())
                        .register(registry);
                Gauge.builder("pk_bot_fallbacks_total", metrics, m -> m.botFallbacks.get())
                        .register(registry);
                Gauge.builder("pk_pairings_total", metrics, m -> m.pairings.get())
                        .register(registry);
                Gauge.builder("pk_level_wins_total", metrics, m -> m.levelWins.get())
                        .register(registry);
                Gauge.builder("pk_anomalies_total", metrics, m -> m.anomalies.get())
                        .register(registry);
                Gauge.builder("pk_anticheat_strikes_total", metrics, m -> m.anticheastStrikes.get())
                        .register(registry);
            }
        };
    }
}
