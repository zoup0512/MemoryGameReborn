package com.memorygames.pk;

import com.memorygames.pk.user.*;
import com.memorygames.pk.protocol.ProtocolCodec;
import com.memorygames.pk.match.PlayerLifecycle;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.datasource.url=jdbc:h2:mem:nickname-tests;DB_CLOSE_DELAY=-1","pk.nickname.global-per-minute=10000",
        "pk.nickname.subject-per-minute=100","pk.nickname.audit-hmac-key=offline-test-hmac-only"})
@ActiveProfiles("test")
class NicknameModerationIntegrationTest {
    @Autowired AccountService accounts;
    @Autowired UserService legacy;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate db;
    @Autowired NicknameMaintenance maintenance;
    @Autowired NicknameService names;
    @Autowired PlayerLifecycle lifecycle;
    @MockBean NicknameProvider provider;
    @LocalServerPort int port;
    @BeforeEach void setup() { reset(provider); when(provider.review(anyString(),anyString())).thenReturn(result(NicknameProvider.Decision.PASS)); }
    private NicknameProvider.Result result(NicknameProvider.Decision d) { return new NicknameProvider.Result(d,"offline-request"); }
    private AccountService.AuthResponse guest() { return accounts.guest(UUID.randomUUID().toString(),AccountService.randomToken(),null,"Offline"); }
    private AccountException fails(int status,String code,Runnable action) { var e=assertThrows(AccountException.class,action::run); assertEquals(status,e.status); assertEquals(code,e.code); return e; }
    @Test void generatedDefaultsAreChineseControlledAndNeverReviewed() {
        for(int i=0;i<15;i++) { var g=guest(); assertTrue(g.user().nickname().matches("[\\p{IsHan}]{4}")); assertEquals(0,g.user().nextNicknameChangeAt()); }
        var old=legacy.register(UUID.randomUUID().toString(),null); assertTrue(old.nickname.matches("[\\p{IsHan}]{4}"));
        verifyNoInteractions(provider);
    }
    @Test void customGuestAlwaysReviewedEvenIfItLooksGeneratedAndFirstProfileChangeFree() {
        var g=accounts.guest(UUID.randomUUID().toString(),AccountService.randomToken(),"晴空小鹿","Offline");
        verify(provider).review(eq("晴空小鹿"),anyString()); assertEquals(0,g.user().nextNicknameChangeAt());
        var next=accounts.profile(accounts.authenticate(g.accessToken()),"山间晨光","avatar_12");
        assertTrue(next.nextNicknameChangeAt()>System.currentTimeMillis());
    }
    @Test void rejectedPendingAndUnavailableJointUpdatesRollbackWithoutCooldown() {
        for(var decision:List.of(NicknameProvider.Decision.REJECT,NicknameProvider.Decision.PENDING,NicknameProvider.Decision.UNAVAILABLE)) {
            var g=guest(); var p=accounts.authenticate(g.accessToken());
            when(provider.review(anyString(),anyString())).thenReturn(result(decision));
            String code=switch(decision) { case REJECT -> "NICKNAME_REJECTED"; case PENDING -> "NICKNAME_PENDING"; default -> "NICKNAME_MODERATION_UNAVAILABLE"; };
            fails(decision==NicknameProvider.Decision.UNAVAILABLE?503:422,code,()->accounts.profile(p,"月光小鱼","avatar_8"));
            var row=users.findById(g.user().userId()).orElseThrow();
            assertEquals(g.user().nickname(),row.nickname); assertEquals("avatar_1",row.avatarValue); assertEquals(0,row.nicknameChangedAt);
            assertEquals(1,db.queryForObject("select count(*) from pk_nickname_audit where user_id=? and result=?",Integer.class,row.id,decision.name()));
        }
    }
    @Test void providerExceptionFailsClosedAndMayRetryWithoutCooldown() {
        var g=guest(); var p=accounts.authenticate(g.accessToken());
        when(provider.review(anyString(),anyString())).thenThrow(new RuntimeException("private error never logged"));
        fails(503,"NICKNAME_MODERATION_UNAVAILABLE",()->accounts.profile(p,"星海小鱼",null));
        doReturn(result(NicknameProvider.Decision.PASS)).when(provider).review(anyString(),anyString());
        assertEquals("星海小鱼",accounts.profile(p,"星海小鱼",null).nickname());
    }
    @Test void avatarOnlyAndUnchangedNameSkipReviewAndValidateAvatarFirst() {
        var g=guest(); var p=accounts.authenticate(g.accessToken());
        assertEquals("avatar_2",accounts.profile(p,null,"avatar_2").avatarValue());
        assertEquals("avatar_3",accounts.profile(p,g.user().nickname(),"avatar_3").avatarValue());
        fails(400,"INVALID_AVATAR",()->accounts.profile(p,"星海小鹿","avatar_13"));
        verifyNoInteractions(provider);
    }
    @Test void sevenDaysCooldownAndExpiredWindow() {
        var g=guest(); var p=accounts.authenticate(g.accessToken());
        var first=accounts.profile(p,"星海小鹿",null);
        assertEquals(users.findById(p.user().id).orElseThrow().nicknameChangedAt+604800000L,first.nextNicknameChangeAt());
        fails(429,"NICKNAME_COOLDOWN",()->accounts.profile(p,"星海企鹅",null));
        verify(provider,times(1)).review(anyString(),anyString());
        db.update("update pk_user set nickname_changed_at=? where id=?",System.currentTimeMillis()-604800001L,p.user().id);
        assertEquals("星海企鹅",accounts.profile(p,"星海企鹅",null).nickname());
    }
    @Test void localRulesAreConservativeButCannotReplaceExternalReview() {
        var g=guest(); var p=accounts.authenticate(g.accessToken());
        for(String bad:List.of("官方客服小明","操你妈啊","微信abc12345","13812345678","www.example.cn"))
            fails(422,"NICKNAME_REJECTED",()->accounts.profile(p,bad,"avatar_2"));
        verifyNoInteractions(provider);
        when(provider.review(anyString(),anyString())).thenReturn(result(NicknameProvider.Decision.UNAVAILABLE));
        fails(503,"NICKNAME_MODERATION_UNAVAILABLE",()->accounts.profile(p,"小明爱游戏",null));
        verify(provider).review(eq("小明爱游戏"),anyString());
    }
    @Test void unicodeCodepointsNormalizationAndForbiddenCharacters() {
        assertEquals("😀".repeat(16),AccountService.nickname("😀".repeat(16)));
        assertEquals("é猫",AccountService.nickname("  e\u0301猫  "));
        assertEquals("小猫",AccountService.nickname("小猫"));
        for(String invalid:List.of("猫","😀".repeat(17),"猫\u0000猫","猫\u200d猫","猫\u0378猫","猫\ud800猫","猫\ue000猫","小猫\n","  ","猫\u115f猫","猫\u1160猫","猫\u3164猫","猫\uffa0猫","猫\ufe0f猫","猫\udb40\udd00猫","猫\u00a0猫","猫\u3000猫","猫\u034f猫","猫\u2800猫"))
            fails(400,"INVALID_NICKNAME",()->AccountService.nickname(invalid));
        fails(400,"INVALID_NICKNAME",()->AccountService.nickname(null));
    }
    @Test void reviewRunsOutsideTransactionsAndBanOrRevocationDuringReviewPreventsJointCommit() {
        for(String mutation:List.of("ban","revoke","delete","cooldown")) {
            var g=guest(); var p=accounts.authenticate(g.accessToken());
            doAnswer(call -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                if(mutation.equals("ban")) db.update("update pk_user set banned_until=? where id=?",System.currentTimeMillis()+60000,p.user().id);
                if(mutation.equals("revoke")) accounts.logout(p,false);
                if(mutation.equals("delete")) db.update("update pk_user set delete_requested_at=1 where id=?",p.user().id);
                if(mutation.equals("cooldown")) db.update("update pk_user set nickname_changed_at=? where id=?",System.currentTimeMillis(),p.user().id);
                return result(NicknameProvider.Decision.PASS);
            }).when(provider).review(anyString(),anyString());
            String code=switch(mutation) { case "ban" -> "ACCOUNT_BANNED"; case "revoke" -> "SESSION_REVOKED"; case "delete" -> "ACCOUNT_PENDING_DELETION"; default -> "PROFILE_CHANGED"; };
            fails(mutation.equals("revoke")?401:mutation.equals("cooldown")?409:403,code,()->accounts.profile(p,"山间小鹿","avatar_9"));
            var row=users.findById(p.user().id).orElseThrow(); assertEquals(g.user().nickname(),row.nickname); assertEquals("avatar_1",row.avatarValue);
        }
    }
    @Test void concurrentSameRequestDeduplicatesExternalReviewButOnlyOneCommitWins() throws Exception {
        var g=guest(); var p=accounts.authenticate(g.accessToken());
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        when(provider.review(anyString(),anyString())).thenAnswer(c->{ entered.countDown(); assertTrue(release.await(5,TimeUnit.SECONDS)); return result(NicknameProvider.Decision.PASS); });
        var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Object> action=()->{ try { return accounts.profile(p,"秋日松鼠","avatar_7"); } catch(AccountException e) { return e.code; } };
            var a=pool.submit(action); assertTrue(entered.await(5,TimeUnit.SECONDS)); var b=pool.submit(action);
            Thread.sleep(150); release.countDown();
            var all=List.of(a.get(5,TimeUnit.SECONDS),b.get(5,TimeUnit.SECONDS));
            assertEquals(1,all.stream().filter(x->x instanceof AccountService.UserView).count());
            assertTrue(all.contains("PROFILE_CHANGED")); verify(provider,times(1)).review(anyString(),anyString());
        } finally { release.countDown(); pool.shutdownNow(); }
    }
    @Test void pendingDuplicateReusesDecisionButIsBoundToUserAndAuditsNoPlaintext() {
        when(provider.review(anyString(),anyString())).thenReturn(result(NicknameProvider.Decision.PENDING));
        var g=guest(); var p=accounts.authenticate(g.accessToken());
        for(int i=0;i<2;i++) fails(422,"NICKNAME_PENDING",()->accounts.profile(p,"隐私昵称甲",null));
        verify(provider,times(1)).review(anyString(),anyString());
        var h=guest(); fails(422,"NICKNAME_PENDING",()->accounts.profile(accounts.authenticate(h.accessToken()),"隐私昵称甲",null));
        verify(provider,times(2)).review(anyString(),anyString());
        var audit=db.queryForMap("select * from pk_nickname_audit where user_id=? limit 1",p.user().id);
        assertEquals(64,((String)audit.get("digest")).length()); assertFalse(audit.toString().contains("隐私昵称甲"));
    }
    @Test void legacyRestoreIgnoresRenameAndWsNewCustomDoesNotBypassModeration() throws Exception {
        UserAccount old=legacy.register(UUID.randomUUID().toString(),null);
        db.update("update pk_user set nickname_changed_at=? where id=?",System.currentTimeMillis(),old.id);
        when(provider.review(anyString(),anyString())).thenReturn(result(NicknameProvider.Decision.REJECT));
        TestWsClient ws=new TestWsClient(URI.create("ws://127.0.0.1:"+port+"/ws")); ws.connect();
        try {
            ProtocolCodec.Register r=new ProtocolCodec.Register(); r.uuid=old.uuid; r.nickname="官方客服";
            ws.send(ProtocolCodec.T_REGISTER,r); var ok=ws.await(ProtocolCodec.T_AUTH_OK,5000); assertNotNull(ok);
            assertEquals(old.nickname,ok.as(ProtocolCodec.AuthOk.class).nickname); verifyNoInteractions(provider);
        } finally { ws.close(); }
        TestWsClient fresh=new TestWsClient(URI.create("ws://127.0.0.1:"+port+"/ws")); fresh.connect();
        try {
            ProtocolCodec.Register r=new ProtocolCodec.Register(); r.uuid=UUID.randomUUID().toString(); r.nickname="测试新昵称";
            fresh.send(ProtocolCodec.T_REGISTER,r); var error=fresh.await(ProtocolCodec.T_ERROR,5000); assertNotNull(error);
            assertTrue(error.data.toString().contains("NICKNAME_REJECTED")); assertTrue(users.findByUuid(r.uuid).isEmpty());
        } finally { fresh.close(); }
    }
    @Test void newGuestRejectedCreatesNoIdentityAndRestoreNeverRenames() {
        String installation=UUID.randomUUID().toString(),secret=AccountService.randomToken();
        when(provider.review(anyString(),anyString())).thenReturn(result(NicknameProvider.Decision.REJECT));
        fails(422,"NICKNAME_REJECTED",()->accounts.guest(installation,secret,"拒绝昵称","Offline"));
        assertEquals(0,db.queryForObject("select count(*) from pk_identity where installation_id=?",Integer.class,installation));
        var g=accounts.guest(installation,secret,null,"Offline");
        var recovered=accounts.guest(installation,secret,"随意改名","Offline"); assertEquals(g.user().nickname(),recovered.user().nickname());
    }
    @Test void staleAdminSnapshotsCannotOverwriteReviewedNickname() {
        var g=guest(); long id=g.user().userId(); UserAccount stale=users.findById(id).orElseThrow();
        accounts.profile(accounts.authenticate(g.accessToken()),"新审昵称",null);
        legacy.adjustBalance(stale,1,"test"); legacy.setElo(stale,1100); legacy.setRestriction(stale,1); legacy.setBan(stale,1);
        var row=users.findById(id).orElseThrow(); assertEquals("新审昵称",row.nickname); assertTrue(row.nicknameChangedAt>0);
        assertEquals(g.user().balance()+1,row.balance); assertEquals(1100,row.elo); assertEquals(1,row.restrictedMatches);
    }
    @Test void historyScanDoesNotMutateAndSafeResetRequiresExactDigestAndIdle() {
        var g=guest(); long id=g.user().userId();
        db.update("update pk_user set nickname='官方客服旧名',nickname_changed_at=123 where id=?",id);
        var scan=maintenance.scan(id-1,1); assertEquals("NICKNAME_REJECTED",scan.get(0).get("result"));
        assertEquals("官方客服旧名",users.findById(id).orElseThrow().nickname);
        String digest=(String)scan.get(0).get("digest");
        fails(409,"PROFILE_CHANGED",()->maintenance.replace(id,"a".repeat(64)));
        assertTrue(lifecycle.claim(id,PlayerLifecycle.IN_MATCH));
        try { fails(409,"ACCOUNT_IN_MATCH",()->maintenance.replace(id,digest)); } finally { lifecycle.release(id); }
        maintenance.replace(id,digest); var row=users.findById(id).orElseThrow(); assertTrue(row.nickname.matches("[\\p{IsHan}]{4}")); assertEquals(123,row.nicknameChangedAt);
    }
}
