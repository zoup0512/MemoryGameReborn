package com.memorygames.pk;

import com.memorygames.pk.protocol.ProtocolCodec;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Admin P0 surface: dashboard, match void with refund, user lifecycle
 * (balance/ban/restrict), maintenance mode, runtime log level, audit trail.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "pk.admin-token=test-admin",
        "pk.economy.welcome-coins=0",
        "pk.economy.entry-fee=30",
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminP0Test {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private HttpHeaders admin() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Admin-Token", "test-admin");
        headers.set("Content-Type", "application/json");
        return headers;
    }

    private TestWsClient newClient() throws Exception {
        TestWsClient client = new TestWsClient(URI.create("ws://127.0.0.1:" + this.port + "/ws"));
        client.connect();
        return client;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dashboard() {
        ResponseEntity<Map> response = rest.exchange("/api/admin/dashboard", HttpMethod.GET,
                new HttpEntity<>(admin()), Map.class);
        assertEquals(200, response.getStatusCode().value());
        return response.getBody();
    }

    @Test
    @Order(1)
    @Timeout(60)
    void dashboardReflectsLiveState() throws Exception {
        TestWsClient a = newClient();
        TestWsClient b = newClient();
        try {
            ProtocolCodec.Register register = new ProtocolCodec.Register();
            register.uuid = UUID.randomUUID().toString();
            register.nickname = "DashUser";
            register.balance = 60;
            a.send(ProtocolCodec.T_REGISTER, register);
            assertNotNull(a.await(ProtocolCodec.T_AUTH_OK, 5000));
            register.uuid = UUID.randomUUID().toString();
            register.nickname = "DashUser2";
            b.send(ProtocolCodec.T_REGISTER, register);
            assertNotNull(b.await(ProtocolCodec.T_AUTH_OK, 5000));
            b.send(ProtocolCodec.T_MATCH_JOIN, new Object()); // queue entry + event trace

            Map<String, Object> view = dashboard();
            Map<String, Object> server = (Map<String, Object>) view.get("server");
            assertNotNull(server.get("version"), "build version present");
            assertTrue(((Number) server.get("upSeconds")).longValue() >= 0);

            Map<String, Object> online = (Map<String, Object>) view.get("online");
            assertTrue(((Number) online.get("wsConnections")).intValue() >= 2);

            List<String> events = (List<String>) view.get("events");
            assertTrue(events.stream().anyMatch(e -> e.contains("MATCH_JOIN") && e.contains("session=")),
                    "match_join events carry the session id (phantom-queue diagnostics)");
            assertTrue(((Number) view.get("eventsCount")).intValue() > 0);
        } finally {
            a.close();
            b.close();
        }
    }

    @Test
    @Order(2)
    @Timeout(90)
    void voidedMatchRefundsEntryFeeAndMovesNoElo() throws Exception {
        TestWsClient a = newClient();
        TestWsClient b = newClient();
        try {
            for (TestWsClient client : new TestWsClient[]{a, b}) {
                ProtocolCodec.Register register = new ProtocolCodec.Register();
                register.uuid = UUID.randomUUID().toString();
                register.nickname = "Void" + System.nanoTime() % 1000;
                register.balance = 100;
                client.send(ProtocolCodec.T_REGISTER, register);
                assertNotNull(client.await(ProtocolCodec.T_AUTH_OK, 5000));
            }
            a.send(ProtocolCodec.T_MATCH_JOIN, new Object());
            b.send(ProtocolCodec.T_MATCH_JOIN, new Object());
            assertNotNull(a.await(ProtocolCodec.T_MATCH_FOUND, 10000));
            a.send(ProtocolCodec.T_MATCH_START, new Object());
            b.send(ProtocolCodec.T_MATCH_START, new Object());
            ProtocolCodec.MatchFound match =
                    a.await(ProtocolCodec.T_MATCH_FOUND, 1000) == null ? null : null;
            // find the live match id via admin list
            ResponseEntity<Map> list = rest.exchange("/api/admin/matches", HttpMethod.GET,
                    new HttpEntity<>(admin()), Map.class);
            List<Map<String, Object>> matches = (List<Map<String, Object>>) list.getBody().get("matches");
            assertEquals(1, matches.size());
            String matchId = (String) matches.get(0).get("matchId");
            assertTrue(((Number) ((Map<String, Object>) matches.get(0)).get("round")).intValue() >= 0);

            ResponseEntity<Map> voided = rest.exchange("/api/admin/matches/" + matchId + "/terminate",
                    HttpMethod.POST, new HttpEntity<>(admin()), Map.class);
            assertEquals(200, voided.getStatusCode().value());
            assertEquals(true, ((Map<?, ?>) voided.getBody()).get("applied"));

            TestWsClient.Frame end = a.await(ProtocolCodec.T_MATCH_END, 10000);
            assertNotNull(end);
            ProtocolCodec.MatchEnd payload = end.as(ProtocolCodec.MatchEnd.class);
            assertEquals(0, payload.eloDelta, "voided matches move no ELO");
            assertEquals(30, payload.coinDelta, "entry fee refunded");
            assertEquals(100, payload.balance, "100 - 30 entry + 30 refund");
        } finally {
            a.close();
            b.close();
        }
    }

    @Test
    @Order(3)
    @Timeout(60)
    void userLifecycleAndAudit() throws Exception {
        TestWsClient a = newClient();
        long userId;
        String token;
        try {
            ProtocolCodec.Register register = new ProtocolCodec.Register();
            register.uuid = UUID.randomUUID().toString();
            register.nickname = "LifeCycle";
            register.balance = 40;
            a.send(ProtocolCodec.T_REGISTER, register);
            TestWsClient.Frame ok = a.await(ProtocolCodec.T_AUTH_OK, 5000);
            assertNotNull(ok);
            ProtocolCodec.AuthOk auth = ok.as(ProtocolCodec.AuthOk.class);
            userId = auth.userId;
            token = auth.token;

            // search finds the account
            ResponseEntity<Map> search = rest.exchange("/api/admin/users?q=LifeCycle",
                    HttpMethod.GET, new HttpEntity<>(admin()), Map.class);
            List<Map<String, Object>> users = (List<Map<String, Object>>) search.getBody().get("users");
            assertEquals(1, users.size());

            // balance grant +50 -> 90, echoed on re-auth
            rest.exchange("/api/admin/users/" + userId + "/balance", HttpMethod.POST,
                    new HttpEntity<>("{\"delta\":50,\"reason\":\"compensation\"}", admin()), Map.class);
            TestWsClient c = newClient();
            try {
                ProtocolCodec.Auth authFrame = new ProtocolCodec.Auth();
                authFrame.token = token;
                c.send(ProtocolCodec.T_AUTH, authFrame);
                TestWsClient.Frame reauth = c.await(ProtocolCodec.T_AUTH_OK, 5000);
                assertNotNull(reauth);
                assertEquals(90, reauth.as(ProtocolCodec.AuthOk.class).balance);

                // ban -> auth refused with 403
                rest.exchange("/api/admin/users/" + userId + "/ban", HttpMethod.POST,
                        new HttpEntity<>("{\"hours\":1,\"reason\":\"test\"}", admin()), Map.class);
                TestWsClient d = newClient();
                try {
                    d.send(ProtocolCodec.T_AUTH, authFrame);
                    TestWsClient.Frame err = d.await(ProtocolCodec.T_ERROR, 5000);
                    assertNotNull(err);
                    assertEquals(403, err.as(ProtocolCodec.ErrorMsg.class).code);
                } finally {
                    d.close();
                }

                // unban -> auth ok again
                rest.exchange("/api/admin/users/" + userId + "/unban", HttpMethod.POST,
                        new HttpEntity<>(admin()), Map.class);
                TestWsClient e = newClient();
                try {
                    e.send(ProtocolCodec.T_AUTH, authFrame);
                    assertNotNull(e.await(ProtocolCodec.T_AUTH_OK, 5000));
                } finally {
                    e.close();
                }
            } finally {
                c.close();
            }
        } finally {
            a.close();
        }

        // audit trail captured the mutations
        ResponseEntity<Map> audit = rest.exchange("/api/admin/audit?n=50", HttpMethod.GET,
                new HttpEntity<>(admin()), Map.class);
        List<String> rows = (List<String>) audit.getBody().get("rows");
        assertTrue(rows.stream().anyMatch(r -> r.contains("USER_BALANCE")));
        assertTrue(rows.stream().anyMatch(r -> r.contains("USER_BAN")));
        assertTrue(rows.stream().anyMatch(r -> r.contains("USER_UNBAN")));
        assertTrue(rows.stream().anyMatch(r -> r.contains("MATCH_TERMINATE")));
    }

    @Test
    @Order(4)
    @Timeout(60)
    void maintenanceBlocksNewMatches() throws Exception {
        TestWsClient a = newClient();
        try {
            ProtocolCodec.Register register = new ProtocolCodec.Register();
            register.uuid = UUID.randomUUID().toString();
            register.nickname = "MaintUser";
            register.balance = 60;
            a.send(ProtocolCodec.T_REGISTER, register);
            assertNotNull(a.await(ProtocolCodec.T_AUTH_OK, 5000));

            rest.exchange("/api/admin/maintenance", HttpMethod.POST,
                    new HttpEntity<>("{\"enabled\":true,\"message\":\"升级维护中\"}", admin()), Map.class);
            try {
                a.send(ProtocolCodec.T_MATCH_JOIN, new Object());
                TestWsClient.Frame err = a.await(ProtocolCodec.T_ERROR, 5000);
                assertNotNull(err);
                assertEquals(503, err.as(ProtocolCodec.ErrorMsg.class).code);
                assertEquals("升级维护中", err.as(ProtocolCodec.ErrorMsg.class).message);
            } finally {
                rest.exchange("/api/admin/maintenance", HttpMethod.POST,
                        new HttpEntity<>("{\"enabled\":false}", admin()), Map.class);
            }
            // after lift, queueing works again (bot fallback eventually pairs)
            a.send(ProtocolCodec.T_MATCH_JOIN, new Object());
            assertNotNull(a.await(ProtocolCodec.T_MATCH_FOUND, 20000));
            a.send(ProtocolCodec.T_MATCH_START, new Object());
            assertNotNull(a.await(ProtocolCodec.T_ROUND_START, 10000));
            a.send(ProtocolCodec.T_RESIGN, new Object());
            assertNotNull(a.await(ProtocolCodec.T_MATCH_END, 10000));
        } finally {
            a.close();
        }
    }

    @Test
    @Order(5)
    void runtimeLogLevel() {
        ResponseEntity<Map> changed = rest.exchange("/api/admin/loglevel", HttpMethod.POST,
                new HttpEntity<>("{\"logger\":\"com.memorygames.pk.test\",\"level\":\"DEBUG\"}", admin()),
                Map.class);
        assertEquals(200, changed.getStatusCode().value());
        assertEquals("DEBUG", ((Map<?, ?>) changed.getBody()).get("level"));

        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger("com.memorygames.pk.test");
        assertEquals(ch.qos.logback.classic.Level.DEBUG, logger.getLevel());
    }

    @Test
    @Order(6)
    void unauthorizedIsHidden() {
        assertEquals(404, rest.getForEntity("/api/admin/dashboard", String.class)
                .getStatusCode().value());
        HttpHeaders wrong = new HttpHeaders();
        wrong.set("X-Admin-Token", "nope");
        assertEquals(404, rest.exchange("/api/admin/matches", HttpMethod.GET,
                new HttpEntity<>(wrong), String.class).getStatusCode().value());
    }
}
