package com.memorygames.pk;

import com.memorygames.pk.match.MatchRecord;
import com.memorygames.pk.match.MatchRecordRepository;
import com.memorygames.pk.protocol.ProtocolCodec;
import com.memorygames.pk.user.UserAccount;
import com.memorygames.pk.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Real database filtering and the unchanged, authenticated WebSocket history contract. */
@Import(PassingNicknameTestConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:history-mode;DB_CLOSE_DELAY=-1",
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
        "logging.file.name=build/history-mode-review/test-logs/history-mode.log"
})
@ActiveProfiles("test")
@Timeout(30)
class HistoryModeIntegrationTest {
    @Autowired UserService users;
    @Autowired MatchRecordRepository records;
    @LocalServerPort int port;

    @Test
    void oldFriendHistorySurvivesMoreThanTwentyNewerOtherModesAndUsesViewerSeat() throws Exception {
        UserAccount viewer = user(), opponent = user(), stranger = user();
        MatchRecord seatA = record("FRIEND", viewer.id, opponent.id, 1, false);
        MatchRecord seatB = record("FRIEND", opponent.id, viewer.id, 2, false);
        for (int i = 0; i < 25; i++) {
            record(i % 2 == 0 ? "RANKED" : "PRACTICE", viewer.id, opponent.id, 100 + i, false);
        }
        record("FRIEND", viewer.id, opponent.id, 1000, true);
        record("FRIEND", opponent.id, viewer.id, 1001, true);
        record("FRIEND", stranger.id, opponent.id, 1002, false);
        assertTrue(records.findRecentForUser(viewer.id, PageRequest.of(0, 20)).stream()
                .noneMatch(row -> "FRIEND".equals(row.mode)), "fixture reproduces the client-side filter bug");
        assertEquals(List.of(seatB.matchId, seatA.matchId),
                records.findRecentForUserAndMode(viewer.id, "FRIEND", PageRequest.of(0, 20))
                        .stream().map(row -> row.matchId).toList());

        TestWsClient client = connect(viewer);
        try {
            ProtocolCodec.HistoryRequest request = new ProtocolCodec.HistoryRequest();
            request.mode = "FRIEND";
            client.sendRaw(ProtocolCodec.encode(ProtocolCodec.T_HISTORY_REQUEST, request, "old-friend"));
            ProtocolCodec.HistoryEntry[] matches = history(client, "old-friend");
            assertEquals(2, matches.length);
            assertEntry(matches[0], seatB, false);
            assertEntry(matches[1], seatA, true);
        } finally {
            client.close();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"RANKED", "PRACTICE", "FRIEND"})
    void eachAllowedModeIsFilteredBeforeItsTwentyRowLimit(String mode) throws Exception {
        UserAccount viewer = user(), opponent = user(), stranger = user();
        List<MatchRecord> expected = new ArrayList<>();
        for (int i = 0; i < 23; i++) {
            expected.add(record(mode, i % 2 == 0 ? viewer.id : opponent.id,
                    i % 2 == 0 ? opponent.id : viewer.id, i + 1, false));
        }
        String otherMode = "RANKED".equals(mode) ? "PRACTICE" : "RANKED";
        record(otherMode, viewer.id, opponent.id, 1000, false);
        record(otherMode, opponent.id, viewer.id, 1001, false);
        record(mode, viewer.id, opponent.id, 1002, true);
        record(mode, opponent.id, viewer.id, 1003, true);
        record(mode, stranger.id, opponent.id, 1004, false);
        TestWsClient client = connect(viewer);
        try {
            client.sendRaw(ProtocolCodec.encode(ProtocolCodec.T_HISTORY_REQUEST, Map.of("mode", mode), mode));
            ProtocolCodec.HistoryEntry[] matches = history(client, mode);
            assertEquals(20, matches.length);
            for (int i = 0; i < 20; i++) {
                MatchRecord row = expected.get(22 - i);
                assertEntry(matches[i], row, row.playerA == viewer.id);
            }
        } finally {
            client.close();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ",\"d\":{}", ",\"d\":null", ",\"d\":{\"mode\":null}",
            ",\"d\":{\"mode\":\"\"}"})
    void missingNullOrEmptyModeKeepsOriginalAllModeTwentyRows(String payload) throws Exception {
        UserAccount viewer = user(), opponent = user();
        String[] modes = {"FRIEND", "PRACTICE", "RANKED"};
        List<MatchRecord> expected = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            expected.add(record(modes[i % 3], viewer.id, opponent.id, i + 1, false));
        }
        TestWsClient client = connect(viewer);
        try {
            client.sendRaw("{\"v\":1,\"t\":\"history_request\",\"rid\":\"legacy\"" + payload + "}");
            ProtocolCodec.HistoryEntry[] matches = history(client, "legacy");
            assertEquals(20, matches.length);
            for (int i = 0; i < 20; i++) assertEntry(matches[i], expected.get(23 - i), true);
        } finally {
            client.close();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"mode\":\"FRIEND_PARTY\"}", "{\"mode\":\"UNKNOWN\"}",
            "{\"mode\":\"friend\"}", "{\"mode\":\" FRIEND\"}", "{\"mode\":\" \"}",
            "{\"mode\":123}", "{\"mode\":true}", "{\"mode\":{}}", "{\"mode\":[]}",
            "[]", "42", "\"FRIEND\""})
    void invalidModeOrPayloadReturns400WithRequestId(String payload) throws Exception {
        TestWsClient client = connect(user());
        try {
            client.sendRaw("{\"v\":1,\"t\":\"history_request\",\"rid\":\"invalid-mode\",\"d\":" + payload + "}");
            TestWsClient.Frame frame = client.await(ProtocolCodec.T_ERROR, 5000);
            assertNotNull(frame);
            assertEquals("invalid-mode", frame.rid);
            assertEquals(400, frame.as(ProtocolCodec.ErrorMsg.class).code);
            assertFalse(((Map<?, ?>) frame.data).containsKey("matches"));
        } finally {
            client.close();
        }
    }

    @Test
    void historyRequiresAuthenticationAndCannotSelectAnotherUsersRecords() throws Exception {
        UserAccount owner = user(), opponent = user(), outsider = user();
        record("FRIEND", owner.id, opponent.id, 1, false);
        TestWsClient anonymous = new TestWsClient(URI.create("ws://localhost:" + port + "/ws"));
        anonymous.connect();
        try {
            anonymous.sendRaw(ProtocolCodec.encode(ProtocolCodec.T_HISTORY_REQUEST,
                    Map.of("mode", "FRIEND", "userId", owner.id), "unauthorized"));
            TestWsClient.Frame frame = anonymous.await(ProtocolCodec.T_ERROR, 5000);
            assertNotNull(frame);
            assertEquals("unauthorized", frame.rid);
            assertEquals(401, frame.as(ProtocolCodec.ErrorMsg.class).code);
            assertFalse(((Map<?, ?>) frame.data).containsKey("matches"));
        } finally {
            anonymous.close();
        }
        TestWsClient client = connect(outsider);
        try {
            client.sendRaw(ProtocolCodec.encode(ProtocolCodec.T_HISTORY_REQUEST,
                    Map.of("mode", "FRIEND", "userId", owner.id), "outsider"));
            assertEquals(0, history(client, "outsider").length);
        } finally {
            client.close();
        }
    }

    private ProtocolCodec.HistoryEntry[] history(TestWsClient client, String rid) {
        TestWsClient.Frame frame = client.await(ProtocolCodec.T_HISTORY_RESPONSE, 5000);
        assertNotNull(frame);
        assertEquals(rid, frame.rid);
        assertEquals(Set.of("matches"), ((Map<?, ?>) frame.data).keySet(), "response shape is unchanged");
        for (Object entry : (List<?>) ((Map<?, ?>) frame.data).get("matches")) {
            assertEquals(Set.of("matchId", "opponentUserId", "ts", "opponent", "mode", "result",
                    "myScore", "oppScore", "vsBot"), ((Map<?, ?>) entry).keySet());
        }
        return frame.as(ProtocolCodec.HistoryResponse.class).matches;
    }

    private void assertEntry(ProtocolCodec.HistoryEntry entry, MatchRecord row, boolean asA) {
        assertEquals(row.matchId, entry.matchId);
        assertEquals(row.mode, entry.mode);
        assertEquals(row.settledAt, entry.ts);
        assertEquals(asA ? row.playerB : row.playerA, entry.opponentUserId);
        assertEquals(asA ? row.nicknameB : row.nicknameA, entry.opponent);
        assertEquals(asA ? "W" : "L", entry.result);
        assertEquals(asA ? row.scoreA : row.scoreB, entry.myScore);
        assertEquals(asA ? row.scoreB : row.scoreA, entry.oppScore);
        assertEquals(row.botOpponent && asA, entry.vsBot);
    }

    private UserAccount user() {
        return users.register(UUID.randomUUID().toString(), "HistoryUser");
    }

    private TestWsClient connect(UserAccount user) throws Exception {
        TestWsClient client = new TestWsClient(URI.create("ws://localhost:" + port + "/ws"));
        client.connect();
        client.send(ProtocolCodec.T_AUTH, Map.of("token", user.token));
        assertNotNull(client.await(ProtocolCodec.T_AUTH_OK, 5000));
        return client;
    }

    private MatchRecord record(String mode, long playerA, long playerB, long ts, boolean voided) {
        MatchRecord record = new MatchRecord();
        record.matchId = UUID.randomUUID().toString();
        record.mode = mode;
        record.playerA = playerA;
        record.playerB = playerB;
        record.nicknameA = "SeatA";
        record.nicknameB = "SeatB";
        record.resultA = "W";
        record.scoreA = 30;
        record.scoreB = 10;
        record.botOpponent = "PRACTICE".equals(mode);
        record.createdAt = ts;
        record.settledAt = ts;
        record.voided = voided;
        return records.saveAndFlush(record);
    }
}
