package com.memorygames.pk.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.*;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/** Single nickname policy and mutation authority. No caller-supplied 'trusted' flag. */
@Service
public class NicknameService {
    public static final String POLICY_VERSION="nickname-v2";
    public static final long COOLDOWN_MS=604800000L;
    private static final long TTL=60000L;
    private static final SecureRandom RANDOM=new SecureRandom();
    private static final String[] FIRST={"晴空","微风","星河","青山","明月","晨光","白云","春雨","秋叶","流萤","清泉","彩虹"};
    private static final String[] LAST={"小鹿","松鼠","海豚","熊猫","白兔","云雀","水獭","雪狐","企鹅","小猫","飞鱼","山雀"};
    private static final Pattern CONTACT=Pattern.compile("(?i)(https?://|www\\.|[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}|(?:微信|微.?信|vx|v信|qq|加群|联系|电话|手机|代练|代充)[\\s:：_+\u002d]*[a-z0-9_\u002d]{5,}|1[3-9][0-9]{9})");
    private final NicknameProvider provider;
    private final JdbcTemplate db;
    private final TransactionTemplate auditTx;
    private final byte[] digestKey;
    private final Semaphore concurrency;
    private final int globalLimit, subjectLimit;
    private final Map<String,Entry> cache=new LinkedHashMap<>();
    private final Map<String,Window> rates=new HashMap<>();
    private final Map<String,Window> requestRates=new HashMap<>();
    private static class Window { long start; int count; Window(long now) { start=now; } }
    private record Entry(CompletableFuture<NicknameProvider.Result> future,long expires) {}
    /** Opaque, short-lived approval binds exact canonical text, user/installation, context and policy. */
    public static final class Approval {
        private final String subject,context,name,version,digest;
        private final long expires;
        private Approval(String subject,String context,String name,String digest) {
            this.subject=subject; this.context=context; this.name=name; this.digest=digest;
            this.version=POLICY_VERSION; this.expires=System.currentTimeMillis()+TTL;
        }
    }
    public NicknameService(NicknameProvider provider,JdbcTemplate db,PlatformTransactionManager manager,
            @Value("${pk.nickname.audit-hmac-key:}") String key,
            @Value("${pk.nickname.max-concurrent:8}") int concurrent,
            @Value("${pk.nickname.global-per-minute:60}") int globalLimit,
            @Value("${pk.nickname.subject-per-minute:5}") int subjectLimit) {
        this.provider=provider; this.db=db; this.auditTx=new TransactionTemplate(manager);
        auditTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.digestKey=(key.isBlank()?AccountService.randomToken():key).getBytes(StandardCharsets.UTF_8);
        this.concurrency=new Semaphore(Math.max(1,Math.min(concurrent,64)));
        this.globalLimit=Math.max(1,globalLimit); this.subjectLimit=Math.max(1,subjectLimit);
    }
    public static String normalize(String value) {
        if(value==null) throw error(400,"INVALID_NICKNAME");
        // Reject forbidden characters BEFORE stripping too: trailing controls are not silently repaired.
        if(value.length()>256 || value.codePoints().anyMatch(c -> {
            int t=Character.getType(c);
            return t==Character.CONTROL || t==Character.FORMAT || t==Character.UNASSIGNED || t==Character.SURROGATE || t==Character.PRIVATE_USE
                    || c==0x034f || c==0x2800 || c==0x115f || c==0x1160 || c==0x3164 || c==0xffa0
                    || (c>=0xfe00 && c<=0xfe0f) || (c>=0xe0100 && c<=0xe01ef)
                    || (c!=0x20 && (Character.isWhitespace(c) || Character.isSpaceChar(c)));
        })) throw error(400,"INVALID_NICKNAME");
        String name=Normalizer.normalize(value.strip(),Normalizer.Form.NFC);
        int length=name.codePointCount(0,name.length());
        if(length<2 || length>16) throw error(400,"INVALID_NICKNAME");
        return name;
    }
    static boolean locallyRejected(String name) {
        String folded=Normalizer.normalize(name,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{P}]","");
        return List.of("法轮功","六四屠杀","操你妈","傻逼","色情","约炮","博彩","赌博","官方客服","官方管理员","系统客服","系统管理员","游戏客服","游戏管理员","官方认证")
                .stream().anyMatch(folded::contains) || CONTACT.matcher(Normalizer.normalize(name,Normalizer.Form.NFKC)).find();
    }
    public Approval review(String subject,long userId,String context,String raw) {
        if(TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("nickname review must run outside transactions");
        // Bound even cache hits and locally rejected requests before any audit write.
        synchronized(requestRates) {
            if(!rate(requestRates,subject,System.currentTimeMillis(),600,30)) throw error(503,"NICKNAME_MODERATION_UNAVAILABLE");
        }
        String name=normalize(raw),digest=digest(name),key=POLICY_VERSION+"|"+subject+"|"+context+"|"+digest;
        NicknameProvider.Result result;
        if(locallyRejected(name)) result=new NicknameProvider.Result(NicknameProvider.Decision.REJECT,"");
        else result=deduplicated(key,subject,name);
        String requestId=result.requestId()!=null&&result.requestId().matches("[A-Za-z0-9_-]{0,128}")?result.requestId():"";
        NicknameProvider.Decision decision=result.decision()==null?NicknameProvider.Decision.UNAVAILABLE:result.decision();
        auditTx.executeWithoutResult(s -> db.update("insert into pk_nickname_audit(user_id,created_at,result,request_id,digest,policy_version) values(?,?,?,?,?,?)",
                userId,System.currentTimeMillis(),decision.name(),requestId,digest,POLICY_VERSION));
        switch(decision) {
            case PASS: return new Approval(subject,context,name,digest);
            case REJECT: throw error(422,"NICKNAME_REJECTED");
            case PENDING: throw error(422,"NICKNAME_PENDING");
            default: throw error(503,"NICKNAME_MODERATION_UNAVAILABLE");
        }
    }
    private NicknameProvider.Result deduplicated(String key,String subject,String name) {
        Entry entry; boolean owner=false; long now=System.currentTimeMillis();
        synchronized(cache) {
            cache.entrySet().removeIf(e -> e.getValue().expires<now && e.getValue().future.isDone());
            entry=cache.get(key);
            if(entry==null) {
                if(cache.size()>=4096 || !rate(rates,subject,now,globalLimit,subjectLimit) || !concurrency.tryAcquire()) return TencentNicknameProvider.unavailable();
                entry=new Entry(new CompletableFuture<>(),now+TTL); cache.put(key,entry); owner=true;
            }
        }
        if(owner) {
            NicknameProvider.Result result;
            try { result=provider.review(name,UUID.randomUUID().toString()); if(result==null) result=TencentNicknameProvider.unavailable(); }
            catch(Exception e) { result=TencentNicknameProvider.unavailable(); }
            finally { concurrency.release(); }
            entry.future.complete(result);
            if(result.decision()==NicknameProvider.Decision.UNAVAILABLE) synchronized(cache) { cache.remove(key,entry); }
        }
        try { return entry.future.get(11000,TimeUnit.MILLISECONDS); }
        catch(InterruptedException e) { Thread.currentThread().interrupt(); return TencentNicknameProvider.unavailable(); }
        catch(Exception e) { return TencentNicknameProvider.unavailable(); }
    }
    private static boolean rate(Map<String,Window> rates,String subject,long now,int globalLimit,int subjectLimit) {
        rates.entrySet().removeIf(e -> now-e.getValue().start>=60000);
        if(rates.size()>8192) return false;
        Window global=rates.computeIfAbsent("global",k->new Window(now));
        Window user=rates.computeIfAbsent("user:"+subject,k->new Window(now));
        if(global.count>=globalLimit || user.count>=subjectLimit) return false;
        global.count++; user.count++; return true;
    }
    public String digest(String name) {
        try { return HexFormat.of().formatHex(TencentNicknameProvider.hmac(digestKey,POLICY_VERSION+"|"+name)); }
        catch(Exception e) { throw new IllegalStateException("nickname digest unavailable"); }
    }
    void apply(UserAccount user,String subject,String context,String raw,Approval approval,boolean change) {
        String name=normalize(raw);
        if(approval==null || !subject.equals(approval.subject) || !context.equals(approval.context)
                || !name.equals(approval.name) || !digest(name).equals(approval.digest)
                || !POLICY_VERSION.equals(approval.version) || approval.expires<System.currentTimeMillis()) throw error(503,"NICKNAME_MODERATION_UNAVAILABLE");
        if(change) checkCooldown(user);
        user.nickname=name;
        if(change) user.nicknameChangedAt=System.currentTimeMillis();
    }
    /** Explicit server-only path; never interprets a client nickname or prefix as trusted. */
    void generate(UserAccount user) { user.nickname=FIRST[RANDOM.nextInt(FIRST.length)]+LAST[RANDOM.nextInt(LAST.length)]; }
    void anonymize(UserAccount user) { user.nickname="已注销棋友"; }
    static void checkCooldown(UserAccount user) {
        if(user.nicknameChangedAt>0 && System.currentTimeMillis()<user.nicknameChangedAt+COOLDOWN_MS) throw error(429,"NICKNAME_COOLDOWN");
    }
    private static AccountException error(int status,String code) { return new AccountException(status,code,code.replace('_',' ')); }
}
