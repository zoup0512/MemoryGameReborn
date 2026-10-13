package com.memorygames.pk.admin;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-process operational counters since server start (dashboard 4.1).
 * Reset on restart by design; P2 moves these to Prometheus.
 */
@Component
public class AdminMetrics {

    public final AtomicLong matchesStarted = new AtomicLong();
    public final AtomicLong matchesSettled = new AtomicLong();
    public final AtomicLong botFallbacks = new AtomicLong();
    public final AtomicLong queueJoins = new AtomicLong();
    public final AtomicLong pairings = new AtomicLong();
    public final AtomicLong queueWaitTotalMs = new AtomicLong();
    public final AtomicLong levelWins = new AtomicLong();
    public final AtomicLong anomalies = new AtomicLong();
    public final AtomicLong anticheastStrikes = new AtomicLong();
    public final AtomicLong adminOps = new AtomicLong();
    /** WS sessions force-closed because their bounded send queue filled up. */
    public final AtomicLong sendOverflow = new AtomicLong();
    /** Settlement transactions that exhausted their retries. */
    public final AtomicLong settlementFailures = new AtomicLong();

    public Map<String, Object> snapshot() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("matchesStarted", this.matchesStarted.get());
        view.put("matchesSettled", this.matchesSettled.get());
        view.put("botFallbacks", this.botFallbacks.get());
        view.put("queueJoins", this.queueJoins.get());
        view.put("pairings", this.pairings.get());
        view.put("levelWins", this.levelWins.get());
        view.put("anomalies", this.anomalies.get());
        view.put("anticheatStrikes", this.anticheastStrikes.get());
        view.put("adminOps", this.adminOps.get());
        view.put("sendOverflow", this.sendOverflow.get());
        view.put("settlementFailures", this.settlementFailures.get());
        long waits = this.pairings.get() + this.botFallbacks.get();
        view.put("avgQueueWaitMs", waits == 0 ? 0 : this.queueWaitTotalMs.get() / waits);
        return view;
    }
}
