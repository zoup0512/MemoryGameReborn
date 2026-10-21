package com.memorygames.pk.party;

import com.memorygames.pk.admin.AdminState;
import com.memorygames.pk.config.PkProperties;
import com.memorygames.pk.game.GameCatalog;
import com.memorygames.pk.match.PlayerEndpoint;
import com.memorygames.pk.match.PlayerLifecycle;
import com.memorygames.pk.protocol.ProtocolCodec;
import com.memorygames.pk.user.UserAccount;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PartyManagerTest {
    static class MutableClock extends Clock {
        final AtomicLong now=new AtomicLong(1000000);
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId z){return this;}
        public Instant instant(){return Instant.ofEpochMilli(millis());}
        public long millis(){return now.get();}
    }
    static class Endpoint implements PlayerEndpoint {
        final List<ProtocolCodec.Envelope> messages=new CopyOnWriteArrayList<>();
        public void send(String json){messages.add(ProtocolCodec.parse(json));}
        public boolean isOpen(){return true;}
        ProtocolCodec.Envelope last(){return messages.get(messages.size()-1);}
    }
    final MutableClock clock=new MutableClock();
    final PlayerLifecycle lifecycle=new PlayerLifecycle();
    final PartyHistory history=mock(PartyHistory.class);
    final AdminState admin=new AdminState();
    final PkProperties properties=new PkProperties();
    final GameCatalog catalog;
    final PartyManager manager;
    final Map<Long,UserAccount> users=new HashMap<>();
    final Map<Long,Endpoint> endpoints=new HashMap<>();
    PartyManagerTest(){
        properties.setRoundDurationOverrideMs(1000); properties.setMinLevelIntervalOverrideMs(10);
        catalog=new GameCatalog(properties);
        manager=new PartyManager(lifecycle,catalog,history,admin,clock,new PartyManager.Timing(150,30,60,900,300,18000,10),false);
    }
    @AfterEach void close(){manager.shutdown();}
    void player(long id){ UserAccount u=new UserAccount(); u.id=id; u.nickname="P"+id; u.avatarValue="a"; users.put(id,u); endpoints.put(id,new Endpoint()); }
    void cmd(long id,String type,Map<String,Object> data){manager.command(users.get(id),endpoints.get(id),"test",type,data,"r");}
    PartyProtocol.State state(){return manager.stateFor(1);}
    PartyProtocol.State lobby(int count){
        for(long i=1;i<=count;i++) player(i);
        cmd(1,"party_create",Map.of("roundCount",3));
        String code=state().code;
        for(long i=2;i<=count;i++) cmd(i,"party_join",Map.of("code",code));
        return state();
    }
    void readyAndStart(){for(long id:users.keySet()) if(id!=1) cmd(id,"party_ready",Map.of("lobbyId",state().lobbyId,"ready",true)); cmd(1,"party_start",Map.of("lobbyId",state().lobbyId));}
    Map<String,Object> ref(){PartyProtocol.State s=state();return Map.of("lobbyId",s.lobbyId,"matchId",s.matchId,"roundIndex",s.roundIndex);}
    void advance(long ms){clock.now.addAndGet(ms);manager.tick();}
    void loadAll(){for(long id:users.keySet())cmd(id,"party_round_ready",ref());advance(30);assertEquals("PLAYING",state().phase);}
    void level(long id,int level,boolean success){Map<String,Object> d=new HashMap<>(ref());d.put("level",level);d.put("success",success);cmd(id,"party_level",d);}
    int error(long id){return ProtocolCodec.convert(endpoints.get(id).last().d,ProtocolCodec.ErrorMsg.class).code;}

    @ParameterizedTest @ValueSource(ints={3,4,6})
    void tiesZeroFrozenNHistoryAndContinuousLobby(int n){
        lobby(n); readyAndStart(); String match=state().matchId;
        long version=state().stateVersion;cmd(1,"party_start",Map.of("lobbyId",state().lobbyId));assertEquals(version,state().stateVersion);
        for(int r=0;r<3;r++){
            loadAll();level(1,1,true);level(2,1,true);
            // No-score participants are eligible but must earn zero.
            for(long id:users.keySet())cmd(id,"party_complete",ref());
            PartyProtocol.State s=state();assertEquals(r+1,s.results.size());
            assertEquals(2*n-3,s.results.get(r).entries.get(0).points2);
            assertEquals(0,s.results.get(r).entries.get(2).points2);
            if(r<2)advance(60);
        }
        assertEquals("FINISHED",state().phase);assertEquals("COMPLETED",state().status);
        assertEquals(1,state().participants.get(0).rank);assertEquals(1,state().participants.get(1).rank);
        assertEquals(3*(2*n-3),state().participants.get(0).points2);
        manager.flushHistory();verify(history).save(any());
        String lobbyId=state().lobbyId;
        cmd(1,"party_return",Map.of("lobbyId",lobbyId,"matchId",match));
        cmd(2,"party_ready",Map.of("lobbyId",lobbyId,"ready",true));
        cmd(1,"party_return",Map.of("lobbyId",lobbyId,"matchId",match));
        assertTrue(state().members.get(1).ready,"return retry must not clear successor preparation");
        readyAndStart();assertNotEquals(match,state().matchId);
        cmd(2,"party_leave",Map.of("lobbyId",lobbyId,"matchId",match));assertEquals(409,error(2));
        assertNotNull(manager.stateFor(2));
    }
    @Test void barrierTimeoutExcludesLateLoaderAndNoChampionOnInterruption(){
        lobby(3);readyAndStart();
        cmd(1,"party_round_ready",ref());advance(150);
        assertEquals("FINISHED",state().phase);assertEquals("loading_failed",state().reason);
        assertTrue(state().participants.stream().allMatch(p->p.rank==0));
        cmd(1,"party_return",Map.of("lobbyId",state().lobbyId,"matchId",state().matchId));readyAndStart();
        cmd(1,"party_round_ready",ref());cmd(2,"party_round_ready",ref());advance(150);
        assertEquals("COUNTDOWN",state().phase);
        cmd(3,"party_round_ready",ref());assertEquals(409,error(3));
        level(1,1,true);assertEquals(409,error(1));advance(30);
        level(3,1,true);assertEquals(409,error(3));
        cmd(2,"party_leave",Map.of("lobbyId",state().lobbyId,"matchId",state().matchId));
        cmd(3,"party_leave",Map.of("lobbyId",state().lobbyId,"matchId",state().matchId));
        assertEquals("INTERRUPTED",state().status);assertTrue(state().participants.stream().allMatch(p->p.rank==0));
    }
    @Test void levelsRejectSpeedDuplicatesTypesStaleAndExtraTime(){
        lobby(3);readyAndStart();loadAll();
        level(1,1,false);assertEquals(0,state().participants.get(0).score);
        level(1,1,true);assertEquals(409,error(1));level(1,2,true);assertEquals(409,error(1));
        advance(2010); // deadlines end round; no score accepted by stale clients.
        level(1,3,true);assertEquals(409,error(1));
        advance(60);loadAll();level(1,100000,true);assertEquals(409,error(1));
        Map<String,Object>d=new HashMap<>(ref());d.put("level",1);d.put("success","true");cmd(1,"party_level",d);assertEquals(400,error(1));
        level(1,1,true);assertTrue(state().participants.get(0).score>0);
        level(1,3,true);assertEquals(409,error(1));
        advance(10);level(1,3,true);assertEquals("party_state",endpoints.get(1L).last().t);
        Map<String,Object> stale=new HashMap<>(ref());stale.put("roundIndex",0);cmd(1,"party_complete",stale);assertEquals(409,error(1));
    }
    @Test void disconnectedHostTransferGraceReconnectAndOwnedClaims(){
        lobby(3);String id=state().lobbyId;
        manager.disconnect(1);advance(300);assertEquals(2,manager.stateFor(2).hostId);
        manager.reconnect(1,endpoints.get(1L));advance(600);assertNotNull(manager.stateFor(1));
        manager.disconnect(1);advance(900);assertNull(manager.stateFor(1));
        cmd(1,"party_create",Map.of());String next=state().lobbyId;assertNotEquals(id,next);
        lifecycle.releaseOwned(1,PlayerLifecycle.PARTY_LOBBY,id);lifecycle.release(1);lifecycle.releaseIf(1,PlayerLifecycle.PARTY_LOBBY);
        assertEquals(PlayerLifecycle.PARTY_LOBBY,lifecycle.stateOf(1));
        cmd(1,"party_leave",Map.of("lobbyId",id));assertEquals(next,state().lobbyId);
        advance(500);assertEquals(next,state().lobbyId);
    }
    @Test void selfKickUsesNumericIdentityOutsideLongCache(){
        player(1000);cmd(1000,"party_create",Map.of());String lobby=manager.stateFor(1000).lobbyId;
        cmd(1000,"party_kick",Map.of("lobbyId",lobby,"playerId",1000L));
        assertEquals(400,error(1000));assertNotNull(manager.stateFor(1000));
        assertEquals(PlayerLifecycle.PARTY_LOBBY,lifecycle.stateOf(1000));
    }
    @Test void transferKickReadyConfigMaintenanceAndIdle(){
        lobby(3);String id=state().lobbyId;
        cmd(2,"party_transfer",Map.of("lobbyId",id,"playerId",3));assertEquals(403,error(2));
        cmd(2,"party_ready",Map.of("lobbyId",id,"ready",true));
        cmd(1,"party_config",Map.of("lobbyId",id,"roundCount",5));assertFalse(state().members.get(1).ready);
        cmd(1,"party_transfer",Map.of("lobbyId",id,"playerId",2));assertEquals(2,state().hostId);
        cmd(2,"party_kick",Map.of("lobbyId",id,"playerId",3));assertNull(manager.stateFor(3));assertFalse(lifecycle.isClaimed(3));
        cmd(2,"party_start",Map.of("lobbyId",id));assertEquals(409,error(2));
        admin.setMaintenance(true,"test");cmd(3,"party_create",Map.of());assertEquals(503,error(3));
        cmd(1,"party_sync",Map.of());assertEquals("party_state",endpoints.get(1L).last().t);
        advance(18000);assertNull(state());assertFalse(lifecycle.isClaimed(1));
    }
    @Test void withdrawalPreservesCompletedPointsDoesNotStealRank(){
        lobby(4);readyAndStart();loadAll();level(1,1,true);level(2,1,true);level(3,1,true);
        for(long id:users.keySet())cmd(id,"party_complete",ref());
        int points=state().participants.get(2).points2;advance(60);loadAll();level(3,1,true);
        cmd(3,"party_leave",Map.of("lobbyId",state().lobbyId,"matchId",state().matchId));
        level(1,1,true);for(long id:List.of(1L,2L,4L))cmd(id,"party_complete",ref());
        PartyProtocol.Entry withdrawn=state().results.get(1).entries.get(2);
        assertEquals(0,withdrawn.points2);assertEquals(0,withdrawn.score);assertFalse(withdrawn.eligible);
        assertEquals(points,state().participants.get(2).points2);assertTrue(state().participants.get(2).withdrawn);
        assertEquals(6,state().results.get(1).entries.get(0).points2,"frozen N=4");
    }
    @Test void parallelAdmissionsNeverOverfillAndBadCodesRateLimit() throws Exception{
        lobby(1);String code=state().code;
        for(long id=2;id<=12;id++)player(id);
        ExecutorService pool=Executors.newFixedThreadPool(11);CountDownLatch go=new CountDownLatch(1);
        List<Future<?>> work=new ArrayList<>();
        for(long id=2;id<=12;id++){long p=id;work.add(pool.submit(()->{try{go.await();cmd(p,"party_join",Map.of("code",code));}catch(InterruptedException e){Thread.currentThread().interrupt();}}));}
        go.countDown();for(Future<?> f:work)f.get();pool.shutdownNow();assertEquals(6,state().members.size());
        long outside=users.keySet().stream().filter(id->manager.stateFor(id)==null).findFirst().orElseThrow();
        for(int i=0;i<11;i++)cmd(outside,"party_join",Map.of("code","invalid"));assertEquals(429,error(outside));
    }
    @Test void pendingHistoryImmediatelyVisibleRetryAndFiveRounds(){
        lobby(3);cmd(1,"party_config",Map.of("lobbyId",state().lobbyId,"roundCount",5));readyAndStart();
        for(int r=0;r<5;r++) { loadAll(); for(long id:users.keySet())cmd(id,"party_complete",ref()); if(r<4)advance(60); }
        assertEquals("COMPLETED",state().status);assertEquals(5,state().results.size());
        assertTrue(state().participants.stream().allMatch(p->p.points2==0 && p.firstPlaces==0));
        when(history.recent(1)).thenReturn(List.of());manager.history(1,endpoints.get(1L),"history-rid");
        assertEquals("history-rid",endpoints.get(1L).last().rid);
        assertEquals(1,ProtocolCodec.convert(endpoints.get(1L).last().d,PartyProtocol.History.class).matches().size());
        doThrow(new IllegalStateException("offline")).doNothing().when(history).save(any());
        manager.flushHistory();manager.flushHistory();manager.flushHistory();verify(history,times(2)).save(any());
    }
    @Test void graceWindowCannotGenerateAdditionalGameplayTime(){
        lobby(3);readyAndStart();loadAll();
        clock.now.addAndGet(995);level(1,1,true);int score=state().participants.get(0).score;
        clock.now.addAndGet(10);level(1,2,true);assertEquals(409,error(1));
        assertEquals(score,state().participants.get(0).score);
    }
    @Test void loadingCutoffIsStrictEvenBeforeNextClockScan(){
        lobby(3);readyAndStart();cmd(1,"party_round_ready",ref());cmd(2,"party_round_ready",ref());
        clock.now.addAndGet(150);cmd(3,"party_round_ready",ref());assertEquals(409,error(3));
        assertEquals("COUNTDOWN",state().phase);assertFalse(state().participants.get(2).roundEligible);
        clock.now.addAndGet(30);level(1,1,true);assertEquals("PLAYING",state().phase);
        assertTrue(state().participants.get(0).score>0);
    }
    @Test void roundTimersAndDisconnectedExpiryNeverAffectSuccessorMatch(){
        lobby(3);readyAndStart();loadAll();String oldMatch=state().matchId;
        cmd(2,"party_leave",Map.of("lobbyId",state().lobbyId,"matchId",oldMatch));
        cmd(3,"party_leave",Map.of("lobbyId",state().lobbyId,"matchId",oldMatch));assertEquals("INTERRUPTED",state().status);
        cmd(1,"party_return",Map.of("lobbyId",state().lobbyId,"matchId",oldMatch));
        for(long id:List.of(2L,3L))cmd(id,"party_join",Map.of("code",state().code));readyAndStart();
        String next=state().matchId;assertNotEquals(oldMatch,next);loadAll();
        Map<String,Object> stale=new HashMap<>(ref());stale.put("matchId",oldMatch);
        cmd(1,"party_complete",stale);assertEquals(409,error(1));
        advance(900);assertEquals(next,state().matchId);assertEquals("PLAYING",state().phase);
        manager.disconnect(2);advance(500);manager.reconnect(2,endpoints.get(2L));advance(400);
        assertNotNull(manager.stateFor(2));assertEquals(PlayerLifecycle.PARTY_LOBBY,lifecycle.stateOf(2));
    }
    @Test void historyRetryImmutableAndShutdownDoesNotPersistInFlight(){
        lobby(3);readyAndStart();loadAll();
        manager.shutdown();verify(history,never()).save(any());assertFalse(lifecycle.isClaimed(1));assertNull(state());
    }
}
