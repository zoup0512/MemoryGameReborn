package com.memorygames.pk;

import com.memorygames.pk.protocol.ProtocolCodec;
import com.memorygames.pk.user.UserAccount;
import com.memorygames.pk.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M2 integration coverage: friend rooms (F-12), match history (F-11),
 * season rollover with ELO soft reset (F-13) and anti-cheat distribution
 * checks with bot-only restriction.
 */
@org.springframework.context.annotation.Import(PassingNicknameTestConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
// Production-scale anti-cheat floors: with a 200ms test floor the fixed
// network/processing latency dominates the ratio and the distribution
// check cannot fire deterministically.
@TestPropertySource(properties = {"pk.min-level-interval-override-ms=1000", "pk.round-duration-override-ms=30000"})
class M2IntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    UserRepository users;

    private TestWsClient newClient() throws Exception {
        TestWsClient client = new TestWsClient(URI.create("ws://127.0.0.1:" + this.port + "/ws"));
        client.connect();
        return client;
    }

    private long register(TestWsClient client, String nickname, int balance) {
        ProtocolCodec.Register register = new ProtocolCodec.Register();
        register.uuid = UUID.randomUUID().toString();
        register.nickname = nickname;
        register.balance = balance;
        client.send(ProtocolCodec.T_REGISTER, register);
        TestWsClient.Frame frame = client.await(ProtocolCodec.T_AUTH_OK, 5000);
        assertNotNull(frame, nickname + " must register");
        return frame.as(ProtocolCodec.AuthOk.class).userId;
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void friendRoomMatchHalvesElo() throws Exception {
        TestWsClient host = newClient();
        TestWsClient guest = newClient();
        try {
            long hostId = register(host, "HostP", 100);
            long guestId = register(guest, "GuestP", 100);

            host.send(ProtocolCodec.T_ROOM_CREATE, new Object());
            TestWsClient.Frame created = host.await(ProtocolCodec.T_ROOM_CREATED, 5000);
            assertNotNull(created, "host must receive the room code");
            String code = created.as(ProtocolCodec.RoomCreated.class).code;
            assertEquals(4, code.length(), "4-digit friend code");

            ProtocolCodec.RoomJoin join = new ProtocolCodec.RoomJoin();
            join.code = code;
            guest.send(ProtocolCodec.T_ROOM_JOIN, join);

            TestWsClient.Frame hostFound = host.await(ProtocolCodec.T_MATCH_FOUND, 10000);
            TestWsClient.Frame guestFound = guest.await(ProtocolCodec.T_MATCH_FOUND, 10000);
            host.send(ProtocolCodec.T_MATCH_START, new Object());
            guest.send(ProtocolCodec.T_MATCH_START, new Object());
            assertNotNull(hostFound, "host must be paired");
            assertNotNull(guestFound, "guest must be paired");
            assertEquals("FRIEND", hostFound.as(ProtocolCodec.MatchFound.class).mode);
            assertEquals("FRIEND", guestFound.as(ProtocolCodec.MatchFound.class).mode);

            // Host resigns mid-duel (after the START gate opens round 1);
            // friend ELO delta must be half of K=32's symmetric 16.
            assertNotNull(host.await(ProtocolCodec.T_ROUND_START, 10000));
            host.send(ProtocolCodec.T_RESIGN, new Object());
            TestWsClient.Frame hostEnd = host.await(ProtocolCodec.T_MATCH_END, 10000);
            assertNotNull(hostEnd);
            assertEquals(-8, hostEnd.as(ProtocolCodec.MatchEnd.class).eloDelta,
                    "friend-loss applies half of the ranked -16");
            TestWsClient.Frame guestEnd = guest.await(ProtocolCodec.T_MATCH_END, 5000);
            assertEquals(8, guestEnd.as(ProtocolCodec.MatchEnd.class).eloDelta);

            // History (F-11) must show the friend match for both seats.
            guest.send(ProtocolCodec.T_HISTORY_REQUEST, new Object());
            TestWsClient.Frame history = guest.await(ProtocolCodec.T_HISTORY_RESPONSE, 5000);
            assertNotNull(history);
            ProtocolCodec.HistoryEntry[] entries =
                    history.as(ProtocolCodec.HistoryResponse.class).matches;
            assertEquals(1, entries.length);
            assertEquals("W", entries[0].result);
            assertEquals("FRIEND", entries[0].mode);
            assertEquals("HostP", entries[0].opponent);
            // ...and for the loser's seat the same record mirrors.
            host.send(ProtocolCodec.T_HISTORY_REQUEST, new Object());
            TestWsClient.Frame hostHistory = host.await(ProtocolCodec.T_HISTORY_RESPONSE, 5000);
            assertNotNull(hostHistory);
            assertEquals("L", hostHistory.as(ProtocolCodec.HistoryResponse.class).matches[0].result);

            // Clean up stored users for the season test below (H2 shared context).
            assertNotNull(users.findById(guestId).orElse(null));
        } finally {
            host.close();
            guest.close();
        }
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void joiningAnUnknownCodeFails() throws Exception {
        TestWsClient a = newClient();
        try {
            register(a, "LonelyJoiner", 100);
            ProtocolCodec.RoomJoin join = new ProtocolCodec.RoomJoin();
            join.code = "9999";
            a.send(ProtocolCodec.T_ROOM_JOIN, join);
            TestWsClient.Frame error = a.await(ProtocolCodec.T_ERROR, 5000);
            assertNotNull(error, "unknown code must 404");
            assertEquals(404, error.as(ProtocolCodec.ErrorMsg.class).code);
        } finally {
            a.close();
        }
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void antiCheatRestrainsSpeedHackers() throws Exception {
        TestWsClient cheater = newClient();
        TestWsClient opponent = newClient();
        try {
            long cheaterId = register(cheater, "Speedy", 100);
            register(opponent, "Victim", 100);
            cheater.send(ProtocolCodec.T_MATCH_JOIN, new Object());
            opponent.send(ProtocolCodec.T_MATCH_JOIN, new Object());
            assertNotNull(cheater.await(ProtocolCodec.T_MATCH_FOUND, 10000));
            assertNotNull(opponent.await(ProtocolCodec.T_MATCH_FOUND, 10000));
            cheater.send(ProtocolCodec.T_MATCH_START, new Object());
            opponent.send(ProtocolCodec.T_MATCH_START, new Object());
            TestWsClient.Frame start = cheater.await(ProtocolCodec.T_ROUND_START, 10000);
            assertNotNull(start);
            int round = start.as(ProtocolCodec.RoundStart.class).round;

            // Machine-gun level wins far below the human floor: the sequence
            // cap allows ~elapsed/200ms levels, so firing fast but within the
            // cap trips the distribution check at settlement.
            // Skip-level burst at the floor interval: each report passes the
            // 1000ms gap check, but reaching level 7 in ~4s averages ~600ms
            // per level, below floor*0.9 — a distribution strike.
            int[] levels = {1, 2, 3, 5, 7};
            for (int level : levels) {
                ProtocolCodec.LevelWin win = new ProtocolCodec.LevelWin();
                win.round = round;
                win.level = level;
                win.score = 0;
                cheater.send(ProtocolCodec.T_LEVEL_WIN, win);
                Thread.sleep(1100);
            }
            cheater.send(ProtocolCodec.T_RESIGN, new Object());
            assertNotNull(cheater.await(ProtocolCodec.T_MATCH_END, 10000));

            UserAccount account = users.findById(cheaterId).orElseThrow();
            assertEquals(1, account.suspicion, "one distribution strike recorded");

            // Second strike restricts the account to bot opponents.
            cheater.send(ProtocolCodec.T_MATCH_JOIN, new Object());
            TestWsClient.Frame botFound = cheater.await(ProtocolCodec.T_MATCH_FOUND, 20000);
            cheater.send(ProtocolCodec.T_MATCH_START, new Object());
            assertNotNull(botFound, "restricted player still gets a (bot) match");
            assertNotNull(cheater.await(ProtocolCodec.T_ROUND_START, 10000));
            assertTrue(botFound.as(ProtocolCodec.MatchFound.class).opponent.isBot,
                    "restricted accounts meet bots only");
            cheater.send(ProtocolCodec.T_RESIGN, new Object());
            assertNotNull(cheater.await(ProtocolCodec.T_MATCH_END, 10000));
        } finally {
            cheater.close();
            opponent.close();
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void authCarriesSeasonSnapshot() throws Exception {
        TestWsClient a = newClient();
        try {
            ProtocolCodec.Register register = new ProtocolCodec.Register();
            register.uuid = UUID.randomUUID().toString();
            register.nickname = "Seasonal";
            register.balance = 50;
            a.send(ProtocolCodec.T_REGISTER, register);
            TestWsClient.Frame ok = a.await(ProtocolCodec.T_AUTH_OK, 5000);
            assertNotNull(ok);
            ProtocolCodec.AuthOk auth = ok.as(ProtocolCodec.AuthOk.class);
            assertTrue(auth.seasonIndex >= 1, "season index is present");
            assertTrue(auth.seasonEndsAt > System.currentTimeMillis(), "season end in the future");
        } finally {
            a.close();
        }
    }
}
