package com.memorygames.pk.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminAuthTest {
    private static final String PASSWORD = "fixture-admin-password-only";
    // Test-only cheap bcrypt credential; never copied to production configuration.
    private static final String HASH = new BCryptPasswordEncoder(4).encode(PASSWORD);
    private final ObjectMapper json = new ObjectMapper();
    private MutableClock clock;
    private AdminSessionService sessions;
    private MockMvc mvc;

    @BeforeEach void setUp() {
        clock = new MutableClock();
        sessions = new AdminSessionService(HASH, 28800, 10, 5, false, clock);
        mvc = mvc(sessions, 500);
    }

    private MockMvc mvc(AdminSessionService service, int adminRate) {
        AdminGuard guard = new AdminGuard("automation-token", adminRate, false, service);
        return MockMvcBuilders.standaloneSetup(new AdminAuthController(service, json), new ProtectedController(guard)).build();
    }
    private MockHttpServletRequestBuilder login(String password) throws Exception {
        return post("/api/admin/auth/login").contentType("application/json")
                .header("Origin", "http://localhost").header("Sec-Fetch-Site", "same-origin")
                .content(json.writeValueAsBytes(Map.of("password", password)));
    }
    private record Login(Cookie cookie, String csrf, String expiry) {}
    private Login loggedIn() throws Exception {
        MvcResult result = mvc.perform(login(PASSWORD)).andExpect(status().isOk()).andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsByteArray());
        String header = result.getResponse().getHeader("Set-Cookie");
        String id = header.substring(header.indexOf('=') + 1, header.indexOf(';'));
        return new Login(new Cookie(AdminSessionService.COOKIE_NAME, id), body.get("csrfToken").textValue(), body.get("expiresAt").textValue());
    }

    @Test void loginCookieAndReloadContractAndNoSensitiveValues() throws Exception {
        MvcResult result = mvc.perform(login(PASSWORD)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn();
        String cookie = result.getResponse().getHeader("Set-Cookie");
        assertTrue(cookie.contains("HttpOnly") && cookie.contains("SameSite=Strict") && cookie.contains("Path=/api/admin"));
        assertTrue(cookie.contains("Max-Age=28800"));
        assertFalse(cookie.contains("Domain=") || cookie.contains("Secure"));
        String response = result.getResponse().getContentAsString();
        assertFalse(response.contains(PASSWORD) || response.contains(HASH) || response.contains("automation-token") || response.contains("password"));
        Login login = loggedIn();
        assertEquals(43, login.cookie.getValue().length());
        mvc.perform(get("/api/admin/auth/session").cookie(login.cookie))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.csrfToken").value(login.csrf))
                .andExpect(jsonPath("$.expiresAt").value(login.expiry));
        mvc.perform(get("/api/admin/config").cookie(login.cookie)).andExpect(status().isOk());
    }

    @Test void wrongPasswordsCountTowardIndependentIpRateLimit() throws Exception {
        for (int i = 0; i < 5; i++) mvc.perform(login("incorrect")).andExpect(status().isUnauthorized());
        mvc.perform(login(PASSWORD)).andExpect(status().isTooManyRequests());
        mvc.perform(login(PASSWORD).with(r -> { r.setRemoteAddr("127.0.0.2"); r.setSecure(true); return r; }))
                .andExpect(status().isForbidden()); // wrong Origin, before password/rate work
        mvc.perform(login(PASSWORD).with(r -> { r.setRemoteAddr("0:0:0:0:0:0:0:1"); return r; }))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/config").header("X-Admin-Token", "automation-token")).andExpect(status().isOk());
        clock.advanceSeconds(60);
        mvc.perform(login(PASSWORD)).andExpect(status().isOk());
    }

    @Test void emptyAndInvalidHashDisableLoginWithoutDisablingAutomation() throws Exception {
        for (String hash : new String[]{"", "plain-text-password", "{bcrypt}" + HASH, "$2a$broken"}) {
            mvc = mvc(new AdminSessionService(hash, 28800, 10, 5, false, clock), 500);
            mvc.perform(login(PASSWORD)).andExpect(status().isServiceUnavailable());
            mvc.perform(get("/api/admin/auth/session")).andExpect(status().isUnauthorized())
                    .andExpect(header().string("Cache-Control", "no-store"));
            mvc.perform(get("/api/admin/config").header("X-Admin-Token", "automation-token")).andExpect(status().isOk());
        }
    }

    @Test void invalidBodiesAndUtf8LengthAreRejectedBeforeBcryptOrRateLimiting() throws Exception {
        String[] invalid = {"", "{}", "null", "[]", "{broken", "{\"password\":1}", "{\"password\":null}",
                "{\"password\":\"\"}", "{\"password\":\"ok\"} {}"};
        for (String body : invalid) mvc.perform(post("/api/admin/auth/login").contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        mvc.perform(login("a".repeat(73))).andExpect(status().isBadRequest());
        mvc.perform(login("审".repeat(25))).andExpect(status().isBadRequest());
        mvc.perform(login("a".repeat(1100))).andExpect(status().isBadRequest());
        // Test bounded read when Content-Length is absent (chunked-like request).
        MockHttpServletRequest chunked = new MockHttpServletRequest() {
            @Override public long getContentLengthLong() { return -1; }
        };
        chunked.setMethod("POST");
        chunked.setContentType("application/json");
        chunked.setContent(json.writeValueAsBytes(Map.of("password", "a".repeat(1100))));
        assertEquals(400, new AdminAuthController(sessions, json).login(chunked).getStatusCode().value());
        mvc.perform(post("/api/admin/auth/login").contentType("text/plain").content(PASSWORD)).andExpect(status().isBadRequest());
        mvc.perform(login(PASSWORD)).andExpect(status().isOk());
        assertTrue(AdminSessionService.validPassword("a".repeat(72)));
        assertTrue(AdminSessionService.validPassword("审".repeat(24)));
    }

    @Test void automationTokenWinsAndNeverRequiresCsrfOrChangesItsHttpPolicy() throws Exception {
        Login login = loggedIn();
        mvc.perform(post("/api/admin/config").header("X-Admin-Token", "automation-token")
                        .header("Origin", "https://evil.example").with(r -> { r.setRemoteAddr("203.0.113.1"); return r; }))
                .andExpect(status().isOk());
        for (String wrong : new String[]{"wrong", ""}) mvc.perform(get("/api/admin/config")
                .cookie(login.cookie).header("X-Admin-Token", wrong)).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/config")).andExpect(status().isNotFound());
        mvc = MockMvcBuilders.standaloneSetup(new ProtectedController(new AdminGuard("", 500, false, sessions))).build();
        mvc.perform(get("/api/admin/config").cookie(login.cookie)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/config").cookie(login.cookie).header("X-Admin-Token", "automation-token"))
                .andExpect(status().isNotFound());
    }

    @Test void everyCookieMutationRequiresCsrfAndExactOrigin() throws Exception {
        Login login = loggedIn();
        for (MockHttpServletRequestBuilder method : new MockHttpServletRequestBuilder[]{post("/api/admin/config"), put("/api/admin/config"),
                patch("/api/admin/config"), delete("/api/admin/config")}) {
            mvc.perform(method.cookie(login.cookie)).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/admin/config").cookie(login.cookie).header(AdminSessionService.CSRF_HEADER, "x".repeat(43)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/config").cookie(login.cookie).header(AdminSessionService.CSRF_HEADER, login.csrf)
                .header("Origin", "http://localhost").header("Sec-Fetch-Site", "same-origin")).andExpect(status().isOk());
        for (String origin : new String[]{"http://sibling.localhost", "http://localhost:81", "https://localhost", "null", "http://localhost/", "http://localhost.evil"}) {
            mvc.perform(post("/api/admin/config").cookie(login.cookie).header(AdminSessionService.CSRF_HEADER, login.csrf)
                    .header("Origin", origin)).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/admin/config").cookie(login.cookie).header(AdminSessionService.CSRF_HEADER, login.csrf)
                .header("Sec-Fetch-Site", "same-site")).andExpect(status().isForbidden());
        // CLI with no browser headers still needs the secret session-bound CSRF token.
        mvc.perform(post("/api/admin/config").cookie(login.cookie).header(AdminSessionService.CSRF_HEADER, login.csrf))
                .andExpect(status().isOk());
    }

    @Test void allAuthOperationsRejectCrossSiteAndAmbiguousBrowserMetadata() throws Exception {
        Login login = loggedIn();
        for (String path : new String[]{"login", "logout", "session"}) {
            MockHttpServletRequestBuilder request = path.equals("session") ? get("/api/admin/auth/" + path) : post("/api/admin/auth/" + path);
            mvc.perform(request.cookie(login.cookie).header("Origin", "http://evil.example"))
                    .andExpect(status().isForbidden()).andExpect(header().string("Cache-Control", "no-store"));
            request = path.equals("session") ? get("/api/admin/auth/" + path) : post("/api/admin/auth/" + path);
            mvc.perform(request.cookie(login.cookie).header("Sec-Fetch-Site", "same-site")).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/admin/auth/login").contentType("application/json").content("{\"password\":\"" + PASSWORD + "\"}")
                .header("Sec-Fetch-Mode", "cors")).andExpect(status().isForbidden());
        mvc.perform(login(PASSWORD).header("Origin", "http://evil.example")).andExpect(status().isForbidden());
        // Browser failures do not consume password attempts.
        mvc.perform(login(PASSWORD)).andExpect(status().isOk());
    }

    @Test void logoutRevokesServerSessionExpiresCookieAndIsIdempotent() throws Exception {
        Login login = loggedIn();
        mvc.perform(post("/api/admin/auth/logout").cookie(login.cookie)).andExpect(status().isForbidden());
        MvcResult result = mvc.perform(post("/api/admin/auth/logout").cookie(login.cookie)
                .header(AdminSessionService.CSRF_HEADER, login.csrf)).andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false)).andReturn();
        String cookie = result.getResponse().getHeader("Set-Cookie");
        assertTrue(cookie.contains("Max-Age=0") && cookie.contains("HttpOnly") && cookie.contains("SameSite=Strict") && cookie.contains("Path=/api/admin"));
        mvc.perform(get("/api/admin/auth/session").cookie(login.cookie)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/config").cookie(login.cookie)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/auth/logout").cookie(login.cookie)).andExpect(status().isOk());
        mvc.perform(post("/api/admin/auth/logout")).andExpect(status().isOk());
        mvc.perform(post("/api/admin/auth/logout").header("Origin", "http://evil.example")).andExpect(status().isForbidden());
    }

    @Test void sessionTtlIsFixedNotSlidingRestartLosesSessionsNotConfiguredPassword() throws Exception {
        Login login = loggedIn();
        clock.advanceSeconds(28799);
        mvc.perform(get("/api/admin/auth/session").cookie(login.cookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").value(login.expiry));
        clock.advanceSeconds(1);
        mvc.perform(get("/api/admin/auth/session").cookie(login.cookie)).andExpect(status().isUnauthorized());
        login = loggedIn();
        sessions = new AdminSessionService(HASH, 28800, 10, 5, false, clock);
        mvc = mvc(sessions, 500);
        mvc.perform(get("/api/admin/auth/session").cookie(login.cookie)).andExpect(status().isUnauthorized());
        mvc.perform(login(PASSWORD)).andExpect(status().isOk());
    }

    @Test void successfulReloginRotatesAndRevokesOldSession() throws Exception {
        Login login = loggedIn();
        mvc.perform(login(PASSWORD).cookie(login.cookie)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/auth/session").cookie(login.cookie)).andExpect(status().isUnauthorized());
    }

    @Test void boundedSessionCapacityRecoversOnExpiryAndRevocation() throws Exception {
        sessions = new AdminSessionService(HASH, 10, 1, 10, false, clock);
        mvc = mvc(sessions, 500);
        Login login = loggedIn();
        mvc.perform(login(PASSWORD)).andExpect(status().isServiceUnavailable());
        mvc.perform(post("/api/admin/auth/logout").cookie(login.cookie).header(AdminSessionService.CSRF_HEADER, login.csrf)).andExpect(status().isOk());
        loggedIn();
        mvc.perform(login(PASSWORD)).andExpect(status().isServiceUnavailable());
        clock.advanceSeconds(10);
        loggedIn();
    }

    @Test void remoteHttpIsForbiddenButHttpsAndTrueLoopbackAreAllowed() throws Exception {
        mvc.perform(login(PASSWORD).with(r -> { r.setRemoteAddr("203.0.113.1"); return r; })).andExpect(status().isForbidden());
        mvc.perform(login(PASSWORD).header("Host", "admin.example.test").with(r -> { r.setServerName("admin.example.test"); return r; }))
                .andExpect(status().isForbidden());
        mvc.perform(login(PASSWORD).header("X-Forwarded-Proto", "https")).andExpect(status().isForbidden());
        mvc.perform(login(PASSWORD).header("X-Forwarded-For", "203.0.113.1")).andExpect(status().isForbidden());
        MvcResult https = mvc.perform(post("/api/admin/auth/login").contentType("application/json")
                .content(json.writeValueAsBytes(Map.of("password", PASSWORD))).secure(true)
                .header("Host", "admin.example.test").header("Origin", "https://admin.example.test")
                .with(r -> { r.setRemoteAddr("203.0.113.1"); return r; })).andExpect(status().isOk()).andReturn();
        assertTrue(https.getResponse().getHeader("Set-Cookie").contains("Secure"));
        Login login = loggedIn();
        mvc.perform(get("/api/admin/config").cookie(login.cookie).with(r -> { r.setRemoteAddr("203.0.113.1"); return r; }))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/auth/session").cookie(login.cookie).with(r -> { r.setRemoteAddr("203.0.113.1"); return r; }))
                .andExpect(status().isForbidden());
    }

    @Test void trustedProxyUsesOptInSchemeAndOriginalHostOnly() throws Exception {
        sessions = new AdminSessionService(HASH, 28800, 10, 5, true, clock);
        mvc = mvc(sessions, 500);
        MvcResult proxied = mvc.perform(post("/api/admin/auth/login").contentType("application/json")
                .content(json.writeValueAsBytes(Map.of("password", PASSWORD)))
                .header("Host", "admin.example.test").header("Origin", "https://admin.example.test")
                .header("X-Forwarded-Proto", "https").header("X-Forwarded-For", "203.0.113.1"))
                .andExpect(status().isOk()).andReturn();
        assertTrue(proxied.getResponse().getHeader("Set-Cookie").contains("Secure"));
        mvc.perform(login(PASSWORD).header("X-Forwarded-Proto", "https").header("X-Forwarded-Host", "evil.example"))
                .andExpect(status().isForbidden()); // http Origin cannot match effective https
        mvc.perform(post("/api/admin/auth/login").contentType("application/json").content(json.writeValueAsBytes(Map.of("password", PASSWORD)))
                .header("Host", "admin.example.test").header("Origin", "https://sibling.example.test")
                .header("X-Forwarded-Proto", "https").header("X-Forwarded-Host", "sibling.example.test"))
                .andExpect(status().isForbidden());
    }

    @Test void unknownRequestsRemainUnavailableAndTokenIsNotAnAuthSession() throws Exception {
        mvc.perform(get("/api/admin/auth/unknown")).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/auth/login")).andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/api/admin/auth/session").header("X-Admin-Token", "automation-token")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/config").cookie(new Cookie(AdminSessionService.COOKIE_NAME, "invalid"))).andExpect(status().isNotFound());
    }

    @Test void authenticatedAdminRateLimitStillAppliesToCookieRequests() throws Exception {
        mvc = mvc(sessions, 1);
        Login login = loggedIn();
        mvc.perform(get("/api/admin/config").cookie(login.cookie)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/config").cookie(login.cookie)).andExpect(status().isTooManyRequests());
    }

    @Test void hashToolRejectsInvalidLengthsAndProducesBcryptOnly() {
        assertThrows(IllegalArgumentException.class, () -> AdminPasswordHash.hashPassword("too-short".toCharArray()));
        assertThrows(IllegalArgumentException.class, () -> AdminPasswordHash.hashPassword("审".repeat(25).toCharArray()));
        String hash = AdminPasswordHash.hashPassword(PASSWORD.toCharArray());
        assertTrue(hash.startsWith("$2a$12$"));
        assertTrue(new BCryptPasswordEncoder().matches(PASSWORD, hash));
    }

    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");
        void advanceSeconds(long seconds) { now = now.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
    @RestController static class ProtectedController {
        private final AdminGuard guard;
        ProtectedController(AdminGuard guard) { this.guard = guard; }
        @RequestMapping("/api/admin/config") public ResponseEntity<?> config(HttpServletRequest request) {
            ResponseEntity<String> denied = guard.deny(request);
            return denied == null ? ResponseEntity.ok(Map.of("ok", true)) : denied;
        }
    }
}
