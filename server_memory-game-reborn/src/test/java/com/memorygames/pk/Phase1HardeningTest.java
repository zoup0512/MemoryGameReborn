package com.memorygames.pk;

import com.memorygames.pk.protocol.ProtocolCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase-1 hardening regression tests (P1 set): server-authoritative
 * registration balance, single-flight matchmaking entries (no forked
 * matches from a queued account), reconnect round timers reflecting the
 * REMAINING time, and late level_wins never scoring.
 */
@org.springframework.context.annotation.Import(PassingNicknameTestConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
// A long bot wait keeps the queued account deterministically QUEUED (no
// fallback race) while the 409 paths are probed.
@TestPropertySource(properties = "pk.timing.bot-wait-ms=60000")
class Phase1HardeningTest {

    @LocalServerPort
    int port;

    private TestWsClient newClient() throws Exception {
        TestWsClient client = new TestWsClient(URI.create("ws://127.0.0.1:" + this.port + "/ws"));
        client.connect();
        return client;
    }

    private ProtocolCodec.AuthOk register(TestWsClient client, String nickname, int claimedBalance) {
        ProtocolCodec.Register register = new ProtocolCodec.Register();
        register.uuid = UUID.randomUUID().toString();
        register.nickname = nickname;
        register.balance = claimedBalance;
        client.send(ProtocolCodec.T_REGISTER, register);
        TestWsClient.Frame frame = client.await(ProtocolCodec.T_AUTH_OK, 5000);
        assertNotNull(frame, nickname + " must register");
        return frame.as(ProtocolCodec.AuthOk.class);
    }

    @Test
    @Timeout(30)
    void registerIgnoresClientClaimedBalance() throws Exception {
        TestWsClient a = newClient();
        try {
            ProtocolCodec.AuthOk profile = register(a, "Rich", 99999);
            assertEquals(100, profile.balance,
                    "a fresh account starts at the welcome grant, not the claimed balance");
        } finally {
            a.close();
        }
    }

    @Test
    @Timeout(30)
    void queuedAccountCannotForkIntoOtherMatchPaths() throws Exception {
        TestWsClient a = newClient();
        try {
            register(a, "Forky", 100);

            a.send(ProtocolCodec.T_MATCH_JOIN, new Object());
            assertNotNull(a.await(ProtocolCodec.T_MATCH_QUEUED, 5000), "join must be receipted");

            // While QUEUED every other entry path must refuse (P1-2).
            a.send(ProtocolCodec.T_ROOM_CREATE, new Object());
            TestWsClient.Frame roomErr = a.await(ProtocolCodec.T_ERROR, 5000);
            assertNotNull(roomErr, "room_create while queued must fail");
            assertEquals(409, roomErr.as(ProtocolCodec.ErrorMsg.class).code);

            a.send(ProtocolCodec.T_BOT_MATCH_REQUEST, new Object());
            TestWsClient.Frame botErr = a.await(ProtocolCodec.T_ERROR, 5000);
            assertNotNull(botErr, "bot_match_request while queued must fail");
            assertEquals(409, botErr.as(ProtocolCodec.ErrorMsg.class).code);

            ProtocolCodec.RoomJoin join = new ProtocolCodec.RoomJoin();
            join.code = "1234";
            a.send(ProtocolCodec.T_ROOM_JOIN, join);
            TestWsClient.Frame joinErr = a.await(ProtocolCodec.T_ERROR, 5000);
            assertNotNull(joinErr, "room_join while queued must fail");
            assertEquals(409, joinErr.as(ProtocolCodec.ErrorMsg.class).code);

            // A duplicate match_join stays an idempotent no-op receipt.
            a.send(ProtocolCodec.T_MATCH_JOIN, new Object());
            assertNotNull(a.await(ProtocolCodec.T_MATCH_QUEUED, 5000));
            assertNull(a.await(ProtocolCodec.T_MATCH_FOUND, 1500),
                    "no second match may appear for the queued account");

            // Cancelling frees the claim: hosting works again.
            a.send(ProtocolCodec.T_MATCH_CANCEL, new Object());
            assertNotNull(a.await(ProtocolCodec.T_MATCH_CANCELLED, 5000));
            a.send(ProtocolCodec.T_ROOM_CREATE, new Object());
            TestWsClient.Frame created = a.await(ProtocolCodec.T_ROOM_CREATED, 5000);
            assertNotNull(created, "after cancel the account can host again");
        } finally {
            a.close();
        }
    }

    @Test
    @Timeout(60)
    void reconnectReceivesRemainingRoundTime() throws Exception {
        TestWsClient host = newClient();
        TestWsClient guest = newClient();
        try {
            ProtocolCodec.AuthOk hostProfile = register(host, "ReHost", 100);
            register(guest, "ReGuest", 100);

            host.send(ProtocolCodec.T_ROOM_CREATE, new Object());
            TestWsClient.Frame created = host.await(ProtocolCodec.T_ROOM_CREATED, 5000);
            assertNotNull(created);
            ProtocolCodec.RoomJoin join = new ProtocolCodec.RoomJoin();
            join.code = created.as(ProtocolCodec.RoomCreated.class).code;
            guest.send(ProtocolCodec.T_ROOM_JOIN, join);
            assertNotNull(host.await(ProtocolCodec.T_MATCH_FOUND, 10000));
            assertNotNull(guest.await(ProtocolCodec.T_MATCH_FOUND, 10000));
            host.send(ProtocolCodec.T_MATCH_START, new Object());
            guest.send(ProtocolCodec.T_MATCH_START, new Object());
            TestWsClient.Frame start = host.await(ProtocolCodec.T_ROUND_START, 10000);
            assertNotNull(start, "round 1 must start for the host");
            assertEquals(2000, start.as(ProtocolCodec.RoundStart.class).durationMs);

            // Drop the host ~700ms into the 2000ms round and reconnect.
            Thread.sleep(700);
            host.close();
            Thread.sleep(100);
            TestWsClient back = newClient();
            try {
                ProtocolCodec.Auth auth = new ProtocolCodec.Auth();
                auth.token = hostProfile.token;
                back.send(ProtocolCodec.T_AUTH, auth);
                assertNotNull(back.await(ProtocolCodec.T_AUTH_OK, 5000), "token re-auth works");
                TestWsClient.Frame resumed = back.await(ProtocolCodec.T_ROUND_START, 3000);
                assertNotNull(resumed, "reconnect must resume the live round");
                int remaining = resumed.as(ProtocolCodec.RoundStart.class).durationMs;
                assertTrue(remaining > 100 && remaining < 1600,
                        "resumed duration is the remaining time, not the full 2000ms: " + remaining);
                assertEquals(1, resumed.as(ProtocolCodec.RoundStart.class).round);

                back.send(ProtocolCodec.T_RESIGN, new Object());
                assertNotNull(back.await(ProtocolCodec.T_MATCH_END, 10000));
            } finally {
                back.close();
            }
            assertNotNull(guest.await(ProtocolCodec.T_MATCH_END, 10000));
        } finally {
            host.close();
            guest.close();
        }
    }
}

/** Register imports no balance (P1-3), so welcome-coins=0 means 402 on join. */
@org.springframework.context.annotation.Import(PassingNicknameTestConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "pk.economy.welcome-coins=0")
class InsufficientBalanceTest {

    @LocalServerPort
    int port;

    @Test
    @Timeout(30)
    void queueingWithoutCoinsIsRejected() throws Exception {
        TestWsClient a = new TestWsClient(URI.create("ws://127.0.0.1:" + this.port + "/ws"));
        try {
            a.connect();
            ProtocolCodec.Register register = new ProtocolCodec.Register();
            register.uuid = UUID.randomUUID().toString();
            register.nickname = "Broke";
            register.balance = 1000; // ignored: welcome grant is 0 in this context
            a.send(ProtocolCodec.T_REGISTER, register);
            assertNotNull(a.await(ProtocolCodec.T_AUTH_OK, 5000));
            a.send(ProtocolCodec.T_MATCH_JOIN, new Object());
            TestWsClient.Frame error = a.await(ProtocolCodec.T_ERROR, 5000);
            assertNotNull(error, "queueing without coins must error");
            assertEquals(402, error.as(ProtocolCodec.ErrorMsg.class).code);
            assertNull(a.await(ProtocolCodec.T_MATCH_FOUND, 2500));
        } finally {
            a.close();
        }
    }
}

/** Late level_wins (past duration + tolerance) must never score (P1-5). */
@org.springframework.context.annotation.Import(PassingNicknameTestConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "pk.timing.score-accept-grace-ms=200")
class LateScoreRejectionTest {

    @LocalServerPort
    int port;

    @Test
    @Timeout(60)
    void levelWinAfterDeadlineIsNotScored() throws Exception {
        TestWsClient host = new TestWsClient(URI.create("ws://127.0.0.1:" + this.port + "/ws"));
        TestWsClient guest = new TestWsClient(URI.create("ws://127.0.0.1:" + this.port + "/ws"));
        try {
            host.connect();
            guest.connect();
            for (TestWsClient client : new TestWsClient[]{host, guest}) {
                ProtocolCodec.Register register = new ProtocolCodec.Register();
                register.uuid = UUID.randomUUID().toString();
                register.nickname = client == host ? "LateHost" : "LateGuest";
                client.send(ProtocolCodec.T_REGISTER, register);
                assertNotNull(client.await(ProtocolCodec.T_AUTH_OK, 5000));
            }
            host.send(ProtocolCodec.T_ROOM_CREATE, new Object());
            TestWsClient.Frame created = host.await(ProtocolCodec.T_ROOM_CREATED, 5000);
            assertNotNull(created);
            ProtocolCodec.RoomJoin join = new ProtocolCodec.RoomJoin();
            join.code = created.as(ProtocolCodec.RoomCreated.class).code;
            guest.send(ProtocolCodec.T_ROOM_JOIN, join);
            assertNotNull(host.await(ProtocolCodec.T_MATCH_FOUND, 10000));
            assertNotNull(guest.await(ProtocolCodec.T_MATCH_FOUND, 10000));
            host.send(ProtocolCodec.T_MATCH_START, new Object());
            guest.send(ProtocolCodec.T_MATCH_START, new Object());
            TestWsClient.Frame start = host.await(ProtocolCodec.T_ROUND_START, 10000);
            assertNotNull(start);
            int round = start.as(ProtocolCodec.RoundStart.class).round;

            // Round is 2000ms; the 200ms tolerance closes scoring at 2200ms.
            Thread.sleep(2500);
            ProtocolCodec.LevelWin win = new ProtocolCodec.LevelWin();
            win.round = round;
            win.level = 1;
            win.score = 0;
            host.send(ProtocolCodec.T_LEVEL_WIN, win);

            assertNull(guest.await(ProtocolCodec.T_OPPONENT_SCORE, 1200),
                    "a late level_win must not relay a score");

            host.send(ProtocolCodec.T_RESIGN, new Object());
            TestWsClient.Frame end = host.await(ProtocolCodec.T_MATCH_END, 15000);
            assertNotNull(end);
            assertEquals(0, end.as(ProtocolCodec.MatchEnd.class).myScore,
                    "the rejected level never entered the settlement");
        } finally {
            host.close();
            guest.close();
        }
    }
}
