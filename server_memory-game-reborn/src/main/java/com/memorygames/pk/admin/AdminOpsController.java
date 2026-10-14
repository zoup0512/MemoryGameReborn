package com.memorygames.pk.admin;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Maintenance mode, runtime log level, event/audit tails (admin design 4.7/4.8). */
@RestController
@RequestMapping("/api/admin")
public class AdminOpsController {

    public static class MaintenanceBody extends AdminGuard.ReasonBody {

        public Boolean enabled;
        public String message;
    }

    public static class LogLevelBody extends AdminGuard.ReasonBody {

        public String logger;
        public String level;
    }

    private final AdminGuard guard;
    private final AdminAuditService audit;
    private final AdminState state;
    private final EventRing events;
    private final AdminMetrics metrics;

    public AdminOpsController(AdminGuard guard, AdminAuditService audit, AdminState state,
                              EventRing events, AdminMetrics metrics) {
        this.guard = guard;
        this.audit = audit;
        this.state = state;
        this.events = events;
        this.metrics = metrics;
    }

    @GetMapping("/events")
    public ResponseEntity<?> events(@RequestParam(name = "n", defaultValue = "50") int n,
                                    HttpServletRequest request) {
        ResponseEntity<String> denied = this.guard.deny(request);
        if (denied != null) {
            return denied;
        }
        List<EventRing.Event> recent = this.events.recent(Math.min(n, 200));
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("count", recent.size());
        view.put("events", recent.stream().map(EventRing.Event::format).toList());
        return ResponseEntity.ok(view);
    }

    @GetMapping("/audit")
    public ResponseEntity<?> audit(@RequestParam(name = "n", defaultValue = "50") int n,
                                   HttpServletRequest request) {
        ResponseEntity<String> denied = this.guard.deny(request);
        if (denied != null) {
            return denied;
        }
        return ResponseEntity.ok(Map.of("rows", this.audit.recent(Math.min(n, 200)).stream()
                .map(row -> String.format("%s %s %s [%s] %s",
                        new java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.US)
                                .format(new java.util.Date(row.ts)),
                        row.action, row.paramsJson, row.operatorIp, row.result))
                .toList()));
    }

    @GetMapping("/maintenance")
    public ResponseEntity<?> maintenance(HttpServletRequest request) {
        ResponseEntity<String> denied = this.guard.deny(request);
        if (denied != null) {
            return denied;
        }
        return ResponseEntity.ok(Map.of("enabled", this.state.isMaintenance(),
                "message", this.state.getMaintenanceMessage()));
    }

    @GetMapping("/loglevel")
    public ResponseEntity<?> loglevel(HttpServletRequest request,
                                     @RequestParam(name = "logger", required = false) String logger) {
        ResponseEntity<String> denied = this.guard.deny(request);
        if (denied != null) {
            return denied;
        }
        if (logger == null) {
            logger = org.slf4j.Logger.ROOT_LOGGER_NAME;
        }
        if (logger.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "logger is required"));
        }
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext context)) {
            return ResponseEntity.status(501).build();
        }
        // Unlike getLogger(), exists() never allocates a logger on a read request.
        ch.qos.logback.classic.Logger target = context.exists(logger.trim());
        if (target == null) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("logger", target.getName());
        view.put("configuredLevel", target.getLevel() == null ? null : target.getLevel().toString());
        view.put("effectiveLevel", target.getEffectiveLevel().toString());
        return ResponseEntity.ok(view);
    }

    @PostMapping("/maintenance")
    public ResponseEntity<?> maintenance(HttpServletRequest request,
                                         @RequestBody(required = false) MaintenanceBody body) {
        ResponseEntity<String> denied = this.guard.deny(request);
        if (denied != null) {
            return denied;
        }
        ResponseEntity<?> invalid = this.guard.denyReason(body == null ? null : body.reason);
        if (invalid != null) {
            return invalid;
        }
        if (body.enabled == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "enabled is required"));
        }
        boolean enabled = body.enabled;
        this.state.setMaintenance(enabled, body.message);
        this.metrics.adminOps.incrementAndGet();
        this.audit.record(enabled ? "MAINTENANCE_ON" : "MAINTENANCE_OFF",
                "message=" + this.state.getMaintenanceMessage() + " reason=" + body.reason,
                this.guard.clientIp(request), "ok");
        return ResponseEntity.ok(Map.of(
                "maintenance", enabled,
                "message", this.state.getMaintenanceMessage()));
    }

    @PostMapping("/loglevel")
    public ResponseEntity<?> loglevel(HttpServletRequest request,
                                     @RequestBody(required = false) LogLevelBody body) {
        ResponseEntity<String> denied = this.guard.deny(request);
        if (denied != null) {
            return denied;
        }
        ResponseEntity<?> invalid = this.guard.denyReason(body == null ? null : body.reason);
        if (invalid != null) {
            return invalid;
        }
        if (body.logger == null || body.logger.isBlank() || body.level == null || body.level.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "logger and level are required"));
        }
        Level level = Level.toLevel(body.level.trim().toUpperCase(Locale.ROOT), null);
        if (level == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "unknown level"));
        }
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext context)) {
            return ResponseEntity.status(501).build();
        }
        String logger = body.logger.trim();
        context.getLogger(logger).setLevel(level);
        this.metrics.adminOps.incrementAndGet();
        this.audit.record("LOG_LEVEL", "logger=" + logger + " level=" + level + " reason=" + body.reason,
                this.guard.clientIp(request), "ok");
        return ResponseEntity.ok(Map.of("logger", logger, "level", level.toString()));
    }
}
