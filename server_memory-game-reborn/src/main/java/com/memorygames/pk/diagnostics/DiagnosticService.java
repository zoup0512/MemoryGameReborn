package com.memorygames.pk.diagnostics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memorygames.pk.user.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class DiagnosticService implements ApplicationRunner {
    public record Upload(Integer schemaVersion,String uploadId,String appVersion,String buildTime,
                         Long startedAt,Long endedAt,String content,String matchId) {}
    public record Receipt(String id,String uploadId,long receivedAt,int bytes) {}
    private static final long RETENTION_MS=14L*86400000;
    private static final String METADATA="id,user_id,upload_id,schema_version,app_version,build_time,started_at,ended_at,match_id,received_at,bytes";
    private final JdbcTemplate db;
    private final TransactionTemplate transactions;
    private final DiagnosticSanitizer sanitizer;
    private final ObjectMapper json;
    private final int maxReports;
    public DiagnosticService(JdbcTemplate db,PlatformTransactionManager manager,DiagnosticSanitizer sanitizer,ObjectMapper json,
                             @Value("${pk.diagnostics.max-reports:1000}") int maxReports) {
        this.db=db; this.transactions=new TransactionTemplate(manager); this.sanitizer=sanitizer; this.json=json;
        this.maxReports=Math.max(1,Math.min(10000,maxReports));
    }
    @Override public void run(ApplicationArguments arguments) {
        // Flyway seeds the production lock; also seed create-drop test databases.
        try { db.update("insert into pk_diagnostic_lock(id) select 1 where not exists(select 1 from pk_diagnostic_lock where id=1)"); }
        catch(org.springframework.dao.DuplicateKeyException ignored) { /* Another instance already seeded it. */ }
    }
    private void lock() { db.queryForObject("select id from pk_diagnostic_lock where id=1 for update",Integer.class); }
    private int count(String sql,Object... args) { return Objects.requireNonNull(db.queryForObject(sql,Integer.class,args)); }
    private static AccountException error(int status,String code) { return new AccountException(status,code,code.replace('_',' ')); }
    public Receipt upload(long userId,Upload upload) {
        if(upload==null || !Integer.valueOf(1).equals(upload.schemaVersion()) || !DiagnosticSanitizer.uuid(upload.uploadId()) ||
                upload.startedAt()==null || upload.endedAt()==null || upload.startedAt()<0 || upload.endedAt()<upload.startedAt() ||
                (upload.matchId()!=null && !DiagnosticSanitizer.identifier(upload.matchId()))) throw DiagnosticSanitizer.invalid();
        String version=sanitizer.metadata(upload.appVersion()),build=sanitizer.metadata(upload.buildTime());
        String content=sanitizer.sanitize(upload.content());
        int bytes=content.getBytes(StandardCharsets.UTF_8).length;
        if(bytes>DiagnosticSanitizer.MAX_CONTENT_BYTES) throw error(413,"PAYLOAD_TOO_LARGE");
        final String fingerprint;
        try {
            // Hash only: different original reports must conflict even if redaction would make them identical.
            fingerprint=AccountService.hash(json.writeValueAsString(Arrays.asList(upload.schemaVersion(),upload.appVersion(),
                    upload.buildTime(),upload.startedAt(),upload.endedAt(),upload.content(),upload.matchId())));
        } catch(com.fasterxml.jackson.core.JsonProcessingException ex) { throw DiagnosticSanitizer.invalid(); }
        String uploadId=UUID.fromString(upload.uploadId()).toString();
        // A DB lock serializes quotas and uniqueness across threads AND service instances.
        // TransactionTemplate returns only after commit, never reports a rolled-back insert as success.
        return transactions.execute(status->{
            lock(); long now=System.currentTimeMillis();
            db.update("delete from pk_diagnostic where received_at<?",now-RETENTION_MS);
            List<Map<String,Object>> prior=db.queryForList("select id,upload_id,received_at,bytes,fingerprint from pk_diagnostic where user_id=? and upload_id=?",userId,uploadId);
            if(!prior.isEmpty()) {
                Map<String,Object> p=prior.get(0);
                if(!fingerprint.equals(p.get("fingerprint"))) throw error(409,"DIAGNOSTIC_CONFLICT");
                return new Receipt((String)p.get("id"),(String)p.get("upload_id"),((Number)p.get("received_at")).longValue(),((Number)p.get("bytes")).intValue());
            }
            long dayStart=Math.floorDiv(now,86400000)*86400000;
            if(count("select count(*) from pk_diagnostic where user_id=? and received_at>?",userId,now-60000)>=2 ||
                    count("select count(*) from pk_diagnostic where user_id=? and received_at>=?",userId,dayStart)>=10)
                throw error(429,"DIAGNOSTIC_QUOTA_EXCEEDED");
            if(count("select count(*) from pk_diagnostic")>=maxReports) throw error(429,"DIAGNOSTIC_CAPACITY_REACHED");
            String id=UUID.randomUUID().toString();
            db.update("insert into pk_diagnostic(id,user_id,upload_id,schema_version,app_version,build_time,started_at,ended_at,match_id,received_at,bytes,fingerprint,content) values(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    id,userId,uploadId,1,version,build,upload.startedAt(),upload.endedAt(),upload.matchId(),now,bytes,fingerprint,content);
            return new Receipt(id,uploadId,now,bytes);
        });
    }
    @Scheduled(fixedDelayString="${pk.diagnostics.cleanup-delay-ms:3600000}",initialDelayString="${pk.diagnostics.cleanup-delay-ms:3600000}")
    public void cleanup() {
        transactions.executeWithoutResult(status->{ lock(); db.update("delete from pk_diagnostic where received_at<?",System.currentTimeMillis()-RETENTION_MS); });
    }
    private Map<String,Object> view(Map<String,Object> row) {
        Map<String,Object> out=new LinkedHashMap<>();
        for(String key:List.of("id","user_id","upload_id","schema_version","app_version","build_time","started_at","ended_at","match_id","received_at","bytes")) {
            String[] parts=key.split("_"); StringBuilder name=new StringBuilder(parts[0]);
            for(int i=1;i<parts.length;i++) name.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
            out.put(name.toString(),row.get(key));
        }
        if(row.containsKey("content")) out.put("content",row.get("content"));
        return out;
    }
    public List<Map<String,Object>> list(Long userId,String matchId,int limit) {
        if(userId!=null && userId<=0 || matchId!=null && !DiagnosticSanitizer.identifier(matchId) || limit<1) throw DiagnosticSanitizer.invalid();
        String sql="select "+METADATA+" from pk_diagnostic where received_at>=?";
        List<Object> args=new ArrayList<>(); args.add(System.currentTimeMillis()-RETENTION_MS);
        if(userId!=null) { sql+=" and user_id=?"; args.add(userId); }
        if(matchId!=null) { sql+=" and match_id=?"; args.add(matchId); }
        sql+=" order by received_at desc,id desc limit ?"; args.add(Math.min(50,limit));
        return db.queryForList(sql,args.toArray()).stream().map(this::view).toList();
    }
    public Map<String,Object> detail(String id) {
        if(!DiagnosticSanitizer.uuid(id)) throw error(404,"DIAGNOSTIC_NOT_FOUND");
        List<Map<String,Object>> rows=db.queryForList("select "+METADATA+",content from pk_diagnostic where id=? and received_at>=?",id,System.currentTimeMillis()-RETENTION_MS);
        if(rows.isEmpty()) throw error(404,"DIAGNOSTIC_NOT_FOUND");
        return view(rows.get(0));
    }
}
