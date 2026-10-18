package com.memorygames.pk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memorygames.pk.match.MatchManager;
import com.memorygames.pk.match.MatchRecord;
import com.memorygames.pk.match.MatchRecordRepository;
import com.memorygames.pk.match.SettlementService;
import com.memorygames.pk.protocol.ProtocolCodec;
import com.memorygames.pk.protocol.ProtocolCodec.MatchStats;
import com.memorygames.pk.user.AccountService;
import com.memorygames.pk.user.UserAccount;
import com.memorygames.pk.user.UserRepository;
import com.memorygames.pk.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Isolated database, real migrations, REST serialization and WebSocket delivery. */
@Import(PassingNicknameTestConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:mode-stats;DB_CLOSE_DELAY=-1",
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
        "logging.file.name=build/test-logs/mode-stats.log"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Timeout(60)
class ModeStatsIntegrationTest {
    private static final Set<String> MODES = Set.of("RANKED", "PRACTICE", "FRIEND");

    @Autowired UserService users;
    @Autowired UserRepository userRepository;
    @Autowired AccountService accounts;
    @Autowired MatchRecordRepository records;
    @Autowired SettlementService settlement;
    @Autowired MatchManager manager;
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @LocalServerPort int port;

    @Test
    void newAndMissingUsersReceiveIndependentZeroBuckets() {
        UserAccount user = user();
        assertZero(users.modeStats(user.id));
        assertZero(users.modeStats(Long.MAX_VALUE));
        Map<String, MatchStats> snapshot = users.modeStats(user.id);
        snapshot.get("RANKED").wins = 99;
        assertEquals(0, snapshot.get("PRACTICE").wins, "buckets must not share objects");
        assertZero(users.modeStats(user.id));
    }

    @Test
    void mixedModesNormalizeBothSeatsIncludingDraws() {
        UserAccount a = user(), b = user(), unrelated = user();
        record("RANKED", a.id, b.id, "W", false);
        record("RANKED", a.id, b.id, "W", false);
        record("RANKED", b.id, a.id, "L", false);
        record("RANKED", b.id, a.id, "W", false);
        record("RANKED", a.id, b.id, "D", false);
        record("RANKED", b.id, a.id, "D", false);
        record("PRACTICE", a.id, -1, "W", false);
        record("PRACTICE", -1, a.id, "W", false);
        record("PRACTICE", a.id, -1, "D", false);
        record("FRIEND", a.id, b.id, "L", false);
        record("FRIEND", b.id, a.id, "W", false);
        record("FRIEND", b.id, a.id, "D", false);
        record("RANKED", unrelated.id, -1, "W", false);

        Map<String, MatchStats> statsA = users.modeStats(a.id);
        assertBucket(statsA, "RANKED", 3, 1, 2);
        assertBucket(statsA, "PRACTICE", 1, 1, 1);
        assertBucket(statsA, "FRIEND", 0, 2, 1);
        Map<String, MatchStats> statsB = users.modeStats(b.id);
        assertBucket(statsB, "RANKED", 1, 3, 2);
        assertBucket(statsB, "PRACTICE", 0, 0, 0);
        assertBucket(statsB, "FRIEND", 2, 0, 1);
    }

    @Test
    void voidedUnknownAndNonPositiveUsersAreExcluded() {
        UserAccount user = user();
        for (String mode : MODES) {
            for (String result : Set.of("W", "L", "D")) {
                record(mode, user.id, -1, result, true);
                record(mode, 0, user.id, result, true);
            }
        }
        record("TOURNAMENT", user.id, -1, "W", false);
        record("ranked", -1, user.id, "L", false);
        record("RANKED", user.id, -1, "UNKNOWN", false);
        record("PRACTICE", -1, user.id, "UNKNOWN", false);
        assertZero(users.modeStats(user.id));

        record("RANKED", user.id, -1, "L", false);
        record("PRACTICE", 0, user.id, "W", false);
        record("FRIEND", -42, user.id, "D", false);
        for (long botId : new long[]{0, -1, -42, Long.MIN_VALUE}) {
            assertZero(users.modeStats(botId));
            assertTrue(records.aggregateModeStatsForUser(botId).isEmpty());
        }
        assertBucket(users.modeStats(user.id), "RANKED", 0, 1, 0);
        assertBucket(users.modeStats(user.id), "PRACTICE", 0, 1, 0);
        assertBucket(users.modeStats(user.id), "FRIEND", 0, 0, 1);
    }

    @Test
    void completeHistoryIsAggregatedBeyondHistoryPageLimits() {
        UserAccount user = user();
        MatchRecord oldRankedWin = record("RANKED", user.id, -1, "W", false);
        oldRankedWin.settledAt = 1;
        records.saveAndFlush(oldRankedWin);
        for (int i = 0; i < 65; i++) {
            record("PRACTICE", user.id, -1, "L", false);
        }
        var recent = records.findRecentForUser(user.id, PageRequest.of(0, 50));
        assertEquals(50, recent.size());
        assertTrue(recent.stream().allMatch(row -> "PRACTICE".equals(row.mode)));
        assertBucket(users.modeStats(user.id), "RANKED", 1, 0, 0);
        assertBucket(users.modeStats(user.id), "PRACTICE", 0, 65, 0);
        assertBucket(users.modeStats(user.id), "FRIEND", 0, 0, 0);
    }

    @Test
    void settlementAndReplaySnapshotsPreserveLegacyAggregatesAndEconomy() {
        UserAccount a = user(), b = user();
        a.wins = 9;
        a.losses = 8;
        a.draws = 7;
        userRepository.saveAndFlush(a);
        assertZero(accounts.view(userRepository.findById(a.id).orElseThrow()).modeStats());
        assertLegacy(a.id, 9, 8, 7, 1000, 100);

        SettlementService.Command ranked = command("RANKED", a, b, "W", false);
        SettlementService.Result first = settlement.settle(ranked);
        assertFalse(first.alreadySettled);
        assertBucket(first.seats[0].modeStats, "RANKED", 1, 0, 0);
        assertBucket(first.seats[1].modeStats, "RANKED", 0, 1, 0);
        assertBucket(first.seats[0].modeStats, "FRIEND", 0, 0, 0);
        assertTrue(settlement.settle(ranked).alreadySettled);

        SettlementService.Result practice = settlement.settle(command("PRACTICE", a, null, "D", false));
        assertBucket(practice.seats[0].modeStats, "RANKED", 1, 0, 0);
        assertBucket(practice.seats[0].modeStats, "PRACTICE", 0, 0, 1);
        assertZero(practice.seats[1].modeStats);
        assertEquals(4, practice.seats[0].coinDelta, "practice draw payout stays unchanged");
        SettlementService.Result friend = settlement.settle(command("FRIEND", a, b, "L", false));
        assertBucket(friend.seats[0].modeStats, "FRIEND", 0, 1, 0);
        assertLegacy(a.id, 10, 9, 8, 1008, 94);
        assertLegacy(b.id, 1, 1, 0, 992, 90);

        // An old command replay returns the CURRENT full snapshot, without another increment.
        SettlementService.Result replay = settlement.settle(ranked);
        assertTrue(replay.alreadySettled);
        assertBucket(replay.seats[0].modeStats, "RANKED", 1, 0, 0);
        assertBucket(replay.seats[0].modeStats, "PRACTICE", 0, 0, 1);
        assertBucket(replay.seats[0].modeStats, "FRIEND", 0, 1, 0);
        assertEquals(10, replay.seats[0].wins);
        assertLegacy(a.id, 10, 9, 8, 1008, 94);
        assertBucket(first.seats[0].modeStats, "PRACTICE", 0, 0, 0);
        assertEquals(1L, db.queryForObject("select count(*) from pk_match_record where match_id=?", Long.class, ranked.matchId));
    }

    @Test
    void practiceWinsStillCountAfterTheDailyRewardCap() {
        UserAccount user = user();
        SettlementService.Command capped = command("PRACTICE", user, null, "W", false);
        capped.practiceDailyCap = 0;
        SettlementService.Result first = settlement.settle(capped);
        assertEquals(0, first.seats[0].coinDelta);
        assertEquals(1, first.seats[0].practiceStreak);
        assertBucket(first.seats[0].modeStats, "PRACTICE", 1, 0, 0);
        assertLegacy(user.id, 1, 0, 0, 1000, 100);

        SettlementService.Command next = command("PRACTICE", user, null, "W", false);
        next.practiceDailyCap = 1; // The first, unpaid win still consumes the daily win allowance.
        SettlementService.Result second = settlement.settle(next);
        assertEquals(0, second.seats[0].coinDelta);
        assertEquals(2, second.seats[0].practiceStreak);
        assertBucket(second.seats[0].modeStats, "PRACTICE", 2, 0, 0);
        assertBucket(second.seats[0].modeStats, "RANKED", 0, 0, 0);
        assertBucket(second.seats[0].modeStats, "FRIEND", 0, 0, 0);
        SettlementService.Result replay = settlement.settle(capped);
        assertTrue(replay.alreadySettled);
        assertBucket(replay.seats[0].modeStats, "PRACTICE", 2, 0, 0);
        assertLegacy(user.id, 2, 0, 0, 1000, 100);
    }

    @Test
    void voidSettlementAndReplayKeepStatsUnchangedAndRefundOnlyOnce() {
        UserAccount a = user(), b = user();
        settlement.settle(command("RANKED", a, b, "W", false));
        SettlementService.Command voided = command("FRIEND", a, b, "D", true);
        SettlementService.Result first = settlement.settle(voided);
        assertBucket(first.seats[0].modeStats, "RANKED", 1, 0, 0);
        assertBucket(first.seats[0].modeStats, "FRIEND", 0, 0, 0);
        assertBucket(first.seats[1].modeStats, "RANKED", 0, 1, 0);
        assertEquals(30, first.seats[0].coinDelta);
        assertTrue(settlement.settle(voided).alreadySettled);
        assertLegacy(a.id, 1, 0, 0, 1016, 120);
        assertLegacy(b.id, 0, 1, 0, 984, 70);
        assertTrue(db.queryForObject("select voided from pk_match_record where match_id=?", Boolean.class, voided.matchId));
        assertBucket(users.modeStats(a.id), "FRIEND", 0, 0, 0);
    }

    @Test
    void restAuthMeAndRefreshSerializeAuthoritativeSnapshotsWithoutChangingLegacyFields() throws Exception {
        JsonNode guest = json.readTree(mvc.perform(post("/api/v2/auth/guest").secure(true)
                        .contentType("application/json").content(json.writeValueAsBytes(Map.of(
                                "installationId", UUID.randomUUID().toString(), "guestSecret", AccountService.randomToken(),
                                "nickname", "Stats guest", "deviceName", "Test device"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertZero(wireStats(guest.path("user")));
        long id = guest.path("user").path("userId").longValue();
        record("PRACTICE", id, -1, "W", false);
        record("FRIEND", -1, id, "L", false);
        record("RANKED", id, -1, "D", true);
        db.update("update pk_user set wins=19,losses=8,draws=4 where id=?", id);
        JsonNode me = json.readTree(mvc.perform(get("/api/v2/me").secure(true)
                        .header("Authorization", "Bearer " + guest.path("accessToken").textValue()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertBucket(wireStats(me), "PRACTICE", 1, 0, 0);
        assertBucket(wireStats(me), "FRIEND", 1, 0, 0);
        assertBucket(wireStats(me), "RANKED", 0, 0, 0);
        assertEquals(19, me.path("wins").intValue());
        assertEquals(8, me.path("losses").intValue());
        assertEquals(4, me.path("draws").intValue());
        JsonNode refreshed = json.readTree(mvc.perform(post("/api/v2/auth/refresh").secure(true)
                        .contentType("application/json").content(json.writeValueAsBytes(Map.of(
                                "refreshToken", guest.path("refreshToken").textValue()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertEquals(me.path("modeStats"), refreshed.path("user").path("modeStats"));
        assertLegacy(id, 19, 8, 4, 1000, 100);
    }

    @Test
    void realAuthOpponentAndFriendMatchEndDeliverFullPerUserSnapshots() throws Exception {
        var guest = accounts.guest(UUID.randomUUID().toString(), AccountService.randomToken(), "Stats guest", "Phone");
        long aId = guest.user().userId();
        UserAccount b = user();
        record("RANKED", aId, -1, "W", false);
        record("RANKED", -1, aId, "L", false);
        record("PRACTICE", aId, -1, "L", false);
        record("PRACTICE", b.id, -1, "W", false);
        record("FRIEND", b.id, aId, "D", false);
        db.update("update pk_user set wins=11,losses=12,draws=13 where id=?", aId);
        db.update("update pk_user set wins=21,losses=22,draws=23 where id=?", b.id);
        TestWsClient a = client(), other = client();
        try {
            ProtocolCodec.AuthOk authA = auth(a, guest.accessToken());
            ProtocolCodec.AuthOk authB = auth(other, b.token);
            assertBucket(authA.modeStats, "RANKED", 2, 0, 0);
            assertBucket(authA.modeStats, "PRACTICE", 0, 1, 0);
            assertBucket(authA.modeStats, "FRIEND", 0, 0, 1);
            assertEquals(11, authA.wins);
            assertEquals(22, authB.losses);

            a.send(ProtocolCodec.T_ROOM_CREATE, new Object());
            ProtocolCodec.RoomJoin join = new ProtocolCodec.RoomJoin();
            join.code = await(a, ProtocolCodec.T_ROOM_CREATED, ProtocolCodec.RoomCreated.class).code;
            other.send(ProtocolCodec.T_ROOM_JOIN, join);
            ProtocolCodec.MatchFound foundA = await(a, ProtocolCodec.T_MATCH_FOUND, ProtocolCodec.MatchFound.class);
            ProtocolCodec.MatchFound foundB = await(other, ProtocolCodec.T_MATCH_FOUND, ProtocolCodec.MatchFound.class);
            assertBucket(foundA.opponent.modeStats, "PRACTICE", 1, 0, 0);
            assertBucket(foundB.opponent.modeStats, "RANKED", 2, 0, 0);
            assertEquals(21, foundA.opponent.wins);
            assertEquals(12, foundB.opponent.losses);
            a.send(ProtocolCodec.T_MATCH_START, new Object());
            other.send(ProtocolCodec.T_MATCH_START, new Object());
            await(a, ProtocolCodec.T_ROUND_START, ProtocolCodec.RoundStart.class);
            await(other, ProtocolCodec.T_ROUND_START, ProtocolCodec.RoundStart.class);
            other.send(ProtocolCodec.T_RESIGN, new Object());
            ProtocolCodec.MatchEnd endA = await(a, ProtocolCodec.T_MATCH_END, ProtocolCodec.MatchEnd.class);
            ProtocolCodec.MatchEnd endB = await(other, ProtocolCodec.T_MATCH_END, ProtocolCodec.MatchEnd.class);
            assertEquals("FRIEND", endA.mode);
            assertEquals("FRIEND", endB.mode);
            assertEquals("W", endA.result);
            assertEquals("L", endB.result);
            assertBucket(endA.modeStats, "RANKED", 2, 0, 0);
            assertBucket(endA.modeStats, "PRACTICE", 0, 1, 0);
            assertBucket(endA.modeStats, "FRIEND", 1, 0, 1);
            assertBucket(endB.modeStats, "RANKED", 0, 0, 0);
            assertBucket(endB.modeStats, "PRACTICE", 1, 0, 0);
            assertBucket(endB.modeStats, "FRIEND", 0, 1, 1);
            assertEquals(12, endA.myWins);
            assertEquals(12, endA.myLosses);
            assertEquals(13, endA.myDraws);
            assertEquals(23, endB.myLosses);
            assertLegacy(aId, 12, 12, 13, 1008, 120);
            assertLegacy(b.id, 21, 23, 23, 992, 70);

            TestWsClient back = client();
            try {
                ProtocolCodec.AuthOk refreshed = auth(back, guest.accessToken());
                assertBucket(refreshed.modeStats, "FRIEND", 1, 0, 1);
                assertBucket(refreshed.modeStats, "RANKED", 2, 0, 0);
                assertEquals(12, refreshed.wins);
            } finally { back.close(); }
        } finally { a.close(); other.close(); }
    }

    @Test
    void realBotVoidPracticeAndRankedDeliveriesNeverMixModes() throws Exception {
        TestWsClient client = client();
        try {
            ProtocolCodec.Register register = new ProtocolCodec.Register();
            register.uuid = UUID.randomUUID().toString();
            register.nickname = "Bot stats";
            client.send(ProtocolCodec.T_REGISTER, register);
            ProtocolCodec.AuthOk profile = await(client, ProtocolCodec.T_AUTH_OK, ProtocolCodec.AuthOk.class);
            assertZero(profile.modeStats);

            client.send(ProtocolCodec.T_BOT_MATCH_REQUEST, new Object());
            await(client, ProtocolCodec.T_MATCH_FOUND, ProtocolCodec.MatchFound.class);
            assertTrue(manager.roomFor(profile.userId).adminTerminate());
            ProtocolCodec.MatchEnd voided = await(client, ProtocolCodec.T_MATCH_END, ProtocolCodec.MatchEnd.class);
            assertEquals("PRACTICE", voided.mode);
            assertZero(voided.modeStats);
            assertEquals(0, voided.myDraws);
            assertEquals(100, voided.balance);

            for (String mode : new String[]{"PRACTICE", "RANKED"}) {
                client.send("PRACTICE".equals(mode) ? ProtocolCodec.T_BOT_MATCH_REQUEST : ProtocolCodec.T_MATCH_JOIN, new Object());
                ProtocolCodec.MatchFound found = await(client, ProtocolCodec.T_MATCH_FOUND, ProtocolCodec.MatchFound.class);
                assertEquals(mode, found.mode);
                assertTrue(found.opponent.isBot);
                assertTrue(found.opponent.wins > 0, "legacy synthetic bot fields remain intact");
                assertZero(found.opponent.modeStats);
                client.send(ProtocolCodec.T_MATCH_START, new Object());
                await(client, ProtocolCodec.T_ROUND_START, ProtocolCodec.RoundStart.class);
                client.send(ProtocolCodec.T_RESIGN, new Object());
                ProtocolCodec.MatchEnd end = await(client, ProtocolCodec.T_MATCH_END, ProtocolCodec.MatchEnd.class);
                assertEquals(mode, end.mode);
                assertBucket(end.modeStats, "PRACTICE", 0, 1, 0);
                assertBucket(end.modeStats, "RANKED", 0, "RANKED".equals(mode) ? 1 : 0, 0);
                assertBucket(end.modeStats, "FRIEND", 0, 0, 0);
                assertEquals("RANKED".equals(mode) ? 2 : 1, end.myLosses);
            }
        } finally { client.close(); }
    }

    private UserAccount user() {
        return users.register(UUID.randomUUID().toString(), "Stats player");
    }

    private MatchRecord record(String mode, long a, long b, String result, boolean voided) {
        MatchRecord row = new MatchRecord();
        row.matchId = UUID.randomUUID().toString();
        row.mode = mode;
        row.playerA = a;
        row.playerB = b;
        row.nicknameA = "Seat A";
        row.nicknameB = "Seat B";
        row.resultA = result;
        row.createdAt = row.settledAt = System.currentTimeMillis();
        row.botOpponent = b <= 0;
        row.voided = voided;
        return records.saveAndFlush(row);
    }

    private SettlementService.Command command(String mode, UserAccount a, UserAccount b, String resultA, boolean voided) {
        SettlementService.Command cmd = new SettlementService.Command();
        cmd.matchId = UUID.randomUUID().toString();
        cmd.mode = mode;
        cmd.practice = "PRACTICE".equals(mode);
        cmd.voided = voided;
        cmd.entryCharged = !cmd.practice;
        cmd.entryFee = cmd.practice ? 0 : 30;
        cmd.createdAt = System.currentTimeMillis();
        cmd.botElo = 800;
        cmd.practiceTierWinCoins = 8;
        cmd.practiceDailyCap = 1000;
        for (int i = 0; i < 2; i++) {
            UserAccount user = i == 0 ? a : b;
            SettlementService.SeatPlan seat = new SettlementService.SeatPlan();
            seat.userId = user == null ? null : user.id;
            seat.nickname = user == null ? "Bot" : user.nickname;
            seat.result = i == 0 || "D".equals(resultA) ? resultA : "W".equals(resultA) ? "L" : "W";
            seat.win = "W".equals(seat.result);
            seat.draw = "D".equals(seat.result);
            int delta = "FRIEND".equals(mode) ? 8 : 16;
            seat.eloDelta = cmd.practice || voided || seat.draw ? 0 : seat.win ? delta : -delta;
            seat.coinDelta = cmd.practice ? seat.win || seat.draw ? 8 : 0 : seat.win ? 50 : seat.draw ? 15 : 0;
            cmd.seats[i] = seat;
        }
        settlement.openMatch(cmd.matchId, mode, a.id, b == null ? null : b.id, cmd.entryFee);
        return cmd;
    }

    private void assertLegacy(long id, int wins, int losses, int draws, int elo, int balance) {
        UserAccount user = userRepository.findById(id).orElseThrow();
        assertEquals(wins, user.wins);
        assertEquals(losses, user.losses);
        assertEquals(draws, user.draws);
        assertEquals(elo, user.elo);
        assertEquals(balance, user.balance);
    }

    private Map<String, MatchStats> wireStats(JsonNode user) {
        JsonNode stats = user.path("modeStats");
        assertEquals(3, stats.size());
        for (String mode : MODES) {
            for (String field : new String[]{"wins", "losses", "draws"}) {
                assertTrue(stats.path(mode).path(field).isIntegralNumber(), mode + "." + field);
            }
        }
        return json.convertValue(stats, json.getTypeFactory().constructMapType(Map.class, String.class, MatchStats.class));
    }

    private static void assertZero(Map<String, MatchStats> stats) {
        for (String mode : MODES) assertBucket(stats, mode, 0, 0, 0);
    }

    private static void assertBucket(Map<String, MatchStats> stats, String mode, long wins, long losses, long draws) {
        assertNotNull(stats);
        assertEquals(MODES, stats.keySet());
        MatchStats bucket = stats.get(mode);
        assertNotNull(bucket);
        assertEquals(wins, bucket.wins, mode + " wins");
        assertEquals(losses, bucket.losses, mode + " losses");
        assertEquals(draws, bucket.draws, mode + " draws");
    }

    private TestWsClient client() throws Exception {
        TestWsClient client = new TestWsClient(URI.create("ws://127.0.0.1:" + port + "/ws"));
        client.connect();
        return client;
    }

    private ProtocolCodec.AuthOk auth(TestWsClient client, String token) {
        ProtocolCodec.Auth auth = new ProtocolCodec.Auth();
        auth.token = token;
        client.send(ProtocolCodec.T_AUTH, auth);
        return await(client, ProtocolCodec.T_AUTH_OK, ProtocolCodec.AuthOk.class);
    }

    private static <T> T await(TestWsClient client, String type, Class<T> payload) {
        TestWsClient.Frame frame = client.await(type, 10000);
        assertNotNull(frame, "must receive " + type);
        return frame.as(payload);
    }
}
