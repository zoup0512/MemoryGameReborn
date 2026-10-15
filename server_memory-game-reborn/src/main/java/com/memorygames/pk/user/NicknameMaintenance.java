package com.memorygames.pk.user;

import com.memorygames.pk.match.PlayerLifecycle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import java.util.*;

/** Explicit administrator operations; never invoked on startup or scheduled against production data. */
@Service
public class NicknameMaintenance {
    private final JdbcTemplate db;
    private final UserRepository users;
    private final NicknameService names;
    private final PlayerLifecycle lifecycle;
    private final TransactionTemplate tx;
    public NicknameMaintenance(JdbcTemplate db,UserRepository users,NicknameService names,PlayerLifecycle lifecycle,PlatformTransactionManager manager) {
        this.db=db; this.users=users; this.names=names; this.lifecycle=lifecycle; this.tx=new TransactionTemplate(manager);
    }
    public List<Map<String,Object>> scan(long after,int limit) {
        List<Map<String,Object>> result=new ArrayList<>();
        for(Map<String,Object> row:db.queryForList("select id,nickname from pk_user where deleted=false and id>? order by id limit ?",Math.max(0,after),Math.max(1,Math.min(limit,20)))) {
            long id=((Number)row.get("id")).longValue(); String name=(String)row.get("nickname");
            String digest=names.digest(name),code="PASS";
            try {
                String normalized=NicknameService.normalize(name);
                if(!normalized.equals(name)) code="INVALID_NICKNAME";
                else names.review("history:"+id,id,"history:"+digest,name);
            } catch(AccountException e) { code=e.code; }
            result.add(Map.of("userId",id,"digest",digest,"result",code,"policyVersion",NicknameService.POLICY_VERSION));
        }
        return result;
    }
    public void replace(long id,String expectedDigest) {
        if(expectedDigest==null || !expectedDigest.matches("[a-f0-9]{64}")) throw new AccountException(400,"INVALID_INPUT","expected digest required");
        tx.executeWithoutResult(s -> {
            UserAccount user=users.findByIdForUpdate(id).orElseThrow(()->new AccountException(404,"NOT_FOUND","not found"));
            if(user.deleted || !names.digest(user.nickname).equals(expectedDigest)) throw new AccountException(409,"PROFILE_CHANGED","profile changed");
            if(!lifecycle.claim(id,"NICKNAME_MAINTENANCE")) throw new AccountException(409,"ACCOUNT_IN_MATCH","account in match");
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) { lifecycle.releaseIf(id,"NICKNAME_MAINTENANCE"); }
            });
            names.generate(user); user.updatedAt=System.currentTimeMillis(); users.saveAndFlush(user);
            // Also scrub stale public match snapshots, without altering stats, cooldown or wallet.
            db.update("update pk_match_record set nickname_a=? where player_a=?",user.nickname,id);
            db.update("update pk_match_record set nickname_b=? where player_b=?",user.nickname,id);
            db.update("insert into pk_nickname_audit(user_id,created_at,result,request_id,digest,policy_version) values(?,?,?,?,?,?)",
                    id,System.currentTimeMillis(),"ADMIN_SAFE_RESET","",expectedDigest,NicknameService.POLICY_VERSION);
        });
    }
}
