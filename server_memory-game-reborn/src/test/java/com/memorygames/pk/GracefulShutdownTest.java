package com.memorygames.pk;

import com.memorygames.pk.match.MatchManager;
import com.memorygames.pk.protocol.ProtocolCodec;
import com.memorygames.pk.user.UserAccount;
import com.memorygames.pk.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Graceful shutdown (P1-6): voiding every live room on @PreDestroy refunds
 * the already-persisted entry fees and writes an idempotent void record,
 * so a deploy restart can no longer swallow the coins. Own context: the
 * manager's scheduler does not survive the test.
 */
@org.springframework.context.annotation.Import(PassingNicknameTestConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class GracefulShutdownTest {

    @LocalServerPort
    int port;

    @Autowired
    MatchManager manager;

    @Autowired
    UserRepository users;

    private TestWsClient newClient() throws Exception {
        TestWsClient client = new TestWsClient(URI.create("ws://127.0.0.1:" + this.port + "/ws"));
        client.connect();
        return client;
    }

    private long register(TestWsClient client, String nickname) {
        ProtocolCodec.Register register = new ProtocolCodec.Register();
        register.uuid = UUID.randomUUID().toString();
        register.nickname = nickname;
        client.send(ProtocolCodec.T_REGISTER, register);
        TestWsClient.Frame frame = client.await(ProtocolCodec.T_AUTH_OK, 5000);
        assertNotNull(frame);
        return frame.as(ProtocolCodec.AuthOk.class).userId;
    }

    @Test
    @Timeout(60)
    void shutdownVoidsLiveMatchesAndRefundsEntryFees() throws Exception {
        TestWsClient host = newClient();
        TestWsClient guest = newClient();
        try {
            long hostId = register(host, "ShutHost");
            long guestId = register(guest, "ShutGuest");

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
            assertNotNull(host.await(ProtocolCodec.T_ROUND_START, 10000));
            assertEquals(1, manager.activeRooms());

            // The entry fees (30 each) are already persisted at this point.
            assertEquals(70, users.findById(hostId).orElseThrow().balance);
            assertEquals(70, users.findById(guestId).orElseThrow().balance);

            manager.shutdown();

            assertEquals(0, manager.activeRooms(), "live rooms are voided, not leaked");
            TestWsClient.Frame end = host.await(ProtocolCodec.T_MATCH_END, 10000);
            assertNotNull(end, "players are told the match ended");
            ProtocolCodec.MatchEnd payload = end.as(ProtocolCodec.MatchEnd.class);
            assertEquals(30, payload.coinDelta, "voided match refunds the entry fee");
            assertEquals(0, payload.eloDelta, "voided match moves no ELO");

            assertEquals(100, users.findById(hostId).orElseThrow().balance,
                    "entry fee refunded to the host");
            assertEquals(100, users.findById(guestId).orElseThrow().balance,
                    "entry fee refunded to the guest");
            // W/L were never counted.
            UserAccount hostAccount = users.findById(hostId).orElseThrow();
            assertEquals(0, hostAccount.wins + hostAccount.losses + hostAccount.draws);
        } finally {
            host.close();
            guest.close();
        }
    }
}
