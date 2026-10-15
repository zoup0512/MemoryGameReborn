package com.memorygames.pk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real Spring wiring and existing admin controllers, independent of static console assets. */
@org.springframework.context.annotation.Import(PassingNicknameTestConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSessionIntegrationTest {
    private static final String PASSWORD = "fixture-admin-password-only";
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("pk.admin-password-hash", () -> new BCryptPasswordEncoder(4).encode(PASSWORD));
        registry.add("pk.admin-token", () -> "session-integration-automation");
        registry.add("pk.admin-rate-limit", () -> "500");
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test void sessionProtectsRealAdminRoutesAndAutomationRemainsIndependent() throws Exception {
        MvcResult login = mvc.perform(post("/api/admin/auth/login").contentType("application/json")
                        .content("{\"password\":\"" + PASSWORD + "\"}")
                        .secure(true).header("Host", "admin.example.test").header("Origin", "https://admin.example.test"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andReturn();
        String header = login.getResponse().getHeader("Set-Cookie");
        assertNotNull(header);
        assertTrue(header.contains("Secure") && header.contains("SameSite=Strict"));
        Cookie cookie = new Cookie("PK_ADMIN_SESSION", header.substring(header.indexOf('=') + 1, header.indexOf(';')));
        JsonNode body = json.readTree(login.getResponse().getContentAsByteArray());
        String csrf = body.get("csrfToken").textValue();
        for (String path : new String[]{"/api/admin/config", "/api/admin/dashboard", "/api/admin/maintenance", "/api/admin/loglevel", "/api/admin/audit"}) {
            mvc.perform(get(path).cookie(cookie).secure(true).header("Host", "admin.example.test"))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        }
        for (String path : new String[]{"/api/admin/users", "/api/admin/matches"}) {
            mvc.perform(get(path).cookie(cookie).secure(true)).andExpect(status().isOk());
        }
        for (String path : new String[]{"/api/admin/maintenance", "/api/admin/loglevel", "/api/admin/users/999999/balance",
                "/api/admin/users/999999/elo", "/api/admin/users/999999/restrict", "/api/admin/users/999999/ban",
                "/api/admin/users/999999/unban", "/api/admin/matches/missing/settle", "/api/admin/matches/missing/terminate"}) {
            mvc.perform(post(path).cookie(cookie).secure(true).contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(delete("/api/admin/users/999999").cookie(cookie).secure(true).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/config").cookie(cookie).secure(true).contentType("application/json")
                .content("{\"reason\":\"session integration\"}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/config").cookie(cookie).secure(true).contentType("application/json")
                .content("{\"reason\":\"session integration\"}").header("X-Admin-CSRF", csrf)
                .header("Host", "admin.example.test").header("Origin", "https://admin.example.test")).andExpect(status().isOk());
        mvc.perform(get("/api/admin/config").cookie(cookie).secure(true).header("X-Admin-Token", "incorrect"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/auth/logout").cookie(cookie).secure(true).header("X-Admin-CSRF", csrf))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/auth/session").cookie(cookie).secure(true)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/config").cookie(cookie).secure(true)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/config").header("X-Admin-Token", "session-integration-automation"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/auth/unknown")).andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", "no-store"));
    }
}
