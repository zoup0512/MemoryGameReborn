package com.memorygames.pk.ws;

import com.memorygames.pk.admin.*;
import com.memorygames.pk.config.PkProperties;
import com.memorygames.pk.match.*;
import com.memorygames.pk.party.PartyManager;
import com.memorygames.pk.protocol.ProtocolCodec;
import com.memorygames.pk.rating.Rating;
import com.memorygames.pk.season.SeasonService;
import com.memorygames.pk.user.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CloseReaperTest {
    @Test @Timeout(15)
    @SuppressWarnings("unchecked")
    void saturatedCloserDetachesRejectsInboundAndEventuallyClosesEveryPendingEndpoint() throws Exception {
        ThreadPoolExecutor closer=(ThreadPoolExecutor)ReflectionTestUtils.getField(PlayerEndpoint.WebSocket.class,"CLOSER");
        assertNotNull(closer);
        CountDownLatch release=new CountDownLatch(1), workersStarted=new CountDownLatch(2);
        Runnable blocking=()->{workersStarted.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}};
        UserService users=mock(UserService.class); PartyManager parties=mock(PartyManager.class);
        PkWebSocketHandler gateway=new PkWebSocketHandler(users,mock(Matchmaker.class),mock(MatchManager.class),
                new PlayerLifecycle(),mock(Rating.class),new PkProperties(),mock(MatchRecordRepository.class),
                mock(SeasonService.class),new AdminState(),new EventRing(),new SessionStats(),new AdminMetrics(),
                mock(AccountService.class),parties);
        List<WebSocketSession> sockets=new ArrayList<>();List<PlayerEndpoint.WebSocket> endpoints=new ArrayList<>();
        CountDownLatch closed=new CountDownLatch(3);AtomicInteger closeCalls=new AtomicInteger();
        try {
            // One task owns each worker; every queue slot is occupied. No socket
            // threads or unbounded pending collection are needed to simulate overload.
            closer.execute(blocking);
            while(closer.getQueue().remainingCapacity()>0)closer.execute(blocking);
            if(closer.getActiveCount()<2)closer.execute(blocking);
            assertTrue(workersStarted.await(2,TimeUnit.SECONDS));
            while(closer.getQueue().remainingCapacity()>0)closer.execute(blocking);
            for(int i=0;i<3;i++) {
                WebSocketSession socket=mock(WebSocketSession.class);String id="saturated-"+i;long user=1000L+i;
                when(socket.getId()).thenReturn(id);when(socket.isOpen()).thenReturn(true);
                doAnswer(call->{closeCalls.incrementAndGet();gateway.afterConnectionClosed(socket,org.springframework.web.socket.CloseStatus.NORMAL);closed.countDown();return null;}).when(socket).close();
                gateway.afterConnectionEstablished(socket);
                Map<String,Long> authenticated=(Map<String,Long>)ReflectionTestUtils.getField(gateway,"authenticated");
                Map<Long,String> controllers=(Map<Long,String>)ReflectionTestUtils.getField(gateway,"sessionByUser");
                authenticated.put(id,user);controllers.put(user,id);
                Map<String,PlayerEndpoint.WebSocket> registry=(Map<String,PlayerEndpoint.WebSocket>)ReflectionTestUtils.getField(gateway,"endpoints");
                PlayerEndpoint.WebSocket endpoint=registry.get(id);endpoints.add(endpoint);sockets.add(socket);
                ReflectionTestUtils.invokeMethod(endpoint,"closeAsynchronously","{}");
                assertTrue(endpoint.hasPendingClose());
                assertTrue(endpoint.isClosing());
            }
            assertEquals(0,closeCalls.get(),"pool saturation must never close inline");
            // Even before a background scan, malicious continued inbound register
            // is detached and cannot authenticate the closing transport again.
            gateway.handleTextMessage(sockets.get(0),new TextMessage(ProtocolCodec.encode("register",Map.of("uuid","blocked"))));
            verifyNoInteractions(users);
            gateway.startCloseReaper();
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
            Map<String,Long> authenticated=(Map<String,Long>)ReflectionTestUtils.getField(gateway,"authenticated");
            while(!authenticated.isEmpty() && System.nanoTime()<until)Thread.sleep(10);
            assertTrue(authenticated.isEmpty(),"logical authentication must be removed while physical close is blocked");
            for(int i=0;i<3;i++)verify(parties).disconnect(1000L+i);
            release.countDown();
            assertTrue(closed.await(5,TimeUnit.SECONDS),"all rejected close submissions must be retried after pool recovery");
            assertEquals(3,closeCalls.get());
            Map<?,?> registry=(Map<?,?>)ReflectionTestUtils.getField(gateway,"endpoints");assertTrue(registry.isEmpty());
            until=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
            while(endpoints.stream().anyMatch(PlayerEndpoint.WebSocket::hasPendingClose) && System.nanoTime()<until)Thread.sleep(5);
            for(PlayerEndpoint.WebSocket endpoint:endpoints)assertFalse(endpoint.hasPendingClose());
        } finally {
            release.countDown();gateway.stopCloseReaper();for(PlayerEndpoint.WebSocket endpoint:endpoints)endpoint.shutdown();
        }
    }
}
