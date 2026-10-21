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
        public void send(String json){messages.add(ProtocolCodec.parse(json));if(messages.size()>4)messages.remove(0);}
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
    void cmd(long id,String type,Map<String,Object> data){manager.command(users.get(id),endpoints.get(id),"test-"+id,type,data,"r");}
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

    @ParameterizedTest @ValueSource(ints={2,3,4,6,7,12,24,64})
    void tiesZeroFrozenNHistoryAndContinuousLobby(int n){
        lobby(n); readyAndStart(); String match=state().matchId;
        long version=state().stateVersion;cmd(1,"party_start",Map.of("lobbyId",state().lobbyId));assertEquals(version,state().stateVersion);
        for(int r=0;r<3;r++){
            loadAll();level(1,1,true);level(2,1,true);
            // No-score participants are eligible but must earn zero.
            for(long id:users.keySet())cmd(id,"party_complete",ref());
            PartyProtocol.State s=state();assertEquals(r+1,s.results.size());
            assertEquals(2*n-3,s.results.get(r).entries.get(0).points2);
            if(n>2) assertEquals(0,s.results.get(r).entries.get(2).points2);
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
        advance(10);level(1,3,true);assertEquals("party_ack",endpoints.get(1L).last().t);
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
    @ParameterizedTest @ValueSource(ints={4,7,12,24,64})
    void withdrawalPreservesCompletedPointsDoesNotStealRank(int n){
        lobby(n);readyAndStart();loadAll();level(1,1,true);level(2,1,true);level(3,1,true);
        for(long id:users.keySet())cmd(id,"party_complete",ref());
        long points=state().participants.get(2).points2;advance(60);loadAll();level(3,1,true);
        cmd(3,"party_leave",Map.of("lobbyId",state().lobbyId,"matchId",state().matchId));
        level(1,1,true);for(long id:users.keySet())if(id!=3)cmd(id,"party_complete",ref());
        PartyProtocol.Entry withdrawn=state().results.get(1).entries.get(2);
        assertEquals(0,withdrawn.points2);assertEquals(0,withdrawn.score);assertFalse(withdrawn.eligible);
        assertEquals(points,state().participants.get(2).points2);assertTrue(state().participants.get(2).withdrawn);
        assertEquals(2L*(n-1),state().results.get(1).entries.get(0).points2,"frozen starting N");
    }
    @Test void parallelAdmissionsDoNotImposeRoomLimitAndBadCodesRateLimit() throws Exception{
        lobby(1);String code=state().code;
        for(long id=2;id<=24;id++)player(id);
        ExecutorService pool=Executors.newFixedThreadPool(23);CountDownLatch go=new CountDownLatch(1);
        List<Future<?>> work=new ArrayList<>();
        for(long id=2;id<=24;id++){long p=id;work.add(pool.submit(()->{try{go.await();cmd(p,"party_join",Map.of("code",code));}catch(InterruptedException e){Thread.currentThread().interrupt();}}));}
        go.countDown();for(Future<?> f:work)f.get();pool.shutdownNow();assertEquals(24,state().members.size());
        long outside=100;player(outside);
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
    @Test void sixthMemberDoesNotMakeRoomFullAndStartStillRequiresTwoPlayers(){
        lobby(1);String lobby=state().lobbyId;
        cmd(1,"party_start",Map.of("lobbyId",lobby));assertEquals(409,error(1));
        for(long id=2;id<=7;id++){
            player(id);cmd(id,"party_join",Map.of("code",state().code));
            assertEquals("party_state",endpoints.get(id).last().t);
            assertEquals(id,state().members.size());assertEquals(0,state().capacity);
        }
        readyAndStart();assertEquals(7,state().participants.size());assertEquals("LOADING",state().phase);
    }
    @ParameterizedTest @ValueSource(ints={2,7,12,24,64})
    void withdrawalsReachSameInterruptionAtOneRemainingPlayer(int n){
        lobby(n);readyAndStart();loadAll();level(1,1,true);
        String lobby=state().lobbyId, match=state().matchId;
        for(long id=n;id>=2;id--){
            cmd(id,"party_leave",Map.of("lobbyId",lobby,"matchId",match));
            assertNull(manager.stateFor(id));assertFalse(lifecycle.isClaimed(id));
            if(id>2) assertEquals("PLAYING",state().phase);
        }
        assertEquals("INTERRUPTED",state().status);assertEquals("not_enough_players",state().reason);
        assertEquals(n,state().participants.size());assertEquals(1,state().members.size());
        assertTrue(state().participants.stream().allMatch(p->p.rank==0 && p.score==0));
        assertEquals(n-1,state().participants.stream().filter(p->p.withdrawn).count());
        manager.flushHistory();verify(history).save(argThat(s->s.participants.size()==n && s.status.equals("INTERRUPTED")));
    }
    @Test void twoPlayerDisconnectAllowsReconnectButExpiryInterruptsLikeExplicitLeave(){
        lobby(2);readyAndStart();loadAll();String match=state().matchId;
        manager.disconnect(2);advance(500);assertEquals("PLAYING",state().phase);
        manager.reconnect(2,endpoints.get(2L));advance(400);assertNotNull(manager.stateFor(2));
        assertTrue(state().members.get(1).connected);
        cmd(1,"party_complete",ref());cmd(2,"party_complete",ref());advance(60);loadAll();
        manager.disconnect(2);advance(899);assertEquals("PLAYING",state().phase);advance(1);
        assertNull(manager.stateFor(2));assertFalse(lifecycle.isClaimed(2));
        assertEquals(match,state().matchId);assertEquals("INTERRUPTED",state().status);
        assertEquals("not_enough_players",state().reason);assertTrue(state().participants.stream().allMatch(p->p.rank==0));
    }
    @Test void scoreReportsUseConstantSizeReceiptsAndOneCoalescedBroadcastPerTickWindow(){
        lobby(24);readyAndStart();loadAll();
        endpoints.values().forEach(e->e.messages.clear());long before=state().stateVersion;
        for(long id=1;id<=24;id++)level(id,1,true);
        advance(10);level(1,2,true);
        for(long id=1;id<=24;id++){
            Endpoint e=endpoints.get(id);assertEquals(id==1?2:1,e.messages.size());
            assertTrue(e.messages.stream().allMatch(f->f.t.equals("party_ack") && f.rid.equals("r")));
        }
        PartyProtocol.Ack ack=ProtocolCodec.convert(endpoints.get(1L).last().d,PartyProtocol.Ack.class);
        assertEquals(before+25,ack.stateVersion());assertEquals(state().participants.get(0).score,ack.score());
        assertEquals(1,ack.playerId());assertEquals(state().matchId,ack.matchId());
        advance(239);assertEquals(1,endpoints.get(2L).messages.size());advance(1);
        for(Endpoint e:endpoints.values())assertEquals(1,e.messages.stream().filter(f->f.t.equals("party_state")).count());
        assertEquals(before+25,state().stateVersion);advance(100);
        for(Endpoint e:endpoints.values())assertEquals(1,e.messages.stream().filter(f->f.t.equals("party_state")).count());
        level(1,2,true);assertEquals(409,error(1));assertEquals("r",endpoints.get(1L).last().rid);
    }
    @SuppressWarnings("unchecked")
    @Test void globalRoomGuardAndHistoryBackpressureRemainIndependentOfRoomSize()throws Exception{
        lobby(24);player(100);String lobby=state().lobbyId,code=state().code;
        var field=PartyManager.class.getDeclaredField("lobbies");field.setAccessible(true);
        Map<String,Object> rooms=(Map<String,Object>)field.get(manager);Object original=rooms.get(lobby);
        try{
            for(int i=1;i<10000;i++)rooms.put("guard-"+i,original);
            cmd(100,"party_create",Map.of());assertEquals(503,error(100));assertFalse(lifecycle.isClaimed(100));
            cmd(100,"party_join",Map.of("code",code));assertEquals(25,state().members.size());
        }finally{rooms.entrySet().removeIf(e->!e.getKey().equals(lobby));}
        field=PartyManager.class.getDeclaredField("pending");field.setAccessible(true);
        Map<String,PartyProtocol.State> backlog=(Map<String,PartyProtocol.State>)field.get(manager);
        try{
            for(int i=0;i<1000;i++)backlog.put("backlog-"+i,new PartyProtocol.State());
            player(101);cmd(101,"party_create",Map.of());assertEquals(503,error(101));
            cmd(101,"party_join",Map.of("code",code));assertEquals(503,error(101));
            cmd(1,"party_start",Map.of("lobbyId",lobby));assertEquals(503,error(1));
            cmd(1,"party_sync",Map.of());assertEquals("party_state",endpoints.get(1L).last().t);
        }finally{backlog.clear();}
    }
    @Test void sharedIpJoinRateLimitStillProtectsUncappedRooms(){
        lobby(1);String code=state().code;
        for(long id=2;id<=42;id++){
            player(id);manager.command(users.get(id),endpoints.get(id),"shared-ip","party_join",Map.of("code",code),"r");
            if(id<=41)assertEquals("party_state",endpoints.get(id).last().t);
        }
        assertEquals(429,error(42));assertEquals(41,state().members.size());
        clock.now.addAndGet(60000);cmd(42,"party_join",Map.of("code",code));assertEquals(42,state().members.size());
    }
    private Object firstSeat()throws Exception{
        var field=PartyManager.class.getDeclaredField("lobbies");field.setAccessible(true);
        Object lobby=((Map<?,?>)field.get(manager)).values().iterator().next();
        field=lobby.getClass().getDeclaredField("seats");field.setAccessible(true);
        return ((Map<?,?>)field.get(lobby)).get(1L);
    }
    @Test void scoreOverflowIsRejectedBeforeConsumingProgressOrCadence()throws Exception{
        lobby(2);readyAndStart();loadAll();Object seat=firstSeat();
        var scoreField=seat.getClass().getDeclaredField("score");scoreField.setAccessible(true);scoreField.setInt(seat,Integer.MAX_VALUE);
        long version=state().stateVersion;
        level(1,1,true);assertEquals(409,error(1));assertEquals(Integer.MAX_VALUE,state().participants.get(0).score);
        assertEquals(version,state().stateVersion);
        level(1,1,false);assertEquals("party_ack",endpoints.get(1L).last().t);
        assertEquals(Integer.MAX_VALUE,state().participants.get(0).score);
    }
    @Test void pointsAccumulateAndSerializeBeyondIntegerRange()throws Exception{
        lobby(2);readyAndStart();loadAll();Object seat=firstSeat();
        var pointsField=seat.getClass().getDeclaredField("points2");pointsField.setAccessible(true);pointsField.setLong(seat,Integer.MAX_VALUE);
        level(1,1,true);for(long id:users.keySet())cmd(id,"party_complete",ref());
        assertEquals((long)Integer.MAX_VALUE+2,state().participants.get(0).points2);
        assertEquals(1,state().participants.get(0).rank);
        PartyProtocol.State decoded=ProtocolCodec.convert(ProtocolCodec.parse(ProtocolCodec.encode("party_state",state())).d,PartyProtocol.State.class);
        assertEquals((long)Integer.MAX_VALUE+2,decoded.participants.get(0).points2);
    }
    @Test void historyRetryImmutableAndShutdownDoesNotPersistInFlight(){
        lobby(3);readyAndStart();loadAll();
        manager.shutdown();verify(history,never()).save(any());assertFalse(lifecycle.isClaimed(1));assertNull(state());
    }
}
