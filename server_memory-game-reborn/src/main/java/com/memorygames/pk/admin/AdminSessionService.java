package com.memorygames.pk.admin;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/** Single administrator, fixed configured bcrypt credential, process-local sessions. */
@Component
public class AdminSessionService {
    public static final String COOKIE_NAME = "PK_ADMIN_SESSION";
    public static final String CSRF_HEADER = "X-Admin-CSRF";
    private final String passwordHash;
    private final boolean enabled;
    private final long ttlSeconds;
    private final int maxSessions;
    private final int loginRateLimit;
    private final boolean trustForwardedHeaders;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    // Never retain bearer session IDs. The map keys are SHA-256 digests.
    private final Map<String, Session> sessions = new HashMap<>();
    private final Map<String, LoginBucket> loginBuckets = new HashMap<>();
    public record Session(String csrfToken, Instant expiresAt) {}
    public record IssuedSession(String id, Session session) {}
    private record LoginBucket(long start, int count) {}

    @org.springframework.beans.factory.annotation.Autowired
    public AdminSessionService(@Value("${pk.admin-password-hash:}") String passwordHash,
                               @Value("${pk.admin.session-ttl-seconds:28800}") long ttlSeconds,
                               @Value("${pk.admin.max-sessions:1000}") int maxSessions,
                               @Value("${pk.admin.login-rate-limit:5}") int loginRateLimit,
                               @Value("${pk.admin.trust-forwarded-headers:false}") boolean trustForwardedHeaders) {
        this(passwordHash, ttlSeconds, maxSessions, loginRateLimit, trustForwardedHeaders, Clock.systemUTC());
    }

    AdminSessionService(String hash, long ttl, int max, int rate, boolean trust, Clock clock) {
        this.passwordHash = hash == null ? "" : hash;
        this.enabled = this.passwordHash.matches("\\$2[aby]\\$(0[4-9]|[12][0-9]|3[01])\\$[./A-Za-z0-9]{53}");
        this.ttlSeconds = Math.max(1, Math.min(ttl, 86400));
        this.maxSessions = Math.max(1, Math.min(max, 10000));
        this.loginRateLimit = Math.max(1, Math.min(rate, 100));
        this.trustForwardedHeaders = trust;
        this.clock = clock;
    }

    public boolean enabled() { return enabled; }

    public static boolean validPassword(String password) {
        return password != null && !password.isEmpty() && password.length() <= 72
                && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    public boolean matchesPassword(String password) {
        if (!enabled || !validPassword(password)) return false;
        try {
            return encoder.matches(password, passwordHash);
        } catch (IllegalArgumentException exception) {
            return false; // Invalid configuration/input must never become a 500 or be logged.
        }
    }

    public synchronized boolean acquireLogin(String ip) {
        long now = clock.millis();
        loginBuckets.entrySet().removeIf(e -> now - e.getValue().start >= 60000);
        if (!loginBuckets.containsKey(ip) && loginBuckets.size() >= 10000) return false;
        LoginBucket old = loginBuckets.get(ip);
        int count = old == null ? 1 : Math.min(old.count + 1, loginRateLimit + 1);
        loginBuckets.put(ip, new LoginBucket(old == null ? now : old.start, count));
        return count <= loginRateLimit;
    }

    public synchronized IssuedSession issue(HttpServletRequest request) {
        cleanup();
        // A successful re-login rotates and revokes the existing browser session.
        revoke(request);
        if (sessions.size() >= maxSessions) return null;
        String id = randomToken();
        Session session = new Session(randomToken(), clock.instant().plusSeconds(ttlSeconds));
        sessions.put(digest(id), session);
        return new IssuedSession(id, session);
    }

    public synchronized Session find(HttpServletRequest request) {
        cleanup();
        String id = cookieId(request);
        return id == null ? null : sessions.get(digest(id));
    }

    public synchronized void revoke(HttpServletRequest request) {
        String id = cookieId(request);
        if (id != null) sessions.remove(digest(id));
    }

    private void cleanup() {
        Instant now = clock.instant();
        sessions.entrySet().removeIf(e -> !e.getValue().expiresAt.isAfter(now));
    }

    public boolean hasCookie(HttpServletRequest request) { return cookieId(request) != null; }

    private String cookieId(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        String id = null;
        for (Cookie cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                if (id != null) return null; // Ambiguous cookies are never authenticated.
                id = cookie.getValue();
            }
        }
        return id != null && id.matches("[A-Za-z0-9_-]{43}") ? id : null;
    }

