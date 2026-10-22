package com.memorygames.pk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memorygames.pk.diagnostics.DiagnosticSanitizer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QuestionDiagnosticSanitizerTest {
    private final ObjectMapper json = new ObjectMapper();
    private final DiagnosticSanitizer sanitizer = new DiagnosticSanitizer(json);

    @Test
    void preservesQuestionOwnershipAndScheduledVersusCurrentQuestion() throws Exception {
        String source = json.writeValueAsString(Map.ofEntries(
                Map.entry("event", "question_callback"),
                Map.entry("matchId", "798137fe6642"), Map.entry("gameId", "4"), Map.entry("round", 4),
                Map.entry("activityId", "0123456789abcdef"), Map.entry("fingerprint", "fedcba9876543210"),
                Map.entry("question", 3), Map.entry("targetQuestion", 2), Map.entry("level", 3),
                Map.entry("callbackId", 7), Map.entry("elapsedMs", 1234567890123L),
                Map.entry("phase", "execute"), Map.entry("flowRun", 3), Map.entry("flowStep", 2),
                Map.entry("generation", 1), Map.entry("delayMs", 200), Map.entry("reason", "answer_correct")));
        JsonNode result = json.readTree(sanitizer.sanitize(source));
        assertEquals(json.readTree(source), result);
        assertNotEquals(result.path("question"), result.path("targetQuestion"));
    }

    @Test
    void acceptsQuestionFlowAndActivityEventsWithPartyOwnership() throws Exception {
        for (String event : List.of("question_generated", "question_visible", "question_hidden", "question_options",
                "question_answer", "question_transition", "question_callback", "flow_start", "flow_state",
                "flow_pause", "flow_resume", "flow_cancel", "game_activity")) {
            JsonNode result = json.readTree(sanitizer.sanitize(json.writeValueAsString(Map.of(
                    "event", event, "matchId", "12345678-1234-1234-1234-123456789abc",
                    "phase", "request", "question", 1))));
            assertEquals(event, result.path("event").asText());
            assertEquals("12345678-1234-1234-1234-123456789abc", result.path("matchId").asText());
        }
    }

    @Test
    void dropsRawQuestionMaterialInvalidFingerprintsAndUnboundedPhases() throws Exception {
        JsonNode result = json.readTree(sanitizer.sanitize(json.writeValueAsString(Map.ofEntries(
                Map.entry("event", "question_generated"), Map.entry("activityId", "not-an-instance"),
                Map.entry("fingerprint", "f".repeat(64)), Map.entry("phase", "arbitrary-private-text"),
                Map.entry("question", "3"), Map.entry("targetQuestion", 1.5),
                Map.entry("seed", 8840975373404224582L), Map.entry("rightAnswer", 7),
                Map.entry("selected", 3), Map.entry("layout", List.of(1, 2, 3)),
                Map.entry("nickname", "PrivateName"), Map.entry("password", "private-secret")))));
        assertEquals(json.readTree("{\"event\":\"question_generated\"}"), result);
    }
}
