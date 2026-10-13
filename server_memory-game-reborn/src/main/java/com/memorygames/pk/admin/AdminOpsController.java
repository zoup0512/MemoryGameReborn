package com.memorygames.pk.admin;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.memorygames.pk.match.MatchManager;
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
import java.util.Map;

/** Maintenance mode, runtime log level, event/audit tails (admin design 4.7/4.8). */
@RestController
@RequestMapping("/api/admin")
public class AdminOpsController {

    public static class MaintenanceBody {

        public Boolean enabled;
        public String message;
    }

    public static class LogLevelBody {

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

    @PostMapping("/maintenance")
    public ResponseEntity<?> maintenance(HttpServletRequest request,
                                         @RequestBody MaintenanceBody body) {
        ResponseEntity<String> denied = this.guard.deny(request);
        if (denied != null) {
            return denied;
        }
        boolean enabled = body != null && Boolean.TRUE.equals(body.enabled);
        this.state.setMaintenance(enabled, body == null ? "" : body.message);
        this.metrics.adminOps.incrementAndGet();
        this.audit.record(enabled ? "MAINTENANCE_ON" : "MAINTENANCE_OFF",
                "message=" + (body == null ? "" : body.message),
                this.guard.clientIp(request), "ok");
        return ResponseEntity.ok(Map.of(
                "maintenance", enabled,
                "message", this.state.getMaintenanceMessage()));
    }

    @PostMapping("/loglevel")
    public ResponseEntity<?> loglevel(HttpServletRequest request, @RequestBody LogLevelBody body) {
        ResponseEntity<String> denied = this.guard.deny(request);
        if (denied != null) {
            return denied;
        }
        if (body == null || body.logger == null || body.level == null) {
            return ResponseEntity.badRequest().build();
        }
        Level level = Level.toLevel(body.level.toUpperCase(), null);
        if (level == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "unknown level"));
        }
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext context)) {
            return ResponseEntity.status(501).build();
        }
        context.getLogger(body.logger).setLevel(level);
        this.metrics.adminOps.incrementAndGet();
        this.audit.record("LOG_LEVEL", "logger=" + body.logger + " level=" + level,
                this.guard.clientIp(request), "ok");
        return ResponseEntity.ok(Map.of("logger", body.logger, "level", level.toString()));
    }
}
