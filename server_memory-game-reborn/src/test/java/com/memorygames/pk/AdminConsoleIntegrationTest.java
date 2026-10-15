package com.memorygames.pk;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.memorygames.pk.admin.AdminAuditService;
import com.memorygames.pk.admin.AdminState;
import com.memorygames.pk.config.PkProperties;
import com.memorygames.pk.user.UserAccount;
import com.memorygames.pk.user.UserRepository;
import com.memorygames.pk.user.UserService;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** HTTP contracts used by the embedded, dependency-free admin console. */
@org.springframework.context.annotation.Import(PassingNicknameTestConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {"pk.admin-token=test-admin", "pk.admin-rate-limit=500"})
class AdminConsoleIntegrationTest {
    @Autowired TestRestTemplate rest;
    @Autowired AdminState state;
    @Autowired PkProperties properties;
    @Autowired UserService service;
    @Autowired UserRepository users;
    @Autowired AdminAuditService audit;

    private HttpHeaders admin() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Admin-Token", "test-admin");
        headers.set("Content-Type", "application/json");
        return headers;
    }

    private ResponseEntity<Map> send(String path, HttpMethod method, Object body) {
        return rest.exchange(path, method, new HttpEntity<>(body, admin()), Map.class);
    }

    @Test
    void maintenanceReadIsCurrentAndDoesNotWriteAudit() {
        boolean previous = state.isMaintenance();
        String message = state.getMaintenanceMessage();
        try {
            state.setMaintenance(true, "升级中");
            long audits = audit.recent(200).size();
            ResponseEntity<Map> response = send("/api/admin/maintenance", HttpMethod.GET, null);
            assertEquals(200, response.getStatusCode().value());
            assertEquals(Map.of("enabled", true, "message", "升级中"), response.getBody());
            assertEquals("no-store", response.getHeaders().getCacheControl());
            assertEquals(audits, audit.recent(200).size(), "reads must not emit mutation audit rows");
        } finally {
            state.setMaintenance(previous, message);
        }
    }

    @Test
    void logLevelReportsConfiguredAndInheritedLevelsWithoutCreatingUnknownLoggers() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger parent = context.getLogger("console-test." + UUID.randomUUID());
        parent.setLevel(Level.WARN);
        Logger child = context.getLogger(parent.getName() + ".child");
        child.setLevel(null);
        ResponseEntity<Map> inherited = send("/api/admin/loglevel?logger=" + child.getName(), HttpMethod.GET, null);
        assertEquals(200, inherited.getStatusCode().value());
        assertEquals(child.getName(), inherited.getBody().get("logger"));
        assertTrue(inherited.getBody().containsKey("configuredLevel"));
        assertNull(inherited.getBody().get("configuredLevel"));
        assertEquals("WARN", inherited.getBody().get("effectiveLevel"));
        child.setLevel(Level.ERROR);
        ResponseEntity<Map> configured = send("/api/admin/loglevel?logger=" + child.getName(), HttpMethod.GET, null);
        assertEquals("ERROR", configured.getBody().get("configuredLevel"));
        assertEquals("ERROR", configured.getBody().get("effectiveLevel"));
        assertEquals("ROOT", send("/api/admin/loglevel", HttpMethod.GET, null).getBody().get("logger"));
        String unknown = "console-unknown-" + UUID.randomUUID();
        int before = context.getLoggerList().size();
        for (int i = 0; i < 5; i++) {
            assertEquals(404, send("/api/admin/loglevel?logger=" + unknown + i, HttpMethod.GET, null).getStatusCode().value());
            assertNull(context.exists(unknown + i));
        }
        assertEquals(before, context.getLoggerList().size());
        assertEquals(400, send("/api/admin/loglevel?logger= ", HttpMethod.GET, null).getStatusCode().value());
    }

    @Test
    void newReadsRemainHiddenWithoutCorrectToken() {
        for (String path : new String[]{"/api/admin/maintenance", "/api/admin/loglevel"}) {
            assertEquals(404, rest.getForEntity(path, String.class).getStatusCode().value());
            HttpHeaders wrong = new HttpHeaders();
            wrong.set("X-Admin-Token", "wrong");
            assertEquals(404, rest.exchange(path, HttpMethod.GET, new HttpEntity<>(wrong), String.class).getStatusCode().value());
        }
        HttpHeaders wrong = admin();
        wrong.set("X-Admin-Token", "wrong");
        assertEquals(404, rest.exchange("/api/admin/maintenance", HttpMethod.POST,
                new HttpEntity<>(Map.of("enabled", true), wrong), String.class).getStatusCode().value());
    }

    @Test
    void everyMutationRequiresNonBlankReasonBeforeLookingUpOrChangingState() {
        String[] paths = {"/config", "/maintenance", "/loglevel", "/matches/missing/settle", "/matches/missing/terminate",
                "/users/999999/balance", "/users/999999/elo", "/users/999999/restrict", "/users/999999/ban",
                "/users/999999/unban", "/users/999999"};
        long auditCount = audit.recent(200).size();
        long duration = properties.getRoundDurationOverrideMs();
        boolean maintenance = state.isMaintenance();
        for (String path : paths) {
            HttpMethod method = path.equals("/config") ? HttpMethod.PUT
                    : path.equals("/users/999999") ? HttpMethod.DELETE : HttpMethod.POST;
            for (String body : new String[]{"{}", "{\"reason\":null}", "{\"reason\":\"   \"}"}) {
                ResponseEntity<Map> response = send("/api/admin" + path, method, body);
                assertEquals(400, response.getStatusCode().value(), path + " body=" + body);
                assertEquals("reason is required", response.getBody().get("error"));
            }
        }
        assertEquals(duration, properties.getRoundDurationOverrideMs());
        assertEquals(maintenance, state.isMaintenance());
        assertEquals(auditCount, audit.recent(200).size());
    }

    @Test
    void missingNullAndMalformedBodiesCannotApplyMaintenanceOrConfig() {
        long duration = properties.getRoundDurationOverrideMs();
        boolean maintenance = state.isMaintenance();
        for (String path : new String[]{"/api/admin/maintenance", "/api/admin/config"}) {
            HttpMethod method = path.endsWith("config") ? HttpMethod.PUT : HttpMethod.POST;
            for (String body : new String[]{null, "null", "{broken", "[]"}) {
                assertEquals(400, send(path, method, body).getStatusCode().value());
            }
        }
        assertEquals(400, send("/api/admin/maintenance", HttpMethod.POST, Map.of("reason", "missing flag")).getStatusCode().value());
        assertEquals(duration, properties.getRoundDurationOverrideMs());
        assertEquals(maintenance, state.isMaintenance());
    }

    @Test
    void maintenanceAndLogChangesEchoAndAuditReason() {
        boolean previous = state.isMaintenance();
        String previousMessage = state.getMaintenanceMessage();
        String loggerName = "console-change-" + UUID.randomUUID();
        try {
            assertEquals(200, send("/api/admin/maintenance", HttpMethod.POST,
                    Map.of("enabled", true, "message", "planned", "reason", "planned-maintenance")).getStatusCode().value());
            assertEquals(Map.of("enabled", true, "message", "planned"), send("/api/admin/maintenance", HttpMethod.GET, null).getBody());
            assertEquals(200, send("/api/admin/loglevel", HttpMethod.POST,
                    Map.of("logger", loggerName, "level", "debug", "reason", "debug-investigation")).getStatusCode().value());
            assertEquals("DEBUG", send("/api/admin/loglevel?logger=" + loggerName, HttpMethod.GET, null).getBody().get("configuredLevel"));
            assertEquals(400, send("/api/admin/loglevel", HttpMethod.POST,
                    Map.of("logger", loggerName, "level", "invalid", "reason", "invalid-level")).getStatusCode().value());
            assertEquals(400, send("/api/admin/loglevel", HttpMethod.POST,
                    Map.of("logger", " ", "level", "DEBUG", "reason", "invalid-name")).getStatusCode().value());
            assertAudit("MAINTENANCE_ON", "planned-maintenance");
            assertAudit("LOG_LEVEL", "debug-investigation");
        } finally {
            state.setMaintenance(previous, previousMessage);
        }
    }

    @Test
    void configAuditContainsReasonAndAppliedClampedValues() {
        int previous = properties.getEconomy().getEntryFee();
        try {
            ResponseEntity<Map> response = send("/api/admin/config", HttpMethod.PUT,
                    Map.of("entryFee", 2000, "reason", "economy-config-test"));
            assertEquals(200, response.getStatusCode().value());
            assertEquals(1000, response.getBody().get("entryFee"));
            assertTrue(audit.recent(200).stream().anyMatch(row -> row.action.equals("CONFIG_APPLIED")
                    && row.paramsJson.contains("economy-config-test") && row.paramsJson.contains("entryFee=1000")));
        } finally {
            properties.getEconomy().setEntryFee(previous);
        }
    }

    @Test
    void maximumLengthReasonPersistsWithoutTruncationAndOversizedReasonIsRejected() {
        String reason = "审".repeat(500);
        assertEquals(200, send("/api/admin/config", HttpMethod.PUT,
                Map.of("reason", reason)).getStatusCode().value());
        assertTrue(audit.recent(200).stream().anyMatch(row -> row.action.equals("CONFIG_APPLIED")
                && row.paramsJson.equals("runtime config change: reason=" + reason)));
        int previous = properties.getEconomy().getEntryFee();
        assertEquals(400, send("/api/admin/config", HttpMethod.PUT,
                Map.of("entryFee", previous + 1, "reason", reason + "审")).getStatusCode().value());
        assertEquals(previous, properties.getEconomy().getEntryFee());
    }

    @Test
    void userMutationsValidateNumbersAndCaptureReasonsIncludingUnbanAndDelete() {
        UserAccount user = service.register(UUID.randomUUID().toString(), "ConsoleUser");
        String base = "/api/admin/users/" + user.id;
        long bannedUntil = user.bannedUntil;
        for (int hours : new int[]{0, -1}) {
            assertEquals(400, send(base + "/ban", HttpMethod.POST, Map.of("hours", hours, "reason", "invalid-ban")).getStatusCode().value());
            assertEquals(bannedUntil, users.findById(user.id).orElseThrow().bannedUntil);
        }
        assertEquals(400, send(base + "/restrict", HttpMethod.POST,
                Map.of("matches", -1, "reason", "invalid-restriction")).getStatusCode().value());
        int initialBalance = user.balance;
        assertEquals(200, send(base + "/balance", HttpMethod.POST, Map.of("delta", 5, "reason", "user-balance-test")).getStatusCode().value());
        assertEquals(initialBalance + 5, users.findById(user.id).orElseThrow().balance);
        assertEquals(200, send(base + "/elo", HttpMethod.POST, Map.of("set", 1350, "reason", "user-elo-test")).getStatusCode().value());
        assertEquals(1350, users.findById(user.id).orElseThrow().elo);
        assertEquals(200, send(base + "/restrict", HttpMethod.POST, Map.of("matches", 3, "reason", "user-restrict-test")).getStatusCode().value());
        assertEquals(3, users.findById(user.id).orElseThrow().restrictedMatches);
        assertEquals(200, send(base + "/ban", HttpMethod.POST, Map.of("hours", 1, "reason", "user-ban-test")).getStatusCode().value());
        assertTrue(users.findById(user.id).orElseThrow().bannedUntil > System.currentTimeMillis());
        assertEquals(200, send(base + "/unban", HttpMethod.POST, Map.of("reason", "user-unban-test")).getStatusCode().value());
        assertEquals(0, users.findById(user.id).orElseThrow().bannedUntil);
        assertEquals(200, send(base, HttpMethod.DELETE, Map.of("reason", "user-delete-test")).getStatusCode().value());
        assertTrue(users.findById(user.id).orElseThrow().deleted);
        for (String action : new String[]{"BALANCE", "ELO", "RESTRICT", "BAN", "UNBAN", "DELETE"}) {
            assertAudit("USER_" + action, "user-" + action.toLowerCase(java.util.Locale.ROOT) + "-test");
        }
    }

    @Test
    void publicShellAndStaticResourcesHaveSecurityHeaders() {
        for (String path : new String[]{"/api/admin/console", "/admin/index.html", "/admin/admin.js", "/admin/admin.css"}) {
            ResponseEntity<String> response = rest.getForEntity(path, String.class);
            assertEquals(200, response.getStatusCode().value(), path);
            assertEquals("no-store", response.getHeaders().getCacheControl());
            assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
            String csp = response.getHeaders().getFirst("Content-Security-Policy");
            assertNotNull(csp);
            assertTrue(csp.contains("script-src 'self'") && csp.contains("frame-ancestors 'none'"));
            assertFalse(response.getHeaders().containsKey("X-Admin-Token"));
        }
    }

    private void assertAudit(String action, String reason) {
        assertTrue(audit.recent(200).stream().anyMatch(row -> row.action.equals(action)
                && row.paramsJson.contains("reason=" + reason)), action + " audit must include reason");
    }
}
