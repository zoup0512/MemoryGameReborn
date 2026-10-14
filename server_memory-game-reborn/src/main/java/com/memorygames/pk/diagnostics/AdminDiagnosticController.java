package com.memorygames.pk.diagnostics;

import com.memorygames.pk.admin.AdminGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/diagnostics")
public class AdminDiagnosticController {
    private final AdminGuard guard;
    private final DiagnosticService diagnostics;
    public AdminDiagnosticController(AdminGuard guard,DiagnosticService diagnostics) { this.guard=guard; this.diagnostics=diagnostics; }
    @GetMapping public ResponseEntity<?> list(@RequestParam(required=false) Long userId,@RequestParam(required=false) String matchId,
                                             @RequestParam(defaultValue="20") int limit,HttpServletRequest request,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        ResponseEntity<String> denied=guard.deny(request); if(denied!=null) return denied;
        return ResponseEntity.ok(Map.of("reports",diagnostics.list(userId,matchId,limit)));
    }
    @GetMapping("/{id}") public ResponseEntity<?> detail(@PathVariable String id,HttpServletRequest request,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        ResponseEntity<String> denied=guard.deny(request); if(denied!=null) return denied;
        return ResponseEntity.ok(diagnostics.detail(id));
    }
}
