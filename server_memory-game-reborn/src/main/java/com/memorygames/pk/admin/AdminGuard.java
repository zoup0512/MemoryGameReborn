package com.memorygames.pk.admin;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared admin-plane guard: automation token or browser session,
 * plus a per-IP token bucket. Controllers call
 * {@link #deny(HttpServletRequest)} first and return its response when
 * non-null.
 */
@Component
public class AdminGuard {

    private final String adminToken;
    private final int ratePerMinute;
    private final AdminSessionService sessions;
    /**
     * X-Forwarded-For is only honoured when the service sits behind a
     * trusted proxy (P2-5): a directly exposed instance would otherwise let
     * callers forge the limiter key and pollute audit IPs.
     */
    private final boolean trustForwardedHeaders;

    /** ip -> {windowStartMs, count}. */
    private final ConcurrentHashMap<String, long[]> buckets = new ConcurrentHashMap<>();

    public AdminGuard(@Value("${pk.admin-token:}") String adminToken,
                      @Value("${pk.admin-rate-limit:60}") int ratePerMinute,
                      @Value("${pk.admin.trust-forwarded-headers:false}") boolean trustForwardedHeaders,
                      AdminSessionService sessions) {
        this.sessions = sessions;
        this.adminToken = adminToken == null ? "" : adminToken.trim();
        this.ratePerMinute = Math.max(1, ratePerMinute);
        this.trustForwardedHeaders = trustForwardedHeaders;
    }

    /** @return null when allowed, otherwise the error response to return. */
    public ResponseEntity<String> deny(HttpServletRequest request) {
        String provided = request.getHeader("X-Admin-Token");
        if (provided != null) {
            // Explicit automation credentials always win; wrong/empty headers never fall back to cookies.
            if (this.adminToken.isEmpty() || !AdminSessionService.constantEquals(this.adminToken, provided)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
        } else {
            AdminSessionService.Session session = sessions.find(request);
            if (session == null) {
                return ResponseEntity.status(sessions.hasCookie(request) ? HttpStatus.UNAUTHORIZED : HttpStatus.NOT_FOUND).build();
            }
            if (!sessions.transportAllowed(request) || !sessions.sameOrigin(request)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            String method = request.getMethod();
            if (!method.equals("GET") && !method.equals("HEAD") && !method.equals("OPTIONS")
                    && !sessions.csrfOk(request, session)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }
        if (!rateOk(clientIp(request))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        return null;
    }

    /** Shared payload for mutations that only need an operator reason. */
    public static class ReasonBody {
        public String reason;
    }

    /** Validate before looking up targets, changing state or writing audit rows. */
    public ResponseEntity<?> denyReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "reason is required"));
        }
        if (reason.length() > 500) {
            return ResponseEntity.badRequest().body(Map.of("error", "reason must be at most 500 characters"));
        }
        return null;
    }

    private boolean rateOk(String ip) {
        long now = System.currentTimeMillis();
        long[] bucket = this.buckets.compute(ip, (k, v) -> {
            if (v == null || now - v[0] >= 60000L) {
                return new long[]{now, 1};
            }
            v[1]++;
            return v;
        });
        if (this.buckets.size() > 10000) {
            this.buckets.clear(); // defensive; admin surface is small
        }
        return bucket[1] <= this.ratePerMinute;
    }

    public String clientIp(HttpServletRequest request) {
        if (this.trustForwardedHeaders) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                int comma = forwarded.indexOf(',');
                return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
            }
        }
        return request.getRemoteAddr();
    }
}
