package com.memorygames.pk;

import com.memorygames.pk.match.MatchManager;
import com.memorygames.pk.match.MatchRoom;
import com.memorygames.pk.match.PlayerLifecycle;
import com.memorygames.pk.match.SettlementService;
import com.memorygames.pk.protocol.ProtocolCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/** Real gateway/admission tests with accounting deliberately held after the exit receipt. */
@Import(PassingNicknameTestConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Timeout(40)
class ResignIntegrationTest {
    @LocalServerPort int port;
    @Autowired MatchManager manager;
    @Autowired PlayerLifecycle lifecycle;
    @SpyBean SettlementService settlement;

    private TestWsClient client() throws Exception {
        TestWsClient client = new TestWsClient(URI.create("ws://127.0.0.1:" + port + "/ws"));
        client.connect();
        return client;
    }

    private ProtocolCodec.AuthOk register(TestWsClient client) {
        ProtocolCodec.Register request = new ProtocolCodec.Register();
        request.uuid = UUID.randomUUID().toString();
        client.send(ProtocolCodec.T_REGISTER, request);
        return await(client, ProtocolCodec.T_AUTH_OK, ProtocolCodec.AuthOk.class);
    }

    private <T> T await(TestWsClient client, String type, Class<T> payload) {
        TestWsClient.Frame frame = client.await(type, 5000);
        assertNotNull(frame, type + " must arrive");
        return frame.as(payload);
    }

    private ProtocolCodec.MatchFound practice(TestWsClient client, String phase) {
        client.send(ProtocolCodec.T_BOT_MATCH_REQUEST, new Object());
        ProtocolCodec.MatchFound found = await(client, ProtocolCodec.T_MATCH_FOUND, ProtocolCodec.MatchFound.class);
        if (!"ACCEPT".equals(phase)) {
            ProtocolCodec.MatchStart start = new ProtocolCodec.MatchStart();
            start.prepareRounds = "PREPARE".equals(phase);
            client.send(ProtocolCodec.T_MATCH_START, start);
            ProtocolCodec.RoundStart round = await(client, ProtocolCodec.T_ROUND_START, ProtocolCodec.RoundStart.class);
            assertEquals("PREPARE".equals(phase), round.preparing);
        }
        return found;
    }

    private void resign(TestWsClient client, String matchId) {
        client.sendRaw(ProtocolCodec.encode(ProtocolCodec.T_RESIGN, Map.of("matchId", matchId), "exit-request"));
        TestWsClient.Frame frame = client.await(ProtocolCodec.T_RESIGN_ACK, 3000);
        assertNotNull(frame, "exit must be acknowledged without waiting for accounting");
        assertEquals("exit-request", frame.rid);
        assertEquals(matchId, frame.as(ProtocolCodec.MatchRef.class).matchId);
    }

    private void waitUntil(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        assertTrue(condition.getAsBoolean());
    }

    @ParameterizedTest(name = "{0}: immediate friend admission as guest={1}")
    @CsvSource({"ACCEPT,false", "ACCEPT,true", "PREPARE,false", "PREPARE,true", "PLAY,false", "PLAY,true"})
    void activeExitFreesSeatBeforeDelayedAccountingAndOldCompletionCannotReleaseNewClaim(
            String phase, boolean asGuest) throws Exception {
        TestWsClient player = client();
        TestWsClient peer = client();
        CountDownLatch finishAccounting = new CountDownLatch(1);
        CountDownLatch accountingEntered = new CountDownLatch(1);
        String oldId = null;
        try {
            ProtocolCodec.AuthOk profile = register(player);
            register(peer);
            ProtocolCodec.MatchFound old = practice(player, phase);
            oldId = old.matchId;
            if ("PLAY".equals(phase)) {
                doAnswer(call -> {
                    SettlementService.Command command = call.getArgument(0);
                    if (old.matchId.equals(command.matchId)) {
                        accountingEntered.countDown();
                        assertTrue(finishAccounting.await(15, TimeUnit.SECONDS));
                    }
                    return call.callRealMethod();
                }).when(settlement).settle(any());
            } else {
                doAnswer(call -> {
                    if (old.matchId.equals(call.getArgument(0))) {
                        accountingEntered.countDown();
                        assertTrue(finishAccounting.await(15, TimeUnit.SECONDS));
                    }
                    return call.callRealMethod();
                }).when(settlement).abortMatch(anyString());
            }

            resign(player, old.matchId);
            assertNull(manager.roomFor(profile.userId));
            assertFalse(lifecycle.isClaimed(profile.userId));
            assertTrue(accountingEntered.await(3, TimeUnit.SECONDS));
            assertNotNull(manager.roomById(old.matchId), "old room must retain pending accounting");

            TestWsClient host = asGuest ? peer : player;
            TestWsClient guest = asGuest ? player : peer;
            host.send(ProtocolCodec.T_ROOM_CREATE, new Object());
            String code = await(host, ProtocolCodec.T_ROOM_CREATED, ProtocolCodec.RoomCreated.class).code;
            // Exercise a new non-room claim too: old completion must not release FRIEND_WAITING.
            if (!asGuest) {
                resign(player, old.matchId);
                assertEquals(PlayerLifecycle.FRIEND_WAITING, lifecycle.stateOf(profile.userId));
            }
            guest.send(ProtocolCodec.T_ROOM_JOIN, Map.of("code", code));
            ProtocolCodec.MatchFound next = await(player, ProtocolCodec.T_MATCH_FOUND, ProtocolCodec.MatchFound.class);
            await(peer, ProtocolCodec.T_MATCH_FOUND, ProtocolCodec.MatchFound.class);
            assertEquals(ProtocolCodec.MODE_FRIEND, next.mode);
            assertNotEquals(old.matchId, next.matchId);
            assertEquals(next.matchId, manager.roomFor(profile.userId).matchId);

            // A duplicate old request must receipt the OLD id, not resign the new friend room.
            resign(player, old.matchId);
            assertEquals(next.matchId, manager.roomFor(profile.userId).matchId);
            assertEquals(PlayerLifecycle.IN_MATCH, lifecycle.stateOf(profile.userId));
            finishAccounting.countDown();
            String completedId = oldId;
            waitUntil(() -> manager.roomById(completedId) == null);
            String terminalType = "PLAY".equals(phase) ? ProtocolCodec.T_MATCH_END : ProtocolCodec.T_MATCH_CANCELLED;
            assertEquals(old.matchId, await(player, terminalType, ProtocolCodec.MatchRef.class).matchId);
            assertEquals(next.matchId, manager.roomFor(profile.userId).matchId);
            assertEquals(PlayerLifecycle.IN_MATCH, lifecycle.stateOf(profile.userId));

            // Also idempotent after the old room has been removed entirely.
            resign(player, old.matchId);
            assertEquals(next.matchId, manager.roomFor(profile.userId).matchId);
            assertEquals(PlayerLifecycle.IN_MATCH, lifecycle.stateOf(profile.userId));
            resign(player, next.matchId);
            await(player, ProtocolCodec.T_MATCH_CANCELLED, ProtocolCodec.MatchRef.class);
        } finally {
            finishAccounting.countDown();
            player.close();
            peer.close();
        }
    }

    @Test
    void alreadySettlingFailedMatchStillReleasesImmediatelyAndCanHostAgain() throws Exception {
        TestWsClient player = client();
        try {
            ProtocolCodec.AuthOk profile = register(player);
            ProtocolCodec.MatchFound old = practice(player, "PLAY");
            AtomicBoolean failOnce = new AtomicBoolean(true);
            CountDownLatch failed = new CountDownLatch(1);
            doAnswer(call -> {
                SettlementService.Command command = call.getArgument(0);
                if (old.matchId.equals(command.matchId) && failOnce.getAndSet(false)) {
                    failed.countDown();
                    throw new IllegalStateException("injected settlement failure");
                }
                return call.callRealMethod();
            }).when(settlement).settle(any());

            // Trigger settlement without active departure, so settledOnce=true still owns the seat.
            MatchRoom room = manager.roomFor(profile.userId);
            assertTrue(room.adminSettle(false));
            assertTrue(failed.await(3, TimeUnit.SECONDS));
            assertNotNull(manager.roomFor(profile.userId));
            resign(player, old.matchId);
            assertNull(manager.roomFor(profile.userId));
            assertFalse(lifecycle.isClaimed(profile.userId));
            player.send(ProtocolCodec.T_ROOM_CREATE, new Object());
            await(player, ProtocolCodec.T_ROOM_CREATED, ProtocolCodec.RoomCreated.class);
            await(player, ProtocolCodec.T_MATCH_END, ProtocolCodec.MatchEnd.class);
            assertEquals(PlayerLifecycle.FRIEND_WAITING, lifecycle.stateOf(profile.userId),
                    "old retry completion cannot release a new waiting-room claim");
            player.send(ProtocolCodec.T_ROOM_CANCEL, new Object());
        } finally {
            player.close();
        }
    }

    @Test
    void abortFailureKeepsAccountingRoomButCannotBlockImmediateFriendHosting() throws Exception {
        TestWsClient player = client();
        try {
            ProtocolCodec.AuthOk profile = register(player);
            ProtocolCodec.MatchFound old = practice(player, "ACCEPT");
            AtomicBoolean failOnce = new AtomicBoolean(true);
            CountDownLatch failed = new CountDownLatch(1);
            doAnswer(call -> {
                if (old.matchId.equals(call.getArgument(0)) && failOnce.getAndSet(false)) {
                    failed.countDown();
                    throw new IllegalStateException("injected abort failure");
                }
                return call.callRealMethod();
            }).when(settlement).abortMatch(anyString());
            resign(player, old.matchId);
            assertNull(manager.roomFor(profile.userId));
            assertTrue(failed.await(3, TimeUnit.SECONDS));
            assertNotNull(manager.roomById(old.matchId), "abort failure must retain pending accounting");
            player.send(ProtocolCodec.T_ROOM_CREATE, new Object());
            await(player, ProtocolCodec.T_ROOM_CREATED, ProtocolCodec.RoomCreated.class);
            resign(player, old.matchId);
            assertEquals(PlayerLifecycle.FRIEND_WAITING, lifecycle.stateOf(profile.userId));
            assertEquals(old.matchId, await(player, ProtocolCodec.T_MATCH_CANCELLED, ProtocolCodec.MatchRef.class).matchId);
            assertEquals(PlayerLifecycle.FRIEND_WAITING, lifecycle.stateOf(profile.userId));
            waitUntil(() -> manager.roomById(old.matchId) == null);
            player.send(ProtocolCodec.T_ROOM_CANCEL, new Object());
        } finally {
            player.close();
        }
    }

    @Test
    void legacyEmptyResignIsAcknowledgedAndReleasesCurrentRoom() throws Exception {
        TestWsClient player = client();
        try {
            ProtocolCodec.AuthOk profile = register(player);
            ProtocolCodec.MatchFound old = practice(player, "ACCEPT");
            player.send(ProtocolCodec.T_RESIGN, "malformed");
            assertEquals(400, await(player, ProtocolCodec.T_ERROR, ProtocolCodec.ErrorMsg.class).code);
            assertEquals(old.matchId, manager.roomFor(profile.userId).matchId,
                    "malformed tagged data must not degrade into a legacy resign");
            player.send(ProtocolCodec.T_RESIGN, new Object());
            assertEquals(old.matchId, await(player, ProtocolCodec.T_RESIGN_ACK, ProtocolCodec.MatchRef.class).matchId);
            assertNull(manager.roomFor(profile.userId));
            assertFalse(lifecycle.isClaimed(profile.userId));
            await(player, ProtocolCodec.T_MATCH_CANCELLED, ProtocolCodec.MatchRef.class);
        } finally {
            player.close();
        }
    }
}
