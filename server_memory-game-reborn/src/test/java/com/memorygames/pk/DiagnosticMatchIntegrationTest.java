package com.memorygames.pk;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.memorygames.pk.admin.EventRing;
import com.memorygames.pk.protocol.ProtocolCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@org.springframework.context.annotation.Import(PassingNicknameTestConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DiagnosticMatchIntegrationTest {
    @LocalServerPort int port;
    @Autowired EventRing events;

    private TestWsClient client() throws Exception {
        TestWsClient client = new TestWsClient(URI.create("ws://127.0.0.1:" + port + "/ws"));
        client.connect();
        return client;
    }

    private ProtocolCodec.AuthOk register(TestWsClient client, String nickname) {
        ProtocolCodec.Register register = new ProtocolCodec.Register();
        register.uuid = UUID.randomUUID().toString();
        register.nickname = nickname;
        client.send(ProtocolCodec.T_REGISTER, register);
        TestWsClient.Frame frame = client.await(ProtocolCodec.T_AUTH_OK, 5000);
        assertNotNull(frame);
        return frame.as(ProtocolCodec.AuthOk.class);
    }

    private EventRing.Event event(String type, String match) {
        return events.recent(1000).stream()
                .filter(e -> type.equals(e.type) && match.equals(e.matchId))
                .findFirst().orElseThrow(() -> new AssertionError("missing " + type + " match=" + match));
    }

    @Test
    @Timeout(60)
    void inviteMapsToSuccessfulMatchAndAllFiveRoundsArePersisted() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(EventRing.class);
        ListAppender<ILoggingEvent> capture = new ListAppender<>();
        capture.list = java.util.Collections.synchronizedList(new ArrayList<>());
        capture.start();
        logger.addAppender(capture);
        TestWsClient host = client();
        TestWsClient guest = client();
        TestWsClient back = null;
        try {
            ProtocolCodec.AuthOk profileA = register(host, "PrivateHostName");
            ProtocolCodec.AuthOk profileB = register(guest, "PrivateGuestName");
            host.send(ProtocolCodec.T_ROOM_CREATE, new Object());
            TestWsClient.Frame created = host.await(ProtocolCodec.T_ROOM_CREATED, 5000);
            assertNotNull(created);
            String code = created.as(ProtocolCodec.RoomCreated.class).code;
            ProtocolCodec.RoomJoin join = new ProtocolCodec.RoomJoin();
            join.code = code;
            guest.send(ProtocolCodec.T_ROOM_JOIN, join);
            TestWsClient.Frame foundA = host.await(ProtocolCodec.T_MATCH_FOUND, 10000);
            TestWsClient.Frame foundB = guest.await(ProtocolCodec.T_MATCH_FOUND, 10000);
            assertNotNull(foundA);
            assertNotNull(foundB);
            String match = foundA.as(ProtocolCodec.MatchFound.class).matchId;
            assertEquals(match, foundB.as(ProtocolCodec.MatchFound.class).matchId);
            assertEquals(5, foundA.as(ProtocolCodec.MatchFound.class).rounds.length);
            host.send(ProtocolCodec.T_MATCH_START, new Object());
            guest.send(ProtocolCodec.T_MATCH_START, new Object());
            for (int round = 1; round <= 5; round++) {
                TestWsClient.Frame startA = host.await(ProtocolCodec.T_ROUND_START, 10000);
                TestWsClient.Frame startB = guest.await(ProtocolCodec.T_ROUND_START, 10000);
                assertNotNull(startA);
                assertNotNull(startB);
                assertEquals(round, startA.as(ProtocolCodec.RoundStart.class).round);
                if (round == 1) {
                    ProtocolCodec.RoundComplete stale = new ProtocolCodec.RoundComplete();
                    stale.round = 99;
                    host.send(ProtocolCodec.T_ROUND_COMPLETE, stale);
                }
                if (round == 2) {
                    host.close();
                    assertNotNull(guest.await(ProtocolCodec.T_OPPONENT_DISCONNECTED, 5000));
                    back = client();
                    ProtocolCodec.Auth auth = new ProtocolCodec.Auth();
                    auth.token = profileA.token;
                    back.send(ProtocolCodec.T_AUTH, auth);
                    assertNotNull(back.await(ProtocolCodec.T_AUTH_OK, 5000));
                    assertNotNull(back.await(ProtocolCodec.T_ROUND_START, 5000));
                    assertNotNull(guest.await(ProtocolCodec.T_OPPONENT_RECONNECTED, 5000));
                    host = back;
                }
                ProtocolCodec.RoundComplete done = new ProtocolCodec.RoundComplete();
                done.round = round;
                host.send(ProtocolCodec.T_ROUND_COMPLETE, done);
                if (round < 5) {
                    guest.send(ProtocolCodec.T_ROUND_COMPLETE, done);
                }
                // The fifth round ends by the existing deadline, not a test-only state change.
                assertNotNull(host.await(ProtocolCodec.T_ROUND_END, 15000));
                assertNotNull(guest.await(ProtocolCodec.T_ROUND_END, 5000));
            }
            TestWsClient.Frame settledA = host.await(ProtocolCodec.T_MATCH_END, 15000);
            TestWsClient.Frame settledB = guest.await(ProtocolCodec.T_MATCH_END, 5000);
            assertNotNull(settledA);
            assertNotNull(settledB);
            assertEquals(ProtocolCodec.RESULT_DRAW, settledA.as(ProtocolCodec.MatchEnd.class).result);
            assertEquals(0, settledA.as(ProtocolCodec.MatchEnd.class).myScore);
            assertEquals(5, settledA.as(ProtocolCodec.MatchEnd.class).rounds.length);

            EventRing.Event joined = event("ROOM_JOINED", match);
            assertEquals("code=" + code + " host=" + profileA.userId + " guest=" + profileB.userId, joined.detail);
            assertTrue(events.recent(1000).stream().anyMatch(e -> e.type.equals("ROOM_CREATED")
                    && e.detail.equals("host=" + profileA.userId + " code=" + code)));
            String plan = event("MATCH_CREATED", match).detail;
            assertTrue(plan.contains("a=" + profileA.userId + " b=" + profileB.userId));
            assertTrue(plan.contains("rounds=5 plan=1:"));
            assertTrue(plan.contains(",5:"));
            assertTrue(event("ROUND_START", match).detail.contains("round=5"));
            assertTrue(event("ROUND_START", match).detail.contains("accepted=true reason=ok"));
            assertTrue(event("ROUND_COMPLETE", match).detail.contains("round=5"));
            assertTrue(events.recent(1000).stream().anyMatch(e -> match.equals(e.matchId)
                    && e.type.equals("ROUND_COMPLETE") && e.detail.contains("accepted=false reason=round_mismatch")));
            assertTrue(event("ROUND_END", match).detail.contains("round=5 forced=true reason=deadline"));
            assertTrue(events.recent(1000).stream().anyMatch(e -> match.equals(e.matchId)
                    && e.type.equals("ROUND_END") && e.detail.contains("forced=false reason=both_complete")));
            assertTrue(event("SETTLE_TRIGGER", match).detail.contains("round=5 reason=rounds_complete"));
            assertTrue(event("SETTLED", match).detail.contains("reason=rounds_complete"));
            assertTrue(event("AUTH_OK", match).detail.contains("user=" + profileA.userId + " session="));
            assertTrue(event("WS_CLOSED", match).detail.contains("closeCode="));
            assertNotNull(event("DISCONNECT", match));
            assertNotNull(event("RECONNECT", match));

            List<ILoggingEvent> logs;
            synchronized (capture.list) {
                logs = new ArrayList<>(capture.list);
            }
            assertTrue(logs.stream().anyMatch(e -> e.getLevel() == Level.INFO
                    && e.getFormattedMessage().contains("event=ROOM_JOINED match=" + match)
                    && e.getFormattedMessage().contains("code=" + code)));
            assertTrue(logs.stream().anyMatch(e -> e.getFormattedMessage().contains("event=ROUND_END match=" + match)
                    && e.getFormattedMessage().contains("round=5 forced=true reason=deadline")));
            for (ILoggingEvent log : logs) {
                String text = log.getFormattedMessage();
                assertFalse(text.contains(profileA.token));
                assertFalse(text.contains(profileB.token));
                assertFalse(text.contains("PrivateHostName"));
                assertFalse(text.contains("PrivateGuestName"));
                assertFalse(text.contains("{\""));
                assertNull(log.getThrowableProxy());
            }
        } finally {
            host.close();
            guest.close();
            if (back != null) back.close();
            logger.detachAppender(capture);
            capture.stop();
        }
    }
}
