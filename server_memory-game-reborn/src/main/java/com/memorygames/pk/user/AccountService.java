package com.memorygames.pk.user;

import com.memorygames.pk.match.PlayerLifecycle;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

@Service
@Transactional(noRollbackFor=CommittedAccountException.class)
public class AccountService {
    public record Revoked(String sessionId) {}
    public record Principal(UserAccount user, String sessionId, String deviceId) {}
    public record UserView(long userId,String accountType,String nickname,String avatarValue,int elo,int balance,int wins,int losses,int draws,String email,boolean emailVerified) {}
    public record AuthResponse(String accessToken,String refreshToken,long accessExpiresAt,long refreshExpiresAt,String sessionId,UserView user) {}
    private static final SecureRandom RANDOM=new SecureRandom();
    private final JdbcTemplate db;
    private final UserRepository repository;
    private final UserService users;
    private final PlayerLifecycle lifecycle;
    private final VerificationMailer mailer;
    private final ApplicationEventPublisher events;
    private final BCryptPasswordEncoder passwords=new BCryptPasswordEncoder(12);
    private final String dummyHash=new BCryptPasswordEncoder(12).encode("non-user-password");
    public AccountService(JdbcTemplate db,UserRepository repository,UserService users,PlayerLifecycle lifecycle,VerificationMailer mailer,ApplicationEventPublisher events) {
        this.db=db; this.repository=repository; this.users=users; this.lifecycle=lifecycle; this.mailer=mailer; this.events=events;
    }
    public static String randomToken() { byte[] bytes=new byte[32]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    public static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
    private static AccountException error(int status,String code) { return new AccountException(status,code,code.replace('_',' ')); }
    private static String text(String value,int min,int max) {
        if(value==null || value.length()<min || value.length()>max || value.codePoints().anyMatch(Character::isISOControl)) throw error(400,"INVALID_INPUT"); return value;
    }
    public static String email(String value) {
        String e=text(value,3,254).strip().toLowerCase(Locale.ROOT);
        if(!e.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw error(400,"INVALID_EMAIL"); return e;
    }
    private static void password(String value) {
        text(value,8,72); if(value.getBytes(StandardCharsets.UTF_8).length>72) throw error(400,"INVALID_PASSWORD");
    }
    public static String nickname(String value) {
        String name=value==null ? "Player" : value.strip();
        int count=name.codePointCount(0,name.length());
        if(count<2 || count>16 || name.codePoints().anyMatch(c -> Character.isISOControl(c) || Character.getType(c)==Character.FORMAT || Character.getType(c)==Character.UNASSIGNED)) throw error(400,"INVALID_NICKNAME");
        return name;
    }
    private Map<String,Object> one(String sql,Object... args) { List<Map<String,Object>> rows=db.queryForList(sql,args); return rows.isEmpty()?null:rows.get(0); }
    private static long number(Map<String,Object> row,String name) { return ((Number)row.get(name)).longValue(); }
    private static boolean flag(Map<String,Object> row,String name) { return Boolean.TRUE.equals(row.get(name)); }
    private UserAccount lock(long id) { return repository.findByIdForUpdate(id).orElseThrow(()->error(401,"INVALID_CREDENTIALS")); }
    private void usable(UserAccount u) {
        if(u.deleted) throw error(403,"ACCOUNT_DELETED");
        if(u.deleteRequestedAt>0) throw error(403,"ACCOUNT_PENDING_DELETION");
        if(u.bannedUntil>System.currentTimeMillis()) throw error(403,"ACCOUNT_BANNED");
    }
    private void idle(UserAccount u) { if(lifecycle.isClaimed(u.id)) throw error(409,"ACCOUNT_IN_MATCH"); }
    private void releaseAuthAfterTransaction(long id) {
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCompletion(int status) { lifecycle.releaseIf(id,"ACCOUNT_AUTH"); }
                });
    }
    private UserAccount currentLocked(Principal p) {
        UserAccount u=lock(p.user.id);
        Map<String,Object> s=one("select * from pk_session where id=? for update",p.sessionId);
        if(s==null || number(s,"user_id")!=u.id || flag(s,"revoked")) throw error(401,"SESSION_REVOKED");
        if(number(s,"access_expires_at")<=System.currentTimeMillis()) throw error(401,"TOKEN_EXPIRED");
        usable(u); return u;
    }
    private void audit(long id,String action) { db.update("insert into pk_account_audit(user_id,action,created_at) values(?,?,?)",id,action,System.currentTimeMillis()); }
    public UserView view(UserAccount u) {
        Map<String,Object> i=one("select * from pk_identity where user_id=?",u.id);
        return new UserView(u.id,u.accountType,u.nickname,u.avatarValue,u.elo,u.balance,u.wins,u.losses,u.draws,i==null?null:(String)i.get("email"),i!=null&&flag(i,"email_verified"));
    }
    public AuthResponse guest(String installation,String secret,String name,String device) {
        text(installation,1,128); text(secret,32,256);
        // Client must supply a CSPRNG secret; SHA-256 never persists the credential itself.
        if(!secret.matches("[A-Za-z0-9_-]{32,256}")) throw error(400,"INVALID_GUEST_SECRET");
        Map<String,Object> identity=one("select * from pk_identity where installation_id=?",installation);
        UserAccount u;
        if(identity==null) {
            u=users.register(UUID.randomUUID().toString(),nickname(name));
            u.legacyAuthDisabled=true; repository.saveAndFlush(u);
            db.update("insert into pk_identity(user_id,installation_id,guest_secret_hash,email_verified) values(?,?,?,false)",u.id,installation,hash(secret));
            audit(u.id,"GUEST_CREATED");
        } else {
            if(!MessageDigest.isEqual(hash(secret).getBytes(StandardCharsets.UTF_8),Objects.toString(identity.get("guest_secret_hash"),"").getBytes(StandardCharsets.UTF_8))) throw error(401,"INVALID_CREDENTIALS");
            u=lock(number(identity,"user_id"));
            identity=one("select * from pk_identity where user_id=?",u.id);
            if(!"GUEST".equals(u.accountType) || !hash(secret).equals(identity.get("guest_secret_hash"))) throw error(401,"INVALID_CREDENTIALS");
            usable(u); idle(u);
        }
        return issue(u,installation,device);
    }
    public AuthResponse migrate(String legacy,String installation,String secret,String device) {
        text(installation,1,128); text(secret,32,256);
        if(!secret.matches("[A-Za-z0-9_-]{32,256}")) throw error(400,"INVALID_GUEST_SECRET");
        UserAccount found=repository.findByToken(text(legacy,1,256)).orElseThrow(()->error(401,"INVALID_CREDENTIALS"));
        UserAccount u=lock(found.id); usable(u); idle(u);
        if(u.legacyAuthDisabled || !"GUEST".equals(u.accountType)) throw error(401,"INVALID_CREDENTIALS");
        if(one("select id from pk_identity where installation_id=?",installation)!=null) throw error(409,"INSTALLATION_TAKEN");
        db.update("insert into pk_identity(user_id,installation_id,guest_secret_hash,email_verified) values(?,?,?,false)",u.id,installation,hash(secret));
        u.legacyAuthDisabled=true; u.token=randomToken(); repository.saveAndFlush(u); audit(u.id,"LEGACY_MIGRATED");
        events.publishEvent(new LegacyDisabled(u.id));
        return issue(u,installation,device);
    }
    public record LegacyDisabled(long userId) {}
    private Map<String,Object> credentials(String e,String p) {
        text(p,1,72); Map<String,Object> i=one("select * from pk_identity where email=?",email(e));
        String encoded=i==null?dummyHash:Objects.toString(i.get("password_hash"),dummyHash);
        if(!passwords.matches(p,encoded) || i==null) throw error(401,"INVALID_CREDENTIALS"); return i;
    }
    public AuthResponse login(String e,String p,String installation,String device) {
        Map<String,Object> i=credentials(e,p); UserAccount u=lock(number(i,"user_id")); credentials(e,p); usable(u); idle(u);
        return issue(u,installation,device);
    }
    private AuthResponse issue(UserAccount u,String installation,String deviceName) {
        if(!lifecycle.claim(u.id,"ACCOUNT_AUTH")) throw error(409,"ACCOUNT_IN_MATCH");
        releaseAuthAfterTransaction(u.id);
        return issueIdle(u,installation,deviceName);
    }
    private AuthResponse issueIdle(UserAccount u,String installation,String deviceName) {
        text(installation,1,128); String name=deviceName==null||deviceName.isBlank()?"Device":text(deviceName.strip(),1,80);
        Map<String,Object> d=one("select * from pk_device where user_id=? and installation_id=?",u.id,installation);
        String deviceId=d==null?UUID.randomUUID().toString():(String)d.get("id");
        if(d==null) db.update("insert into pk_device(id,user_id,installation_id,device_name,last_active_at) values(?,?,?,?,?)",deviceId,u.id,installation,name,System.currentTimeMillis());
        else db.update("update pk_device set device_name=?,last_active_at=? where id=?",name,System.currentTimeMillis(),deviceId);
        for(Map<String,Object> s:db.queryForList("select id from pk_session where device_id=? and revoked=false",deviceId)) revoke((String)s.get("id"));
        List<Map<String,Object>> active=db.queryForList("select s.id from pk_session s join pk_device d on d.id=s.device_id where s.user_id=? and s.revoked=false and s.refresh_expires_at>? order by d.last_active_at",u.id,System.currentTimeMillis());
        while(active.size()>=5) revoke((String)active.remove(0).get("id"));
        String id=UUID.randomUUID().toString(),access=randomToken(),refresh=randomToken(); long now=System.currentTimeMillis(),ae=now+900000,re=now+2592000000L;
        db.update("insert into pk_session(id,user_id,device_id,access_hash,access_expires_at,refresh_expires_at,revoked,created_at) values(?,?,?,?,?,?,false,?)",id,u.id,deviceId,hash(access),ae,re,now);
        db.update("insert into pk_refresh(id,session_id,used) values(?,?,false)",hash(refresh),id);
        audit(u.id,"SESSION_CREATED"); return new AuthResponse(access,refresh,ae,re,id,view(u));
    }
    public Principal authenticate(String token) {
        if(token==null || token.isBlank() || token.length()>512) throw error(401,"INVALID_CREDENTIALS");
        Map<String,Object> s=one("select * from pk_session where access_hash=?",hash(token));
        if(s==null) throw error(401,"INVALID_CREDENTIALS");
        if(flag(s,"revoked")) throw error(401,"SESSION_REVOKED");
        if(number(s,"access_expires_at")<=System.currentTimeMillis()) throw error(401,"TOKEN_EXPIRED");
        UserAccount u=repository.findById(number(s,"user_id")).orElseThrow(()->error(401,"INVALID_CREDENTIALS")); usable(u);
        db.update("update pk_device set last_active_at=? where id=?",System.currentTimeMillis(),s.get("device_id"));
        return new Principal(u,(String)s.get("id"),(String)s.get("device_id"));
    }
    public Optional<UserAccount> accessUser(String token) { try { return Optional.of(authenticate(token).user()); } catch(AccountException ex) { return Optional.empty(); } }
    public Optional<UserAccount> socketUser(String sessionId) {
        Map<String,Object> s=one("select * from pk_session where id=?",sessionId);
        if(s==null || flag(s,"revoked") || number(s,"refresh_expires_at")<=System.currentTimeMillis()) return Optional.empty();
        UserAccount u=repository.findById(number(s,"user_id")).orElse(null);
        if(u==null || u.deleted || u.deleteRequestedAt>0 || u.bannedUntil>System.currentTimeMillis()) return Optional.empty(); return Optional.of(u);
    }
    public AuthResponse refresh(String token) {
        text(token,1,512);
        Map<String,Object> r=one("select * from pk_refresh where id=?",hash(token));
        if(r==null) throw error(401,"INVALID_CREDENTIALS");
        Map<String,Object> initial=one("select * from pk_session where id=?",r.get("session_id"));
        UserAccount u=lock(number(initial,"user_id"));
        Map<String,Object> s=one("select * from pk_session where id=? for update",r.get("session_id"));
        // Re-read only after the family lock: concurrent refreshes must trigger replay detection.
        r=one("select * from pk_refresh where id=?",hash(token));
        if(flag(r,"used")) { revoke((String)s.get("id")); throw new CommittedAccountException(401,"SESSION_REVOKED"); }
        if(flag(s,"revoked")) throw error(401,"SESSION_REVOKED");
        if(number(s,"refresh_expires_at")<=System.currentTimeMillis()) { revoke((String)s.get("id")); throw new CommittedAccountException(401,"TOKEN_EXPIRED"); }
        usable(u);
        String access=randomToken(),refresh=randomToken(); long ae=System.currentTimeMillis()+900000;
        db.update("update pk_refresh set used=true where id=?",hash(token));
        db.update("insert into pk_refresh(id,session_id,used) values(?,?,false)",hash(refresh),s.get("id"));
        db.update("update pk_session set access_hash=?,access_expires_at=? where id=?",hash(access),ae,s.get("id"));
        return new AuthResponse(access,refresh,ae,number(s,"refresh_expires_at"),(String)s.get("id"),view(u));
    }
    public void revoke(String id) {
        Map<String,Object> s=one("select user_id from pk_session where id=?",id);
        if(s==null) return;
        lock(number(s,"user_id"));
        db.update("update pk_session set revoked=true where id=?",id); events.publishEvent(new Revoked(id));
    }
    public void logout(Principal p,boolean all) {
        currentLocked(p);
        if(all) revokeAll(p.user.id); else revoke(p.sessionId);
    }
    public void revokeAll(long id) { for(Map<String,Object> s:db.queryForList("select id from pk_session where user_id=? and revoked=false",id)) revoke((String)s.get("id")); audit(id,"SESSIONS_REVOKED"); }
    public void sendVerification(Principal p,String rawEmail) {
        UserAccount u=currentLocked(p); usable(u);
        if(!"GUEST".equals(u.accountType)) throw error(409,"ALREADY_REGISTERED");
        String e=email(rawEmail); if(one("select id from pk_identity where email=?",e)!=null) throw error(409,"EMAIL_TAKEN");
        sendCode(u.id,e,"REGISTER");
    }
    private void sendCode(long id,String e,String purpose) {
        String code=String.format(Locale.ROOT,"%06d",RANDOM.nextInt(1000000));
        mailer.send(e,code,purpose);
        db.update("update pk_verification set consumed=true where user_id=? and email=? and purpose=?",id,e,purpose);
        db.update("insert into pk_verification(id,user_id,email,purpose,code_hash,expires_at,attempts,consumed) values(?,?,?,?,?,?,0,false)",UUID.randomUUID().toString(),id,e,purpose,hash(code),System.currentTimeMillis()+600000);
    }
    private void verify(long id,String e,String purpose,String code) {
        Map<String,Object> v=one("select * from pk_verification where user_id=? and email=? and purpose=? and consumed=false order by expires_at desc limit 1 for update",id,e,purpose);
        if(v==null) throw error(400,"INVALID_VERIFICATION_CODE");
        db.update("update pk_verification set attempts=attempts+1 where id=?",v.get("id"));
        if(number(v,"expires_at")<=System.currentTimeMillis() || number(v,"attempts")>=5 || code==null || !code.matches("[0-9]{6}") || !MessageDigest.isEqual(hash(code).getBytes(StandardCharsets.UTF_8),((String)v.get("code_hash")).getBytes(StandardCharsets.UTF_8))) throw new CommittedAccountException(400,"INVALID_VERIFICATION_CODE");
        db.update("update pk_verification set consumed=true where id=?",v.get("id"));
    }
    public AuthResponse register(Principal p,String rawEmail,String pwd,String code) {
        String e=email(rawEmail); password(pwd); UserAccount u=currentLocked(p); usable(u);
        if(!lifecycle.claim(u.id,"ACCOUNT_AUTH")) throw error(409,"ACCOUNT_IN_MATCH");
        releaseAuthAfterTransaction(u.id);
        if(!"GUEST".equals(u.accountType)) throw error(409,"ALREADY_REGISTERED");
        if(one("select id from pk_identity where email=?",e)!=null) throw error(409,"EMAIL_TAKEN");
        verify(u.id,e,"REGISTER",code);
        db.update("update pk_identity set email=?,password_hash=?,email_verified=true,guest_secret_hash=null where user_id=?",e,passwords.encode(pwd),u.id);
        u.accountType="REGISTERED"; u.upgradedAt=System.currentTimeMillis(); u.legacyAuthDisabled=true; repository.saveAndFlush(u);
        // Upgrade rotates the same session (device and PK controller stay attached).
        String access=randomToken(),refresh=randomToken(); long ae=System.currentTimeMillis()+900000,re=System.currentTimeMillis()+2592000000L;
        db.update("update pk_refresh set used=true where session_id=?",p.sessionId);
        db.update("insert into pk_refresh(id,session_id,used) values(?,?,false)",hash(refresh),p.sessionId);
        db.update("update pk_session set access_hash=?,access_expires_at=?,refresh_expires_at=? where id=?",hash(access),ae,re,p.sessionId);
        for(Map<String,Object> s:db.queryForList("select id from pk_session where user_id=? and id<>? and revoked=false",u.id,p.sessionId)) revoke((String)s.get("id"));
        audit(u.id,"ACCOUNT_UPGRADED"); return new AuthResponse(access,refresh,ae,re,p.sessionId,view(u));
    }
    public void resetRequest(String rawEmail) {
        String e=email(rawEmail); Map<String,Object> i=one("select * from pk_identity where email=?",e);
        mailer.checkAvailable();
        if(i!=null) sendCode(number(i,"user_id"),e,"RESET");
    }
    public void resetConfirm(String rawEmail,String code,String pwd) {
        String e=email(rawEmail); password(pwd); Map<String,Object> i=one("select * from pk_identity where email=?",e);
        if(i==null) throw error(400,"INVALID_VERIFICATION_CODE");
        UserAccount u=lock(number(i,"user_id")); verify(u.id,e,"RESET",code);
        db.update("update pk_identity set password_hash=? where user_id=?",passwords.encode(pwd),u.id); revokeAll(u.id); audit(u.id,"PASSWORD_RESET");
    }
    public UserView profile(Principal p,String name,String avatar) {
        UserAccount u=currentLocked(p);
        String nextName=name==null?u.nickname:nickname(name);
        if(avatar!=null && !avatar.matches("avatar_([1-9]|1[0-2])")) throw error(400,"INVALID_AVATAR");
        if(!nextName.equals(u.nickname)) {
            if(u.nicknameChangedAt>0 && System.currentTimeMillis()-u.nicknameChangedAt<604800000L) throw error(429,"NICKNAME_COOLDOWN");
            u.nickname=nextName; u.nicknameChangedAt=System.currentTimeMillis();
        }
        if(avatar!=null) u.avatarValue=avatar;
        u.updatedAt=System.currentTimeMillis(); repository.save(u); return view(u);
    }
    public Map<String,Object> devices(Principal p) {
        List<Map<String,Object>> result=new ArrayList<>();
        for(Map<String,Object> row:db.queryForList("select d.id,d.device_name,d.last_active_at,s.id as session_id from pk_device d join pk_session s on s.device_id=d.id where s.user_id=? and s.revoked=false and s.refresh_expires_at>? order by d.last_active_at desc",p.user.id,System.currentTimeMillis()))
            result.add(Map.of("deviceId",row.get("id"),"sessionId",row.get("session_id"),"deviceName",row.get("device_name"),"lastActiveAt",row.get("last_active_at"),"current",p.sessionId.equals(row.get("session_id"))));
        return Map.of("devices",result);
    }
    public void removeDevice(Principal p,String device) {
        currentLocked(p);
        for(Map<String,Object> s:db.queryForList("select id from pk_session where user_id=? and device_id=? and revoked=false",p.user.id,device)) revoke((String)s.get("id"));
    }
    public void deleteRequest(Principal p,String pwd) {
        UserAccount u=currentLocked(p);
        if(!lifecycle.claim(u.id,"ACCOUNT_AUTH")) throw error(409,"ACCOUNT_IN_MATCH");
        releaseAuthAfterTransaction(u.id);
        if("REGISTERED".equals(u.accountType)) {
            Map<String,Object> i=one("select * from pk_identity where user_id=?",u.id);
            if(pwd==null || pwd.length()>72 || !passwords.matches(pwd,(String)i.get("password_hash"))) throw error(401,"INVALID_CREDENTIALS");
        }
        u.deleteRequestedAt=System.currentTimeMillis(); u.deleteAfter=u.deleteRequestedAt+604800000L; repository.saveAndFlush(u); revokeAll(u.id); audit(u.id,"DELETE_REQUESTED");
    }
    public AuthResponse cancelDelete(String e,String pwd,String installation,String device) {
        Map<String,Object> i=credentials(e,pwd); UserAccount u=lock(number(i,"user_id")); credentials(e,pwd);
        if(u.deleted || u.deleteAfter<=System.currentTimeMillis() || u.deleteRequestedAt==0) throw error(403,"ACCOUNT_DELETED");
        if(u.bannedUntil>System.currentTimeMillis()) throw error(403,"ACCOUNT_BANNED");
        u.deleteAfter=0; u.deleteRequestedAt=0; repository.saveAndFlush(u); audit(u.id,"DELETE_CANCELLED"); return issue(u,installation,device);
    }
    @Scheduled(fixedDelay=60000)
    public void finalizeDeletions() {
        for(Map<String,Object> row:db.queryForList("select id from pk_user where deleted=false and delete_after>0 and delete_after<=?",System.currentTimeMillis())) {
            UserAccount u=lock(number(row,"id"));
            if(u.deleteAfter==0 || u.deleteAfter>System.currentTimeMillis()) continue;
            revokeAll(u.id); u.deleted=true; u.nickname="deleted-"+u.id; u.avatarValue="avatar_1"; u.uuid="deleted-"+u.id; u.token=randomToken(); u.legacyAuthDisabled=true; repository.saveAndFlush(u);
            db.update("update pk_identity set email=null,password_hash=null,installation_id=null,guest_secret_hash=null,email_verified=false where user_id=?",u.id);
            db.update("update pk_device set device_name='Deleted device',installation_id=id where user_id=?",u.id);
            db.update("delete from pk_verification where user_id=?",u.id);
            db.update("update pk_match_record set nickname_a=? where player_a=?",u.nickname,u.id);
            db.update("update pk_match_record set nickname_b=? where player_b=?",u.nickname,u.id); audit(u.id,"DELETE_FINALIZED");
        }
    }
}
