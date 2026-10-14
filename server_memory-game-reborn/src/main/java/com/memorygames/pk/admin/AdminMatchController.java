package com.memorygames.pk.admin;

import com.memorygames.pk.match.MatchManager;
import com.memorygames.pk.match.MatchRoom;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Live match inspection and forced settlement (admin design 4.2). */
@RestController
@RequestMapping("/api/admin/matches")
public class AdminMatchController {

    public static class SettleBody extends AdminGuard.ReasonBody {

        public Boolean refund;
    }

    private final AdminGuard guard;
    private final AdminAuditService audit;
    private final MatchManager manager;
    private final EventRing events;

    public AdminMatchController(AdminGuard guard, AdminAuditService audit,
                                MatchManager manager, EventRing events) {
        this.guard = guard;
        this.audit = audit;
        this.manager = manager;
        this.events = events;
    }

    @GetMapping
    public ResponseEntity<?> list(HttpServletRequest request) {
        ResponseEntity<String> denied = this.guard.deny(request);
        if (denied != null) {
            return denied;
        }
        List<MatchRoom.Snapshot> active = this.manager.activeSnapshots();
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("active", active.size());
        view.put("matches", active);
        return ResponseEntity.ok(view);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable String id, HttpServletRequest request) {
        ResponseEntity<String> denied = this.guard.deny(request);
        if (denied != null) {
            return denied;
        }
        MatchRoom room = this.manager.roomById(id);
        if (room == null) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("snapshot", room.adminSnapshot());
        view.put("timeline", this.events.recent(200).stream()
                .filter(e -> id.equals(e.matchId))
                .map(EventRing.Event::format)
                .toList());
        return ResponseEntity.ok(view);
    }

    @PostMapping("/{id}/settle")
    public ResponseEntity<?> settle(@PathVariable String id, HttpServletRequest request,
                                    @RequestBody(required = false) SettleBody body) {
        ResponseEntity<String> denied = this.guard.deny(request);
        if (denied != null) {
            return denied;
        }
        ResponseEntity<?> invalid = this.guard.denyReason(body == null ? null : body.reason);
        if (invalid != null) {
            return invalid;
        }
        MatchRoom room = this.manager.roomById(id);
        if (room == null) {
            return ResponseEntity.notFound().build();
        }
        boolean refund = Boolean.TRUE.equals(body.refund);
        boolean applied = room.adminSettle(refund);
        this.audit.record("MATCH_SETTLE", "id=" + id + " refund=" + refund + " reason=" + body.reason,
                this.guard.clientIp(request), applied ? "ok" : "error:already-settled");
        return ResponseEntity.ok(Map.of("applied", applied));
    }

    @PostMapping("/{id}/terminate")
    public ResponseEntity<?> terminate(@PathVariable String id, HttpServletRequest request,
                                      @RequestBody(required = false) AdminGuard.ReasonBody body) {
        ResponseEntity<String> denied = this.guard.deny(request);
        if (denied != null) {
            return denied;
        }
        ResponseEntity<?> invalid = this.guard.denyReason(body == null ? null : body.reason);
        if (invalid != null) {
            return invalid;
        }
        MatchRoom room = this.manager.roomById(id);
        if (room == null) {
            return ResponseEntity.notFound().build();
        }
        boolean applied = room.adminTerminate();
        this.audit.record("MATCH_TERMINATE", "id=" + id + " reason=" + body.reason,
                this.guard.clientIp(request), applied ? "ok" : "error:already-settled");
        return ResponseEntity.ok(Map.of("applied", applied, "voided", true));
    }
}
