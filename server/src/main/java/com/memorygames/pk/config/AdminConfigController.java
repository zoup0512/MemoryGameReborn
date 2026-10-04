package com.memorygames.pk.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runtime configuration endpoint (PRD M2 ops): round durations, the coin
 * economy and matchmaking pacing live in the PkProperties singleton and are
 * read on every use, so mutating the bean applies to the next match/round
 * without a restart. Guarded by a static admin token; with no token
 * configured the endpoint stays disabled. Changes are clamped and audited.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminConfigController {

    private static final Logger log = LoggerFactory.getLogger(AdminConfigController.class);

    private static final long MIN_ROUND_MS = 10_000L;
    private static final long MAX_ROUND_MS = 600_000L;

    private final PkProperties properties;
    private final String adminToken;

    public AdminConfigController(PkProperties properties,
                                 @Value("${pk.admin-token:}") String adminToken) {
        this.properties = properties;
        this.adminToken = adminToken == null ? "" : adminToken.trim();
    }

    public static class ConfigBody {

        public Long roundDurationOverrideMs;
        public Long botLevelIntervalOverrideMs;
        public Long minLevelIntervalOverrideMs;
        public Integer entryFee;
        public Integer winCoins;
        public Integer drawCoins;
        public Integer welcomeCoins;
        public Long botWaitMs;
        public Long roundGraceMs;
        public Long roundIntermissionMs;
        public Long matchFoundDelayMs;
    }

    @GetMapping("/config")
    public ResponseEntity<?> get(@RequestHeader(value = "X-Admin-Token", required = false) String token) {
        if (!authorized(token)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("roundDurationOverrideMs", this.properties.getRoundDurationOverrideMs());
        view.put("botLevelIntervalOverrideMs", this.properties.getBotLevelIntervalOverrideMs());
        view.put("minLevelIntervalOverrideMs", this.properties.getMinLevelIntervalOverrideMs());
        view.put("entryFee", this.properties.getEconomy().getEntryFee());
        view.put("winCoins", this.properties.getEconomy().getWinCoins());
        view.put("drawCoins", this.properties.getEconomy().getDrawCoins());
        view.put("welcomeCoins", this.properties.getEconomy().getWelcomeCoins());
        view.put("botWaitMs", this.properties.getTiming().getBotWaitMs());
        view.put("roundGraceMs", this.properties.getTiming().getRoundGraceMs());
        view.put("roundIntermissionMs", this.properties.getTiming().getRoundIntermissionMs());
        view.put("matchFoundDelayMs", this.properties.getTiming().getMatchFoundDelayMs());
        return ResponseEntity.ok(view);
    }

    @PutMapping("/config")
    public ResponseEntity<?> put(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                 @RequestBody ConfigBody body) {
        if (!authorized(token)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        StringBuilder audit = new StringBuilder("runtime config change:");
        if (body.roundDurationOverrideMs != null) {
            long value = clamp(body.roundDurationOverrideMs, MIN_ROUND_MS, MAX_ROUND_MS);
            this.properties.setRoundDurationOverrideMs(value);
            audit.append(" roundDurationOverrideMs=").append(value);
        }
        if (body.botLevelIntervalOverrideMs != null) {
            long value = clamp(body.botLevelIntervalOverrideMs, 0L, 60_000L);
            this.properties.setBotLevelIntervalOverrideMs(value);
            audit.append(" botLevelIntervalOverrideMs=").append(value);
        }
        if (body.minLevelIntervalOverrideMs != null) {
            long value = clamp(body.minLevelIntervalOverrideMs, 0L, 60_000L);
            this.properties.setMinLevelIntervalOverrideMs(value);
            audit.append(" minLevelIntervalOverrideMs=").append(value);
        }
        if (body.entryFee != null) {
            int value = (int) clamp(body.entryFee, 0L, 1000L);
            this.properties.getEconomy().setEntryFee(value);
            audit.append(" entryFee=").append(value);
        }
        if (body.winCoins != null) {
            int value = (int) clamp(body.winCoins, 0L, 1000L);
            this.properties.getEconomy().setWinCoins(value);
            audit.append(" winCoins=").append(value);
        }
        if (body.drawCoins != null) {
            int value = (int) clamp(body.drawCoins, 0L, 1000L);
            this.properties.getEconomy().setDrawCoins(value);
            audit.append(" drawCoins=").append(value);
        }
        if (body.welcomeCoins != null) {
            int value = (int) clamp(body.welcomeCoins, 0L, 1000L);
            this.properties.getEconomy().setWelcomeCoins(value);
            audit.append(" welcomeCoins=").append(value);
        }
        if (body.botWaitMs != null) {
            long value = clamp(body.botWaitMs, 1_000L, 120_000L);
            this.properties.getTiming().setBotWaitMs(value);
            audit.append(" botWaitMs=").append(value);
        }
        if (body.roundGraceMs != null) {
            long value = clamp(body.roundGraceMs, 1_000L, 120_000L);
            this.properties.getTiming().setRoundGraceMs(value);
            audit.append(" roundGraceMs=").append(value);
        }
        if (body.roundIntermissionMs != null) {
            long value = clamp(body.roundIntermissionMs, 500L, 120_000L);
            this.properties.getTiming().setRoundIntermissionMs(value);
            audit.append(" roundIntermissionMs=").append(value);
        }
        if (body.matchFoundDelayMs != null) {
            long value = clamp(body.matchFoundDelayMs, 500L, 60_000L);
            this.properties.getTiming().setMatchFoundDelayMs(value);
            audit.append(" matchFoundDelayMs=").append(value);
        }
        log.info("{}", audit);
        return get(token);
    }

    private boolean authorized(String token) {
        return !this.adminToken.isEmpty() && this.adminToken.equals(token);
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }
}
