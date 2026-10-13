package com.memorygames.pk.match;

import com.memorygames.pk.admin.AdminMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Crash recovery (P3): every ACTIVE pk_match_instance row at boot belongs
 * to a match that died with a previous process while its entry fees were
 * already escrowed. Each is aborted idempotently (conditional status CAS +
 * refund + REFUND ledger rows in one transaction), so restart loops can
 * never double-pay and players never lose the fee to a crash.
 */
@Component
public class MatchRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MatchRecovery.class);

    private final MatchInstanceRepository instances;
    private final SettlementService settlement;
    private final AdminMetrics metrics;

    /** Captured at bean creation: strictly before this process can seat anyone. */
    private final long startedAt = System.currentTimeMillis();

    public MatchRecovery(MatchInstanceRepository instances, SettlementService settlement,
                         AdminMetrics metrics) {
        this.instances = instances;
        this.settlement = settlement;
        this.metrics = metrics;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<MatchInstance> active = this.instances.findByStatus(MatchInstance.STATUS_ACTIVE);
        if (active.isEmpty()) {
            return;
        }
        int refunded = 0;
        for (MatchInstance instance : active) {
            if (instance.createdAt > this.startedAt) {
                continue; // seated through this process's own sockets
            }
            try {
                if (this.settlement.abortMatch(instance.matchId)) {
                    refunded++;
                    log.info("recovered interrupted match {} (mode={}, fee {} refunded to its seats)",
                            instance.matchId, instance.mode, instance.entryFee);
                }
            } catch (RuntimeException e) {
                log.error("recovery of match {} failed: {}", instance.matchId, e.getMessage());
            }
        }
        log.info("startup recovery: {} interrupted matches found, {} refunded", active.size(), refunded);
    }
}
