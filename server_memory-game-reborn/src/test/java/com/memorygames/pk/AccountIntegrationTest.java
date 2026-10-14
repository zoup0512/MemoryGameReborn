package com.memorygames.pk;

import com.memorygames.pk.user.*;
import com.memorygames.pk.match.PlayerLifecycle;
import com.memorygames.pk.protocol.ProtocolCodec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.net.URI;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AccountIntegrationTest {
    @Autowired AccountService accounts;
    @Autowired UserService legacy;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate db;
    @Autowired PlayerLifecycle lifecycle;
    @MockBean VerificationMailer mailer;
    @LocalServerPort int port;
    private AccountService.AuthResponse guest() { return accounts.guest(UUID.randomUUID().toString(),AccountService.randomToken(),"Tester","Test device"); }
    private String code(AccountService.AuthResponse a,String email) {
        final String[] captured={null};
        doAnswer(call->{ captured[0]=call.getArgument(1); return null; }).when(mailer).send(eq(email),anyString(),anyString());
        accounts.sendVerification(accounts.authenticate(a.accessToken()),email); return captured[0];
    }
    private AccountService.AuthResponse upgrade(AccountService.AuthResponse g,String email) {
        return accounts.register(accounts.authenticate(g.accessToken()),email,"correct-password",code(g,email));
    }
    private void fails(String code,Runnable action) { AccountException e=assertThrows(AccountException.class,action::run); assertEquals(code,e.code); }
    @Test void guestSecretRecoveryAndHashOnly() {
        String installation=UUID.randomUUID().toString(),secret=AccountService.randomToken();
        var first=accounts.guest(installation,secret,"Guest","Phone");
        fails("INVALID_CREDENTIALS",()->accounts.guest(installation,AccountService.randomToken(),"Guest","Phone"));
        var recovered=accounts.guest(installation,secret,"Guest","Phone");
        assertEquals(first.user().userId(),recovered.user().userId());
        assertEquals(1,db.queryForObject("select count(*) from pk_coin_ledger where user_id=? and reason='WELCOME'",Integer.class,first.user().userId()));
        assertEquals(AccountService.hash(secret),db.queryForObject("select guest_secret_hash from pk_identity where user_id=?",String.class,first.user().userId()));
        assertEquals(AccountService.hash(recovered.accessToken()),db.queryForObject("select access_hash from pk_session where id=?",String.class,recovered.sessionId()));
        fails("SESSION_REVOKED",()->accounts.authenticate(first.accessToken()));
    }
    @Test void legacyMigrationPreservesDataAndDisablesUuid() {
        UserAccount old=legacy.register(UUID.randomUUID().toString(),"Legacy");
        legacy.adjustBalance(old,221,"test"); old=users.findById(old.id).orElseThrow();
        long id=old.id; String uuid=old.uuid,token=old.token,installation=UUID.randomUUID().toString(),secret=AccountService.randomToken();
        var migrated=accounts.migrate(token,installation,secret,"Phone");
        assertEquals(id,migrated.user().userId()); assertEquals(321,migrated.user().balance());
        assertTrue(legacy.byToken(token).isEmpty()); fails("LEGACY_AUTH_DISABLED",()->legacy.register(uuid,"Bad"));
        assertEquals(id,accounts.guest(installation,secret,"Recover","Phone").user().userId());
        assertEquals(2,db.queryForObject("select count(*) from pk_coin_ledger where user_id=?",Integer.class,id));
    }
    @Test void upgradePreservesIdLedgerHistoryAndRejectedVerification() {
        var g=guest(); long id=g.user().userId(); String email=UUID.randomUUID()+"@test.invalid";
        db.update("insert into pk_match_record(match_id,mode,player_a,nickname_a,result_a,score_a,player_b,nickname_b,score_b,elo_delta_a,created_at,settled_at,bot_opponent,voided) values(?,'PRACTICE',?,'Tester','W',10,0,'Bot',0,0,1,2,true,false)",UUID.randomUUID().toString(),id);
        String correct=code(g,email);
        fails("INVALID_VERIFICATION_CODE",()->accounts.register(accounts.authenticate(g.accessToken()),email,"correct-password","WRONG"));
        assertEquals("GUEST",users.findById(id).orElseThrow().accountType);
        assertEquals(1,db.queryForObject("select attempts from pk_verification where user_id=? and consumed=false",Integer.class,id));
        var a=accounts.register(accounts.authenticate(g.accessToken()),email,"correct-password",correct);
        assertEquals(id,a.user().userId()); assertEquals("REGISTERED",a.user().accountType()); assertTrue(a.user().emailVerified());
        assertEquals(g.sessionId(),a.sessionId()); assertEquals(1,db.queryForObject("select count(*) from pk_match_record where player_a=?",Integer.class,id));
        assertEquals(1,db.queryForObject("select count(*) from pk_coin_ledger where user_id=?",Integer.class,id));
        var other=guest(); fails("EMAIL_TAKEN",()->accounts.sendVerification(accounts.authenticate(other.accessToken()),email));
        fails("INVALID_CREDENTIALS",()->accounts.login(email,"wrong-password",UUID.randomUUID().toString(),"Phone"));
        assertEquals(id,accounts.login(email,"correct-password",UUID.randomUUID().toString(),"Second phone").user().userId());
    }
    @Test void profileCooldownAndRevokedPrincipalCannotMutate() {
        var g=guest(); var p=accounts.authenticate(g.accessToken());
        assertEquals("Changed",accounts.profile(p,"Changed","avatar_2").nickname());
        assertEquals("avatar_3",accounts.profile(p,"Changed","avatar_3").avatarValue());
        fails("NICKNAME_COOLDOWN",()->accounts.profile(p,"Again",null));
        db.update("update pk_user set nickname_changed_at=1 where id=?",p.user().id);
        assertEquals("Again",accounts.profile(p,"Again",null).nickname());
        accounts.logout(p,false);
        fails("SESSION_REVOKED",()->accounts.profile(p,"After revoke",null));
    }
    @Test void concurrentRefreshOneWinsButFamilyRevoked() throws Exception {
        var g=guest(); var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        var start=new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Object> refresh=()->{ start.await(); try { return accounts.refresh(g.refreshToken()); } catch(AccountException e) { return e.code; } };
            var first=pool.submit(refresh); var second=pool.submit(refresh); start.countDown();
            List<Object> results=List.of(first.get(15,java.util.concurrent.TimeUnit.SECONDS),second.get(15,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1,results.stream().filter(r->r instanceof AccountService.AuthResponse).count());
            assertTrue(results.contains("SESSION_REVOKED")); assertTrue(accounts.socketUser(g.sessionId()).isEmpty());
        } finally { pool.shutdownNow(); }
    }
    @Test void verificationFiveAttemptsCannotResetByRollback() {
        var g=guest(); String email=UUID.randomUUID()+"@test.invalid"; String correct=code(g,email);
        for(int i=0;i<5;i++) fails("INVALID_VERIFICATION_CODE",()->accounts.register(accounts.authenticate(g.accessToken()),email,"correct-password","WRONG"));
        fails("INVALID_VERIFICATION_CODE",()->accounts.register(accounts.authenticate(g.accessToken()),email,"correct-password",correct));
        assertEquals("GUEST",users.findById(g.user().userId()).orElseThrow().accountType);
    }
    @Test void rotationReplayAndSocketRevocation() throws Exception {
        var g=guest();
        TestWsClient ws=new TestWsClient(URI.create("ws://127.0.0.1:"+port+"/ws")); ws.connect();
        try {
            ProtocolCodec.Auth auth=new ProtocolCodec.Auth(); auth.token=g.accessToken(); ws.send(ProtocolCodec.T_AUTH,auth);
            var ok=ws.await(ProtocolCodec.T_AUTH_OK,5000); assertNotNull(ok); assertEquals(g.accessToken(),ok.as(ProtocolCodec.AuthOk.class).token);
            var rotated=accounts.refresh(g.refreshToken());
            ws.send(ProtocolCodec.T_HISTORY_REQUEST,new Object()); assertNotNull(ws.await(ProtocolCodec.T_HISTORY_RESPONSE,5000));
            db.update("update pk_session set access_expires_at=1 where id=?",g.sessionId());
            fails("TOKEN_EXPIRED",()->accounts.authenticate(rotated.accessToken()));
            ws.send(ProtocolCodec.T_TOP_REQUEST,new Object()); assertNotNull(ws.await(ProtocolCodec.T_TOP_RESPONSE,5000));
            fails("SESSION_REVOKED",()->accounts.refresh(g.refreshToken()));
            fails("SESSION_REVOKED",()->accounts.refresh(rotated.refreshToken()));
            assertNotNull(ws.await("session_replaced",5000));
            assertTrue(accounts.socketUser(g.sessionId()).isEmpty());
        } finally { ws.close(); }
    }
    @Test void multiUserDeletionNoUniqueTokenConflictAndLeaderboardFilters() {
        var a=guest(); var b=guest();
        legacy.markDeleted(users.findById(a.user().userId()).orElseThrow());
        legacy.markDeleted(users.findById(b.user().userId()).orElseThrow());
        assertTrue(legacy.topByElo().stream().noneMatch(u->u.deleted));
        assertTrue(accounts.socketUser(a.sessionId()).isEmpty()); assertTrue(accounts.socketUser(b.sessionId()).isEmpty());
    }
    @Test void deviceCapBusyLoginResetDeletionAndCancel() {
        var g=guest(); String email=UUID.randomUUID()+"@test.invalid"; var a=upgrade(g,email);
        for(int i=0;i<6;i++) a=accounts.login(email,"correct-password",UUID.randomUUID().toString(),"Phone");
        assertEquals(5,db.queryForObject("select count(*) from pk_session where user_id=? and revoked=false",Integer.class,a.user().userId()));
        long id=a.user().userId(); assertTrue(lifecycle.claim(id,PlayerLifecycle.IN_MATCH));
        try { fails("ACCOUNT_IN_MATCH",()->accounts.login(email,"correct-password",UUID.randomUUID().toString(),"Phone")); } finally { lifecycle.release(id); }
        var principal=accounts.authenticate(a.accessToken());
        accounts.deleteRequest(principal,"correct-password");
        fails("ACCOUNT_PENDING_DELETION",()->accounts.login(email,"correct-password",UUID.randomUUID().toString(),"Phone"));
        a=accounts.cancelDelete(email,"correct-password",UUID.randomUUID().toString(),"Phone");
        assertEquals(id,a.user().userId());
        accounts.deleteRequest(accounts.authenticate(a.accessToken()),"correct-password");
        db.update("update pk_user set delete_after=1 where id=?",id); accounts.finalizeDeletions();
        assertTrue(users.findById(id).orElseThrow().deleted);
        assertNull(db.queryForObject("select email from pk_identity where user_id=?",String.class,id));
    }
    @Test void failedMigrationAndProfileRollbackAndPasswordReset() {
        UserAccount old=legacy.register(UUID.randomUUID().toString(),"Legacy"); String oldToken=old.token;
        fails("INVALID_INPUT",()->accounts.migrate(oldToken,UUID.randomUUID().toString(),AccountService.randomToken(),"x".repeat(81)));
        assertTrue(legacy.byToken(oldToken).isPresent()); assertFalse(users.findById(old.id).orElseThrow().legacyAuthDisabled);
        var g=guest(); var p=accounts.authenticate(g.accessToken());
        fails("INVALID_AVATAR",()->accounts.profile(p,"Changed","bad")); assertEquals("Tester",users.findById(g.user().userId()).orElseThrow().nickname);
        String email=UUID.randomUUID()+"@test.invalid"; var a=upgrade(g,email);
        final String[] c={null}; doAnswer(call->{ c[0]=call.getArgument(1); return null; }).when(mailer).send(eq(email),anyString(),eq("RESET"));
        accounts.resetRequest(email); accounts.resetConfirm(email,c[0],"new-password");
        fails("SESSION_REVOKED",()->accounts.authenticate(a.accessToken()));
        fails("INVALID_CREDENTIALS",()->accounts.login(email,"correct-password",UUID.randomUUID().toString(),"Phone"));
        assertEquals(a.user().userId(),accounts.login(email,"new-password",UUID.randomUUID().toString(),"Phone").user().userId());
    }
    @Test void bothGuestAndRegisteredCanPlayAndSocketReplacementPreservesNewController() throws Exception {
        var g=guest(); var r=upgrade(guest(),UUID.randomUUID()+"@test.invalid");
        TestWsClient a=new TestWsClient(URI.create("ws://127.0.0.1:"+port+"/ws"));
        TestWsClient b=new TestWsClient(URI.create("ws://127.0.0.1:"+port+"/ws"));
        a.connect(); b.connect();
        try {
            ProtocolCodec.Auth aa=new ProtocolCodec.Auth(); aa.token=g.accessToken(); a.send(ProtocolCodec.T_AUTH,aa); assertNotNull(a.await(ProtocolCodec.T_AUTH_OK,5000));
            ProtocolCodec.Auth bb=new ProtocolCodec.Auth(); bb.token=r.accessToken(); b.send(ProtocolCodec.T_AUTH,bb); assertNotNull(b.await(ProtocolCodec.T_AUTH_OK,5000));
            a.send(ProtocolCodec.T_ROOM_CREATE,new Object()); var room=a.await(ProtocolCodec.T_ROOM_CREATED,5000); assertNotNull(room);
            ProtocolCodec.RoomJoin join=new ProtocolCodec.RoomJoin(); join.code=room.as(ProtocolCodec.RoomCreated.class).code; b.send(ProtocolCodec.T_ROOM_JOIN,join);
            assertNotNull(a.await(ProtocolCodec.T_MATCH_FOUND,10000)); assertNotNull(b.await(ProtocolCodec.T_MATCH_FOUND,10000));
            a.send(ProtocolCodec.T_MATCH_START,new Object()); b.send(ProtocolCodec.T_MATCH_START,new Object());
            assertNotNull(a.await(ProtocolCodec.T_ROUND_START,5000));
            a.send(ProtocolCodec.T_RESIGN,new Object()); assertNotNull(a.await(ProtocolCodec.T_MATCH_END,5000));
            assertNotNull(b.await(ProtocolCodec.T_MATCH_END,5000));
        } finally { a.close(); b.close(); }
        TestWsClient first=new TestWsClient(URI.create("ws://127.0.0.1:"+port+"/ws")); TestWsClient next=new TestWsClient(URI.create("ws://127.0.0.1:"+port+"/ws")); first.connect(); next.connect();
        try {
            ProtocolCodec.Auth auth=new ProtocolCodec.Auth(); auth.token=r.accessToken(); first.send(ProtocolCodec.T_AUTH,auth); assertNotNull(first.await(ProtocolCodec.T_AUTH_OK,5000));
            next.send(ProtocolCodec.T_AUTH,auth); assertNotNull(next.await(ProtocolCodec.T_AUTH_OK,5000)); assertNotNull(first.await("session_replaced",5000));
            next.send(ProtocolCodec.T_HISTORY_REQUEST,new Object()); assertNotNull(next.await(ProtocolCodec.T_HISTORY_RESPONSE,5000));
        } finally { first.close(); next.close(); }
    }
}
