package com.memorygames.pk;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.memorygames.pk.admin.EventRing;
import com.memorygames.pk.match.PlayerEndpoint;
import com.memorygames.pk.protocol.ProtocolCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DiagnosticLoggingTest {
    private final EventRing events = new EventRing();
    private final Logger logger = (Logger) LoggerFactory.getLogger(EventRing.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Level previousLevel;

    @BeforeEach
    void capture() {
        previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
        appender.stop();
        logger.setLevel(previousLevel);
    }

    @Test
    void ringAndInfoUseTheSameBoundedRedactedFields() {
        events.emit("AUTH_OK\r\nforged", "private@example.com",
                "user=7 session=socket-7 token=secret nickname=PrivateName email=private@example.com "
                        + "frame=private@example.com reason=remote-secret {\"token\":\"secret\"}\nforged");
        EventRing.Event event = events.recent(1).get(0);
        assertEquals("UNKNOWN", event.type);
        assertEquals("unknown", event.matchId);
        assertEquals("user=7 session=socket-7", event.detail);
        assertEquals(Level.INFO, appender.list.get(0).getLevel());
        assertEquals("event=UNKNOWN match=unknown user=7 session=socket-7",
                appender.list.get(0).getFormattedMessage());
        events.emit("TEST", null, "user=7 " + "x".repeat(3000));
        assertEquals("detail=redacted", events.recent(1).get(0).detail);
        for (int i = 0; i < 1001; i++) {
            events.emit("TEST", null, "round=" + i);
        }
        assertEquals(1000, events.size());
        assertEquals("round=1000", events.recent(1).get(0).detail);
        assertEquals(1003, appender.list.size(), "eviction must not erase INFO history");
    }

    private WebSocketSession socket() {
        WebSocketSession socket = mock(WebSocketSession.class);
        when(socket.getId()).thenReturn("socket-7");
        when(socket.isOpen()).thenReturn(true);
        return socket;
    }

    private String frame() {
        return ProtocolCodec.encode(ProtocolCodec.T_MATCH_FOUND,
                Map.of("matchId", "abcdef123456", "token", "secret-token", "nickname", "PrivateName",
                        "email", "private@example.com"));
    }

    private void assertSafe(String type) {
        EventRing.Event event = events.recent(20).stream()
                .filter(e -> type.equals(e.type)).findFirst().orElseThrow();
        assertEquals("abcdef123456", event.matchId);
        assertTrue(event.detail.contains("session=socket-7 frame=match_found"));
        assertTrue(event.detail.contains("class="));
        for (ILoggingEvent log : appender.list) {
            String message = log.getFormattedMessage();
            assertFalse(message.contains("secret-token"));
            assertFalse(message.contains("PrivateName"));
            assertFalse(message.contains("private@example.com"));
            assertFalse(message.contains("sensitive-exception"));
            assertFalse(message.contains("{\""));
            assertNull(log.getThrowableProxy());
        }
    }

    @Test
    @Timeout(10)
    void sendFailureContainsMetadataAndExceptionClassOnly() throws Exception {
        WebSocketSession socket = socket();
        CountDownLatch closed = new CountDownLatch(1);
        doThrow(new IOException("sensitive-exception private@example.com")).when(socket).sendMessage(any());
        doAnswer(call -> { closed.countDown(); return null; }).when(socket).close();
        PlayerEndpoint.WebSocket endpoint = new PlayerEndpoint.WebSocket(socket, null, events, () -> null);
        try {
            endpoint.send(frame());
            assertTrue(closed.await(5, TimeUnit.SECONDS));
            assertSafe("SEND_FAILED");
            assertTrue(events.recent(1).get(0).detail.contains("class=java.io.IOException"));
        } finally {
            endpoint.shutdown();
        }
    }

    @Test
    @Timeout(10)
    void sendAndCloseFailureIsLoggedWithoutCloseReason() throws Exception {
        WebSocketSession socket = socket();
        CountDownLatch closed = new CountDownLatch(1);
        doThrow(new IOException("sensitive-exception")).when(socket).sendMessage(any());
        doAnswer(call -> { closed.countDown(); return null; }).when(socket).close(any(CloseStatus.class));
        PlayerEndpoint.WebSocket endpoint = new PlayerEndpoint.WebSocket(socket, null, events, () -> null);
        try {
            endpoint.sendAndClose(frame(), new CloseStatus(1008, "private@example.com"));
            assertTrue(closed.await(5, TimeUnit.SECONDS));
            assertSafe("SEND_FAILED");
        } finally {
            endpoint.shutdown();
        }
    }

    @Test
    void socketTransportKickAndCloseNeverLogRemoteReasons() throws Exception {
        com.memorygames.pk.ws.PkWebSocketHandler handler = new com.memorygames.pk.ws.PkWebSocketHandler(
                mock(com.memorygames.pk.user.UserService.class),
                mock(com.memorygames.pk.match.Matchmaker.class),
                mock(com.memorygames.pk.match.MatchManager.class),
                mock(com.memorygames.pk.match.PlayerLifecycle.class),
                mock(com.memorygames.pk.rating.Rating.class),
                new com.memorygames.pk.config.PkProperties(),
                mock(com.memorygames.pk.match.MatchRecordRepository.class),
                mock(com.memorygames.pk.season.SeasonService.class),
                mock(com.memorygames.pk.admin.AdminState.class), events,
                new com.memorygames.pk.admin.SessionStats(),
                new com.memorygames.pk.admin.AdminMetrics(),
                mock(com.memorygames.pk.user.AccountService.class));
        WebSocketSession socket = socket();
        handler.afterConnectionEstablished(socket);
        @SuppressWarnings("unchecked")
        Map<String, Long> authenticated = (Map<String, Long>)
                org.springframework.test.util.ReflectionTestUtils.getField(handler, "authenticated");
        assertNotNull(authenticated);
        authenticated.put(socket.getId(), 7L);
        handler.handleTransportError(socket, new IOException("sensitive-exception private@example.com"));
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(handler,
                "kickSession", socket.getId(), "session replaced");
        handler.afterConnectionClosed(socket, new CloseStatus(1008, "private@example.com\nremote-secret"));
        EventRing.Event closed = events.recent(1).get(0);
        assertEquals("WS_CLOSED", closed.type);
        assertEquals("user=7 session=socket-7 closeCode=1008", closed.detail);
        assertTrue(events.recent(10).stream().anyMatch(e -> e.type.equals("WS_KICK")
                && e.detail.contains("reason=session_replaced closeCode=1008")));
        assertTrue(events.recent(10).stream().anyMatch(e -> e.type.equals("WS_TRANSPORT_ERROR")
                && e.detail.contains("user=7 session=socket-7 class=java.io.IOException")));
        for (ILoggingEvent log : appender.list) {
            assertFalse(log.getFormattedMessage().contains("private@example.com"));
            assertFalse(log.getFormattedMessage().contains("remote-secret"));
            assertFalse(log.getFormattedMessage().contains("sensitive-exception"));
            assertNull(log.getThrowableProxy());
        }
    }

    @Test
    @Timeout(10)
    void blockedSenderOverflowContainsSessionFrameAndMatch() throws Exception {
        WebSocketSession socket = socket();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger overflows = new AtomicInteger();
        doAnswer(call -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return null;
        }).when(socket).sendMessage(any());
        PlayerEndpoint.WebSocket endpoint = new PlayerEndpoint.WebSocket(socket,
                overflows::incrementAndGet, events, () -> "abcdef123456");
        try {
            endpoint.send(frame());
            assertTrue(started.await(5, TimeUnit.SECONDS));
            for (int i = 0; i < 257; i++) {
                endpoint.send(frame());
            }
            assertEquals(1, overflows.get());
            assertSafe("SEND_OVERFLOW");
            verify(socket).close();
        } finally {
            release.countDown();
            endpoint.shutdown();
        }
    }
}
