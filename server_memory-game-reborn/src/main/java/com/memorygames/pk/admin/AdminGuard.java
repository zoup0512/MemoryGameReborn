package com.memorygames.pk.admin;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared admin-plane guard: static token (empty token hides the whole
 * surface with 404) plus a per-IP token bucket. Controllers call
 * {@link #deny(HttpServletRequest)} first and return its response when
 * non-null.
 */
@Component
public class AdminGuard {

    private final String adminToken;
    private final int ratePerMinute;

    /** ip -> {windowStartMs, count}. */
    private final ConcurrentHashMap<String, long[]> buckets = new ConcurrentHashMap<>();

    public AdminGuard(@Value("${pk.admin-token:}") String adminToken,
                      @Value("${pk.admin-rate-limit:60}") int ratePerMinute) {
        this.adminToken = adminToken == null ? "" : adminToken.trim();
        this.ratePerMinute = Math.max(1, ratePerMinute);
    }

    /** @return null when allowed, otherwise the error response to return. */
    public ResponseEntity<String> deny(HttpServletRequest request) {
        if (this.adminToken.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        String provided = request.getHeader("X-Admin-Token");
        if (!this.adminToken.equals(provided)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (!rateOk(clientIp(request))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
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

    public static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        return request.getRemoteAddr();
    }
}
