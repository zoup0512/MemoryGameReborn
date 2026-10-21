package com.memorygames.pk;

import com.memorygames.pk.match.PlayerLifecycle;
import com.memorygames.pk.party.PartyManager;
import com.memorygames.pk.party.PartyProtocol;
import com.memorygames.pk.protocol.ProtocolCodec;
import com.memorygames.pk.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;

@Import(PassingNicknameTestConfig.class)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.datasource.url=jdbc:h2:mem:party-integration;DB_CLOSE_DELAY=-1",
        "spring.flyway.enabled=true","spring.jpa.hibernate.ddl-auto=validate",
        "pk.party.loading-ms=1000","pk.party.countdown-ms=100","pk.party.intermission-ms=100",
        "pk.party.disconnect-ms=1000","pk.party.host-transfer-ms=300",
        "pk.round-duration-override-ms=1500","pk.min-level-interval-override-ms=10"})
@ActiveProfiles("test")
@Timeout(40)
class PartyIntegrationTest {
    @LocalServerPort int port;
    @Autowired PartyManager manager;
    @Autowired PlayerLifecycle lifecycle;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @org.springframework.boot.test.mock.mockito.SpyBean com.memorygames.pk.party.PartyHistory partyHistory;
    TestWsClient client()throws Exception{TestWsClient c=new TestWsClient(URI.create("ws://127.0.0.1:"+port+"/ws"));c.connect();return c;}
    ProtocolCodec.AuthOk register(TestWsClient c){
        c.send("register",Map.of("uuid",UUID.randomUUID().toString()));
        TestWsClient.Frame f=c.await("auth_ok",5000);assertNotNull(f);return f.as(ProtocolCodec.AuthOk.class);
    }
    TestWsClient.Frame request(TestWsClient c,String type,Map<String,Object>d){
        String rid=UUID.randomUUID().toString();c.sendRaw(ProtocolCodec.encode(type,d,rid));
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        try{while(System.nanoTime()<end){TestWsClient.Frame f=c.inbox.poll(100,TimeUnit.MILLISECONDS);if(f!=null && rid.equals(f.rid))return f;}}
        catch(InterruptedException e){Thread.currentThread().interrupt();}
        fail("missing correlated reply "+type);return null;
    }
    PartyProtocol.State state(long id,Predicate<PartyProtocol.State> predicate)throws Exception{
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<end){PartyProtocol.State s=manager.stateFor(id);if(s!=null && predicate.test(s))return s;Thread.sleep(10);}
        fail("state condition not reached");return null;
    }
    Map<String,Object> ref(PartyProtocol.State s){return Map.of("lobbyId",s.lobbyId,"matchId",s.matchId,"roundIndex",s.roundIndex);}
    @Test void slowHistoryDoesNotBlockOtherAuthenticationAndRegisterReattachesParty()throws Exception{
        TestWsClient original=client(), replacement=null, other=null;
        java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1), release=new java.util.concurrent.CountDownLatch(1);
        String uuid=UUID.randomUUID().toString();
        try {
            original.send("register",Map.of("uuid",uuid));ProtocolCodec.AuthOk p=original.await("auth_ok",5000).as(ProtocolCodec.AuthOk.class);
            PartyProtocol.State lobby=request(original,"party_create",Map.of()).as(PartyProtocol.State.class);
            org.mockito.Mockito.doAnswer(call->{if(((Long)call.getArgument(0))==p.userId){entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));}return call.callRealMethod();})
                    .when(partyHistory).recent(org.mockito.ArgumentMatchers.anyLong());
            original.sendRaw(ProtocolCodec.encode("party_history",Map.of(),"slow-history"));assertTrue(entered.await(2,TimeUnit.SECONDS));
            other=client();assertNotNull(register(other));
            replacement=client();replacement.send("register",Map.of("uuid",uuid));
            assertNotNull(replacement.await("auth_ok",3000));assertNotNull(replacement.await("party_state",3000));
            release.countDown();original.close();Thread.sleep(100);
            assertTrue(manager.stateFor(p.userId).members.get(0).connected);
            assertEquals(lobby.lobbyId,manager.stateFor(p.userId).lobbyId);
            request(replacement,"party_leave",Map.of("lobbyId",lobby.lobbyId));
        } finally {release.countDown();original.close();if(replacement!=null)replacement.close();if(other!=null)other.close();}
    }
    @Test void realSocketFlywaySixPlayersIndependentHistoryAndReplacement()throws Exception{
        List<TestWsClient> clients=new ArrayList<>();List<ProtocolCodec.AuthOk> profiles=new ArrayList<>();
        try{
            for(int i=0;i<6;i++){TestWsClient c=client();clients.add(c);profiles.add(register(c));}
            long host=profiles.get(0).userId;
            PartyProtocol.State s=request(clients.get(0),"party_create",Map.of("roundCount",3)).as(PartyProtocol.State.class);
            assertTrue(s.code.matches("[0-9]{6}"));String lobby=s.lobbyId;
            for(int i=1;i<6;i++)assertEquals("party_state",request(clients.get(i),"party_join",Map.of("code",s.code)).type);
            // Legacy matchmaking cannot steal PARTY_LOBBY occupancy.
            assertEquals(409,request(clients.get(1),"bot_match_request",Map.of()).as(ProtocolCodec.ErrorMsg.class).code);
            for(int i=1;i<6;i++)request(clients.get(i),"party_ready",Map.of("lobbyId",lobby,"ready",true));
            s=request(clients.get(0),"party_start",Map.of("lobbyId",lobby)).as(PartyProtocol.State.class);String match=s.matchId;
            for(int r=0;r<3;r++){
                final int round=r;s=state(host,x->x.phase.equals("LOADING") && x.roundIndex==round);
                for(TestWsClient c:clients)assertEquals("party_state",request(c,"party_round_ready",ref(s)).type);
                s=state(host,x->x.phase.equals("PLAYING") && x.roundIndex==round);
                for(int i=0;i<2;i++){Map<String,Object>d=new HashMap<>(ref(s));d.put("level",1);d.put("success",true);d.put("score",99999999);assertEquals("party_state",request(clients.get(i),"party_level",d).type);}
                for(TestWsClient c:clients)assertEquals("party_state",request(c,"party_complete",ref(s)).type);
            }
            s=state(host,x->x.phase.equals("FINISHED"));assertEquals(6,s.participants.size());assertEquals(27,s.participants.get(0).points2);
            assertEquals(27,s.participants.get(1).points2);assertEquals(0,s.participants.get(2).points2);assertEquals(3,s.participants.get(0).firstPlaces);
            assertEquals(1,s.participants.get(0).rank);assertEquals(1,s.participants.get(1).rank);
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(jdbc.queryForObject("select count(*) from pk_party_match where match_id=?",Integer.class,match)==0 && System.nanoTime()<until)Thread.sleep(20);
            assertEquals(1,jdbc.queryForObject("select count(*) from pk_party_match where match_id=?",Integer.class,match));
            assertEquals(6,jdbc.queryForObject("select count(*) from pk_party_participant where match_id=?",Integer.class,match));
            PartyProtocol.History h=request(clients.get(0),"party_history",Map.of()).as(PartyProtocol.History.class);
            assertEquals(match,h.matches().get(0).matchId());assertEquals(27,h.matches().get(0).points2());assertEquals(1,h.matches().get(0).rank());
            assertEquals(0,jdbc.queryForObject("select count(*) from pk_match_record where player_a=? or player_b=?",Integer.class,host,host));
            for(ProtocolCodec.AuthOk p:profiles){var u=users.findById(p.userId).orElseThrow();assertEquals(p.balance,u.balance);assertEquals(p.elo,u.elo);assertEquals(p.wins,u.wins);}
            request(clients.get(0),"party_return",Map.of("lobbyId",lobby,"matchId",match));
            TestWsClient replacement=client();clients.add(replacement);replacement.send("auth",Map.of("token",profiles.get(0).token));
            assertNotNull(replacement.await("auth_ok",3000));assertNotNull(replacement.await("party_state",3000));
            clients.get(0).close();Thread.sleep(100);assertTrue(manager.stateFor(host).members.get(0).connected);
            assertEquals(PlayerLifecycle.PARTY_LOBBY,lifecycle.stateOf(host));
            assertEquals("party_left",request(replacement,"party_leave",Map.of("lobbyId",lobby,"matchId",match)).type);
            assertNull(manager.stateFor(host));assertFalse(lifecycle.isClaimed(host));
            assertNull(request(replacement,"party_sync",Map.of()).as(PartyProtocol.Left.class).lobbyId());
        }finally{for(TestWsClient c:clients)c.close();}
    }
}
