package com.memorygames.pk.admin;

import com.memorygames.pk.user.NicknameMaintenance;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/nicknames")
public class AdminNicknameController {
    public record Reset(String expectedDigest,String reason) {}
    private final AdminGuard guard;
    private final AdminAuditService audit;
    private final NicknameMaintenance maintenance;
    public AdminNicknameController(AdminGuard guard,AdminAuditService audit,NicknameMaintenance maintenance) {
        this.guard=guard; this.audit=audit; this.maintenance=maintenance;
    }
    @PostMapping("/scan") public ResponseEntity<?> scan(@RequestParam(defaultValue="0") long after,
            @RequestParam(defaultValue="10") int limit,HttpServletRequest request) {
        ResponseEntity<String> denied=guard.deny(request); if(denied!=null) return denied;
        var result=maintenance.scan(after,limit);
        audit.record("NICKNAME_SCAN","after="+after+" count="+result.size(),guard.clientIp(request),"ok");
        return ResponseEntity.ok(Map.of("users",result));
    }
    @PostMapping("/{id}/safe-reset") public ResponseEntity<?> reset(@PathVariable long id,@RequestBody Reset body,HttpServletRequest request) {
        ResponseEntity<String> denied=guard.deny(request); if(denied!=null) return denied;
        ResponseEntity<?> invalid=guard.denyReason(body.reason()); if(invalid!=null) return invalid;
        maintenance.replace(id,body.expectedDigest());
        // Do not persist free text here: an operator might paste the offensive nickname.
        audit.record("NICKNAME_SAFE_RESET","id="+id,guard.clientIp(request),"ok");
        return ResponseEntity.ok(Map.of("ok",true));
    }
}
