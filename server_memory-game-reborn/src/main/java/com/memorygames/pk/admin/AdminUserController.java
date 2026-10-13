package com.memorygames.pk.admin;

import com.memorygames.pk.match.MatchRecord;
import com.memorygames.pk.match.MatchRecordRepository;
import com.memorygames.pk.user.UserAccount;
import com.memorygames.pk.user.UserRepository;
import com.memorygames.pk.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** User search and moderation (admin design 4.3). */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    public static class BalanceBody {

        public Integer delta;
        public String reason;
    }

    public static class EloBody {

        public Integer set;
        public String reason;
    }

    public static class RestrictBody {

        public Integer matches;
    }

    public static class BanBody {

        public Integer hours;
        public String reason;
    }

    private final AdminGuard guard;
    private final AdminAuditService audit;
    private final UserRepository users;
    private final UserService service;
    private final MatchRecordRepository records;

    public AdminUserController(AdminGuard guard, AdminAuditService audit, UserRepository users,
                               UserService service, MatchRecordRepository records) {
        this.guard = guard;
        this.audit = audit;
        this.users = users;
        this.service = service;
        this.records = records;
    }

    @GetMapping
    public ResponseEntity<?> search(@RequestParam(name = "q", defaultValue = "") String q,
                                    HttpServletRequest request) {
        ResponseEntity<String> denied = this.guard.deny(request);
        if (denied != null) {
            return denied;
        }
        List<UserAccount> found = q.isBlank()
                ? this.users.findAll(PageRequest.of(0, 20)).getContent()
                : this.users.search(q.trim());
        return ResponseEntity.ok(Map.of("users", found.stream().limit(20).map(this::view).toList()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable long id, HttpServletRequest request) {
        ResponseEntity<String> denied = this.guard.deny(request);
        if (denied != null) {
            return denied;
        }
        UserAccount account = this.users.findById(id).orElse(null);
        if (account == null) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("user", view(account));
        List<MatchRecord> recent = this.records.findRecentForUser(id, PageRequest.of(0, 20));
        view.put("recentMatches", recent.stream().map(r -> String.format(
                "%s %s %d:%d vs %s", new java.text.SimpleDateFormat("MM-dd HH:mm",
                        java.util.Locale.US).format(new java.util.Date(r.settledAt)),
                r.mode, r.playerA == id ? r.scoreA : r.scoreB,
                r.playerA == id ? r.scoreB : r.scoreA,
                r.playerA == id ? r.nicknameB : r.nicknameA)).toList());
        return ResponseEntity.ok(view);
    }

    @PostMapping("/{id}/balance")
    public ResponseEntity<?> balance(@PathVariable long id, HttpServletRequest request,
                                     @RequestBody BalanceBody body) {
        ResponseEntity<String> denied = this.guard.deny(request);
        if (denied != null) {
            return denied;
        }
        UserAccount account = this.users.findById(id).orElse(null);
        if (account == null || body.delta == null) {
            return ResponseEntity.badRequest().build();
        }
        account = this.service.adjustBalance(account, body.delta,
                body.reason != null && !body.reason.isBlank() ? body.reason : "unspecified");
        this.audit.record("USER_BALANCE",
                "id=" + id + " delta=" + body.delta + " reason=" + body.reason,
                this.guard.clientIp(request), "ok");
        return ResponseEntity.ok(view(account));
    }

    @PostMapping("/{id}/elo")
    public ResponseEntity<?> elo(@PathVariable long id, HttpServletRequest request,
                                 @RequestBody EloBody body) {
        ResponseEntity<String> denied = this.guard.deny(request);
        if (denied != null) {
            return denied;
        }
        UserAccount account = this.users.findById(id).orElse(null);
        if (account == null || body.set == null) {
            return ResponseEntity.badRequest().build();
        }
        account = this.service.setElo(account, body.set);
        this.audit.record("USER_ELO", "id=" + id + " set=" + body.set + " reason=" + body.reason,
                this.guard.clientIp(request), "ok");
        return ResponseEntity.ok(view(account));
    }

    @PostMapping("/{id}/restrict")
    public ResponseEntity<?> restrict(@PathVariable long id, HttpServletRequest request,
                                      @RequestBody RestrictBody body) {
        ResponseEntity<String> denied = this.guard.deny(request);
        if (denied != null) {
            return denied;
        }
        UserAccount account = this.users.findById(id).orElse(null);
        if (account == null || body.matches == null) {
            return ResponseEntity.badRequest().build();
        }
        account = this.service.setRestriction(account, Math.min(body.matches, 100));
        this.audit.record("USER_RESTRICT", "id=" + id + " matches=" + body.matches,
                this.guard.clientIp(request), "ok");
        return ResponseEntity.ok(view(account));
    }

    @PostMapping("/{id}/ban")
    public ResponseEntity<?> ban(@PathVariable long id, HttpServletRequest request,
                                 @RequestBody BanBody body) {
        ResponseEntity<String> denied = this.guard.deny(request);
        if (denied != null) {
            return denied;
        }
        UserAccount account = this.users.findById(id).orElse(null);
        if (account == null || body.hours == null) {
            return ResponseEntity.badRequest().build();
        }
        account = this.service.setBan(account, Math.min(body.hours, 24 * 365));
        this.audit.record("USER_BAN", "id=" + id + " hours=" + body.hours + " reason=" + body.reason,
                this.guard.clientIp(request), "ok");
        return ResponseEntity.ok(view(account));
    }

    @PostMapping("/{id}/unban")
    public ResponseEntity<?> unban(@PathVariable long id, HttpServletRequest request) {
        ResponseEntity<String> denied = this.guard.deny(request);
        if (denied != null) {
            return denied;
        }
        UserAccount account = this.users.findById(id).orElse(null);
        if (account == null) {
            return ResponseEntity.notFound().build();
        }
        account = this.service.setBan(account, 0);
        this.audit.record("USER_UNBAN", "id=" + id, this.guard.clientIp(request), "ok");
        return ResponseEntity.ok(view(account));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable long id, HttpServletRequest request) {
        ResponseEntity<String> denied = this.guard.deny(request);
        if (denied != null) {
            return denied;
        }
        UserAccount account = this.users.findById(id).orElse(null);
        if (account == null) {
            return ResponseEntity.notFound().build();
        }
        account = this.service.markDeleted(account);
        this.audit.record("USER_DELETE", "id=" + id, this.guard.clientIp(request), "ok");
        return ResponseEntity.ok(view(account));
    }

    private Map<String, Object> view(UserAccount account) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", account.id);
        view.put("nickname", account.nickname);
        view.put("elo", account.elo);
        view.put("wins", account.wins);
        view.put("losses", account.losses);
        view.put("draws", account.draws);
        view.put("balance", account.balance);
        view.put("suspicion", account.suspicion);
        view.put("restrictedMatches", account.restrictedMatches);
        view.put("bannedUntil", account.bannedUntil);
        view.put("deleted", account.deleted);
        return view;
    }
}