    public boolean csrfOk(HttpServletRequest request, Session session) {
        String supplied = request.getHeader(CSRF_HEADER);
        return supplied != null && supplied.length() == 43 && constantEquals(session.csrfToken, supplied);
    }

    public String cookie(HttpServletRequest request, String id, boolean expire) {
        return ResponseCookie.from(COOKIE_NAME, expire ? "" : id)
                .httpOnly(true).secure(isHttps(request)).sameSite("Strict").path("/api/admin")
                .maxAge(expire ? 0 : ttlSeconds).build().toString();
    }

    public String clientIp(HttpServletRequest request) {
        if (trustForwardedHeaders) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                int comma = forwarded.indexOf(',');
                return comma >= 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
            }
        }
        return request.getRemoteAddr();
    }

    public boolean isHttps(HttpServletRequest request) {
        // Spring's automatic forwarded-header support is explicitly disabled in application.yml.
        return request.isSecure() || (trustForwardedHeaders && "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto")));
    }

    public boolean transportAllowed(HttpServletRequest request) {
        if (isHttps(request)) return true;
        // Both peer and authority must be local: a local reverse proxy is not proof of a local client.
        // Forwarded traffic may never use the loopback exception.
        return request.getHeader("X-Forwarded-For") == null && request.getHeader("X-Forwarded-Proto") == null
                && loopback(request.getRemoteAddr()) && loopback(request.getServerName());
    }

    private static boolean loopback(String host) {
        if (host == null) return false;
        String normalized = host.toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("localhost") || normalized.equals("127.0.0.1")
                || normalized.equals("::1") || normalized.equals("[::1]")
                || normalized.equals("0:0:0:0:0:0:0:1");
    }

    /** Origin is an exact scheme/host/port match; sibling subdomains are not trusted. */
    public boolean sameOrigin(HttpServletRequest request) {
        String site = request.getHeader("Sec-Fetch-Site");
        if (site != null && !site.equals("same-origin")) return false;
        String origin = request.getHeader("Origin");
        if (origin == null) {
            // CLI (no browser metadata) or same-origin browser GET. Cookie mutations still need CSRF.
            return site != null || (request.getHeader("Sec-Fetch-Mode") == null
                    && request.getHeader("Sec-Fetch-Dest") == null && request.getHeader("Sec-Fetch-User") == null);
        }
        if (java.util.Collections.list(request.getHeaders("Origin")).size() != 1) return false;
        try {
            URI supplied = URI.create(origin);
            String scheme = isHttps(request) ? "https" : "http";
            String host = request.getServerName();
            int port = request.getServerPort();
            // Trust the original Host authority, never a freely supplied X-Forwarded-Host.
            String authority = request.getHeader("Host");
            if (authority != null) {
                URI target = URI.create(scheme + "://" + authority);
                host = target.getHost();
                port = target.getPort() == -1 ? defaultPort(scheme) : target.getPort();
                if (target.getRawUserInfo() != null || target.getRawPath() == null || !target.getRawPath().isEmpty()) return false;
            }
            return supplied.getRawUserInfo() == null && supplied.getRawQuery() == null && supplied.getRawFragment() == null
                    && supplied.getRawPath() != null && supplied.getRawPath().isEmpty()
                    && scheme.equals(supplied.getScheme()) && host != null && host.equalsIgnoreCase(supplied.getHost())
                    && port == (supplied.getPort() == -1 ? defaultPort(scheme) : supplied.getPort());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static int defaultPort(String scheme) { return "https".equals(scheme) ? 443 : 80; }
    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private static String digest(String value) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    public static boolean constantEquals(String expected, String supplied) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }
}
