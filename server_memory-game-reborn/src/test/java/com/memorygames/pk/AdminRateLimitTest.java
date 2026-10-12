package com.memorygames.pk;

import org.junit.jupiter.api.Test;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The admin surface is rate limited per IP (guard, design 6). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "pk.admin-token=test-admin",
        "pk.admin-rate-limit=3",
})
class AdminRateLimitTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Test
    void rapidRequestsGetThrottled() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Admin-Token", "test-admin");
        int throttled = 0;
        for (int i = 0; i < 8; i++) {
            ResponseEntity<String> response = rest.exchange("/api/admin/audit?n=1", HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);
            if (response.getStatusCode().value() == 429) {
                throttled++;
            }
        }
        assertTrue(throttled > 0, "at least one request must be throttled at 3/min");
        assertEquals(429, rest.exchange("/api/admin/audit?n=1", HttpMethod.GET,
                new HttpEntity<>(headers), String.class).getStatusCode().value(),
                "still throttled within the window");
    }
}
