package com.memorygames.pk.user;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NicknameServiceTest {
    private NicknameService service(NicknameProvider provider,int concurrent,int global,int perUser) {
        PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        return new NicknameService(provider,mock(JdbcTemplate.class),manager,"offline-key",concurrent,global,perUser);
    }
    @Test void rateLimitIsFailClosedAndDuplicateDoesNotSpendAnotherExternalCall() {
        NicknameProvider provider=mock(NicknameProvider.class);
        when(provider.review(anyString(),anyString())).thenReturn(new NicknameProvider.Result(NicknameProvider.Decision.PASS,"offline"));
        var names=service(provider,1,3,1);
        var first=names.review("u1",1,"context","星河小鹿");
        assertNotNull(names.review("u1",1,"context","星河小鹿"));
        var blocked=assertThrows(AccountException.class,()->names.review("u1",1,"context","星河企鹅"));
        assertEquals(503,blocked.status); assertEquals("NICKNAME_MODERATION_UNAVAILABLE",blocked.code);
        verify(provider,times(1)).review(anyString(),anyString());
        var user=new UserAccount();
        assertThrows(AccountException.class,()->names.apply(user,"u2","context","星河小鹿",first,false));
        assertThrows(AccountException.class,()->names.apply(user,"u1","other","星河小鹿",first,false));
        assertThrows(AccountException.class,()->names.apply(user,"u1","context","其他昵称",first,false));
        names.apply(user,"u1","context","星河小鹿",first,false); assertEquals("星河小鹿",user.nickname);
    }
    @Test void concurrentLimitDoesNotQueueOrCallProvider() throws Exception {
        NicknameProvider provider=mock(NicknameProvider.class); var names=service(provider,1,100,100);
        var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
        when(provider.review(anyString(),anyString())).thenAnswer(c->{ entered.countDown(); assertTrue(release.await(5,TimeUnit.SECONDS)); return new NicknameProvider.Result(NicknameProvider.Decision.PASS,"offline"); });
        var pool=Executors.newSingleThreadExecutor();
        try {
            var pending=pool.submit(()->names.review("u1",1,"context","星河小鹿"));
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            var error=assertThrows(AccountException.class,()->names.review("u2",2,"context","星河企鹅")); assertEquals(503,error.status);
            release.countDown(); assertNotNull(pending.get(5,TimeUnit.SECONDS)); verify(provider,times(1)).review(anyString(),anyString());
        } finally { release.countDown(); pool.shutdownNow(); }
    }
    @Test void localRejectFloodIsBoundedBeforeAuditOrExternalCall() {
        NicknameProvider provider=mock(NicknameProvider.class); var names=service(provider,8,100,100);
        for(int i=0;i<30;i++) assertEquals(422,assertThrows(AccountException.class,()->names.review("u1",1,"context","官方客服")).status);
        assertEquals(503,assertThrows(AccountException.class,()->names.review("u1",1,"context","官方客服")).status);
        verifyNoInteractions(provider);
    }
    @Test void globalLimitIsSharedBetweenSubjects() {
        var names=service((n,id)->new NicknameProvider.Result(NicknameProvider.Decision.PASS,"offline"),8,1,10);
        names.review("u1",1,"context","星河小鹿");
        assertEquals(503,assertThrows(AccountException.class,()->names.review("u2",2,"context","星河企鹅")).status);
    }
}
