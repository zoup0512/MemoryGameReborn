package com.memorygames.pk;

import com.memorygames.pk.protocol.ProtocolCodec;
import org.junit.jupiter.api.Test;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runtime configuration (PRD M2 ops): the admin API hot-edits round
 * durations and the coin economy; new matches pick the values up without a
 * restart, auth_ok echoes the live economy to clients, and the endpoint is
 * disabled without the admin token.
 */
@org.springframework.context.annotation.Import(PassingNicknameTestConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "pk.admin-token=test-admin",
        "pk.economy.welcome-coins=50",
})
class AdminConfigTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private TestWsClient newClient() throws Exception {
        TestWsClient client = new TestWsClient(URI.create("ws://127.0.0.1:" + this.port + "/ws"));
        client.connect();
        return client;
    }

    private TestWsClient register(TestWsClient client, String nickname, int balance) {
        ProtocolCodec.Register register = new ProtocolCodec.Register();
        register.uuid = UUID.randomUUID().toString();
        register.nickname = nickname;
        register.balance = balance;
        client.send(ProtocolCodec.T_REGISTER, register);
        return client;
    }

    @Test
    void configEndpointIsGuarded() {
        ResponseEntity<String> noToken = rest.getForEntity("/api/admin/config", String.class);
        assertEquals(404, noToken.getStatusCode().value(), "no token -> hidden");

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Admin-Token", "wrong");
        ResponseEntity<String> badToken = rest.exchange("/api/admin/config", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertEquals(404, badToken.getStatusCode().value(), "wrong token -> hidden");
    }

    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void hotConfigAppliesToNewMatchesAndAuthEcho() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Admin-Token", "test-admin");
        headers.set("Content-Type", "application/json");

        // Hot-config: 45s rounds and a 7-coin entry fee.
        String body = "{\"roundDurationOverrideMs\":45000,\"entryFee\":7,\"reason\":\"hot config regression\"}";
        ResponseEntity<Map> updated = rest.exchange("/api/admin/config", HttpMethod.PUT,
                new HttpEntity<>(body, headers), Map.class);
        assertEquals(200, updated.getStatusCode().value());
        assertEquals(45000, ((Number) updated.getBody().get("roundDurationOverrideMs")).intValue());
        assertEquals(7, ((Number) updated.getBody().get("entryFee")).intValue());

        try {
            TestWsClient a = newClient();
            TestWsClient b = newClient();
            try {
                register(a, "CfgA", 50);
                register(b, "CfgB", 50);

                // auth_ok must echo the live economy so clients gate/mirror correctly.
                a.send(ProtocolCodec.T_AUTH, new Object()); // no-op frame; auth_ok came on register
                TestWsClient.Frame authFrame = b.await(ProtocolCodec.T_AUTH_OK, 5000);
                assertNotNull(authFrame);
                ProtocolCodec.AuthOk auth = authFrame.as(ProtocolCodec.AuthOk.class);
                assertEquals(7, auth.entryFee, "auth_ok carries the hot-edited entry fee");

                a.send(ProtocolCodec.T_MATCH_JOIN, new Object());
                b.send(ProtocolCodec.T_MATCH_JOIN, new Object());
                TestWsClient.Frame found = a.await(ProtocolCodec.T_MATCH_FOUND, 10000);
                assertNotNull(found);
                ProtocolCodec.MatchFound match = found.as(ProtocolCodec.MatchFound.class);
                assertTrue(match.rounds.length > 0);
                assertEquals(45000, match.rounds[0].durationMs,
                        "round plan carries the hot-edited duration");

                a.send(ProtocolCodec.T_MATCH_START, new Object());
                b.send(ProtocolCodec.T_MATCH_START, new Object());
                assertNotNull(a.await(ProtocolCodec.T_ROUND_START, 10000));

                // Entry fee 7 charged from both 50-coin accounts.
                a.send(ProtocolCodec.T_RESIGN, new Object());
                TestWsClient.Frame endA = a.await(ProtocolCodec.T_MATCH_END, 15000);
                assertNotNull(endA);
                assertEquals(43, endA.as(ProtocolCodec.MatchEnd.class).balance,
                        "50 - hot-edited entry fee 7");
            } finally {
                a.close();
                b.close();
            }
        } finally {
            // Restore shared-suite defaults for other tests in this context.
            rest.exchange("/api/admin/config", HttpMethod.PUT,
                    new HttpEntity<>("{\"roundDurationOverrideMs\":2000,\"entryFee\":30,\"reason\":\"restore regression defaults\"}", headers), Map.class);
        }
    }
}
