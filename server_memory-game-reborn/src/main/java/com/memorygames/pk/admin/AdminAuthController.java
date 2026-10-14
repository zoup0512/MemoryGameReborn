package com.memorygames.pk.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {
    private final AdminSessionService sessions;
    private final ObjectMapper json;

    public AdminAuthController(AdminSessionService sessions, ObjectMapper json) {
        this.sessions = sessions;
        this.json = json;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(HttpServletRequest request) throws IOException {
        ResponseEntity<?> denied = preflight(request);
        if (denied != null) return denied;
        if (request.getContentLengthLong() > 1024 || request.getContentType() == null
                || !MediaType.APPLICATION_JSON.isCompatibleWith(parseType(request.getContentType()))) return error(400);
        // Bound actual bytes too, including chunked bodies, before parsing or invoking bcrypt.
        byte[] bytes = request.getInputStream().readNBytes(1025);
        if (bytes.length > 1024) return error(400);
        String password;
        try {
            JsonNode body = json.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(bytes);
            if (body == null || !body.isObject() || !body.path("password").isTextual()) return error(400);
            password = body.get("password").textValue();
        } catch (IOException exception) { return error(400); }
        if (!AdminSessionService.validPassword(password)) return error(400);
        if (!sessions.acquireLogin(sessions.clientIp(request))) return error(429);
        if (!sessions.enabled()) return error(503);
        if (!sessions.matchesPassword(password)) return error(401);
        AdminSessionService.IssuedSession issued = sessions.issue(request);
        if (issued == null) return error(503);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.SET_COOKIE, sessions.cookie(request, issued.id(), false))
                .body(view(issued.session()));
    }

    @GetMapping("/session")
    public ResponseEntity<?> session(HttpServletRequest request) {
        ResponseEntity<?> denied = preflight(request);
        if (denied != null) return denied;
        AdminSessionService.Session session = sessions.find(request);
        return session == null ? error(401) : ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(view(session));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        ResponseEntity<?> denied = preflight(request);
        if (denied != null) return denied;
        AdminSessionService.Session session = sessions.find(request);
        if (session != null && !sessions.csrfOk(request, session)) return error(403);
        sessions.revoke(request);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.SET_COOKIE, sessions.cookie(request, "", true))
                .body(Map.of("authenticated", false));
    }

    private ResponseEntity<?> preflight(HttpServletRequest request) {
        return !sessions.transportAllowed(request) || !sessions.sameOrigin(request) ? error(403) : null;
    }
    private static MediaType parseType(String type) {
        try { return MediaType.parseMediaType(type); }
        catch (IllegalArgumentException exception) { return MediaType.APPLICATION_OCTET_STREAM; }
    }
    private static Map<String, Object> view(AdminSessionService.Session session) {
        return Map.of("authenticated", true, "csrfToken", session.csrfToken(), "expiresAt", session.expiresAt().toString());
    }
    private static ResponseEntity<?> error(int status) {
        return ResponseEntity.status(status).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Map.of("error", status == 401 ? "authentication failed" : status == 503 ? "admin login unavailable"
                        : status == 429 ? "too many login attempts" : status == 403 ? "request forbidden" : "invalid input"));
    }
}
