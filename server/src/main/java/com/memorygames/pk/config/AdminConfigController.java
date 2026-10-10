package com.memorygames.pk.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.memorygames.pk.admin.AdminAuditService;
import com.memorygames.pk.admin.AdminGuard;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Lazy;
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
    private final AdminGuard guard;
    private final AdminAuditService audit;

    public AdminConfigController(PkProperties properties, AdminGuard guard,
                                 @Lazy AdminAuditService audit) {
        this.properties = properties;
        this.guard = guard;
        this.audit = audit;
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
        public Integer roundsPerMatch;
        /** Practice (play-vs-bot): daily paid-win cap. */
        public Integer practiceDailyRewardCap;
        /** Patches one practice difficulty tier identified by id. */
        public PracticeDifficultyPatch practiceDifficulty;
    }

    public static class PracticeDifficultyPatch {

        public String id;
        public Integer elo;
        public Double accuracy;
        public Double pace;
        public Integer winCoins;
    }

    @GetMapping("/config")
    public ResponseEntity<?> get(HttpServletRequest request) {
        ResponseEntity<String> denied = this.guard.deny(request);
        if (denied != null) {
            return denied;
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
        view.put("roundsPerMatch", this.properties.getMatch().getRoundsPerMatch());
        Map<String, Object> practice = new LinkedHashMap<>();
        practice.put("dailyRewardCap", this.properties.getPractice().getDailyRewardCap());
        java.util.List<Map<String, Object>> tiers = new java.util.ArrayList<>();
        for (PkProperties.BotDifficulty tier : this.properties.getPractice().getDifficulties()) {
            Map<String, Object> tierView = new LinkedHashMap<>();
            tierView.put("id", tier.getId());
            tierView.put("elo", tier.getElo());
            tierView.put("accuracy", tier.getAccuracy());
            tierView.put("pace", tier.getPace());
            tierView.put("winCoins", tier.getWinCoins());
            tiers.add(tierView);
        }
        practice.put("difficulties", tiers);
        view.put("practice", practice);
        return ResponseEntity.ok(view);
    }

    @PutMapping("/config")
    public ResponseEntity<?> put(HttpServletRequest request, @RequestBody ConfigBody body) {
        ResponseEntity<String> denied = this.guard.deny(request);
        if (denied != null) {
            return denied;
        }
        this.audit.record("CONFIG_PUT", body.toString(), AdminGuard.clientIp(request), "ok");
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
        if (body.roundsPerMatch != null) {
            // Applies to matches created after the change only: round plans
            // are fixed when a MatchRoom is constructed.
            int value = (int) clamp(body.roundsPerMatch, 1L, 15L);
            this.properties.getMatch().setRoundsPerMatch(value);
            audit.append(" roundsPerMatch=").append(value);
        }
        if (body.practiceDailyRewardCap != null) {
            int value = (int) clamp(body.practiceDailyRewardCap, 0L, 100L);
            this.properties.getPractice().setDailyRewardCap(value);
            audit.append(" practiceDailyRewardCap=").append(value);
        }
        if (body.practiceDifficulty != null && body.practiceDifficulty.id != null) {
            PkProperties.BotDifficulty tier =
                    this.properties.practiceDifficulty(body.practiceDifficulty.id);
            boolean matched = tier.getId() != null
                    && tier.getId().equalsIgnoreCase(body.practiceDifficulty.id);
            if (matched) {
                if (body.practiceDifficulty.elo != null) {
                    tier.setElo((int) clamp(body.practiceDifficulty.elo, 0L, 3000L));
                }
                if (body.practiceDifficulty.accuracy != null) {
                    tier.setAccuracy(clamp(body.practiceDifficulty.accuracy, 0.1d, 0.99d));
                }
                if (body.practiceDifficulty.pace != null) {
                    tier.setPace(clamp(body.practiceDifficulty.pace, 0.2d, 5.0d));
                }
                if (body.practiceDifficulty.winCoins != null) {
                    tier.setWinCoins((int) clamp(body.practiceDifficulty.winCoins, 0L, 1000L));
                }
                audit.append(" practiceDifficulty=").append(tier.getId());
            } else {
                audit.append(" practiceDifficulty=unknown:").append(body.practiceDifficulty.id);
            }
        }
        log.info("{}", audit);
        this.audit.record("CONFIG_APPLIED", audit.toString(), AdminGuard.clientIp(request), "ok");
        return get(request);
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
