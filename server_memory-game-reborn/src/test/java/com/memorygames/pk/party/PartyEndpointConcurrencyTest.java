package com.memorygames.pk.party;

import com.memorygames.pk.match.PlayerEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PartyEndpointConcurrencyTest {
    @Test @Timeout(10)
    void overflowCloseNeverReentersGatewayWhilePartyLockIsHeld() throws Exception {
        WebSocketSession socket=mock(WebSocketSession.class);
        when(socket.isOpen()).thenReturn(true);when(socket.getId()).thenReturn("overflow-test");
        CountDownLatch senderEntered=new CountDownLatch(1), releaseSender=new CountDownLatch(1), closed=new CountDownLatch(1);
        Object partyMonitor=new Object();AtomicBoolean closeHeldPartyLock=new AtomicBoolean();
        doAnswer(call->{senderEntered.countDown();try { releaseSender.await(); } catch(InterruptedException ignored) { } return null;}).when(socket).sendMessage(any());
        doAnswer(call->{closeHeldPartyLock.set(Thread.holdsLock(partyMonitor));closed.countDown();return null;}).when(socket).close();
        PlayerEndpoint.WebSocket endpoint=new PlayerEndpoint.WebSocket(socket);
        try {
            endpoint.send("{}");assertTrue(senderEntered.await(2,TimeUnit.SECONDS));
            synchronized(partyMonitor) { for(int i=0;i<300;i++)endpoint.send("{}"); }
            assertTrue(closed.await(2,TimeUnit.SECONDS));assertFalse(closeHeldPartyLock.get());
        } finally {releaseSender.countDown();endpoint.shutdown();}
    }
}
