package com.memorygames.pk;

import com.fasterxml.jackson.databind.*;
import com.memorygames.pk.diagnostics.*;
import com.memorygames.pk.user.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Offline, unique H2 database; real Flyway V1-V7 and Hibernate validation, no deployed credentials. */
@org.springframework.context.annotation.Import(PassingNicknameTestConfig.class)
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:diagnostics-v7;DB_CLOSE_DELAY=-1",
        "spring.flyway.enabled=true","spring.jpa.hibernate.ddl-auto=validate","pk.auth.allow-http=false",
        "pk.admin-token=diagnostics-fixture-admin","pk.admin-rate-limit=10000","pk.diagnostics.max-reports=12",
        "logging.file.name=build/test-logs/diagnostics.log"})
@AutoConfigureMockMvc
class DiagnosticsIntegrationTest {
    private static final String PATH="/api/v2/diagnostics/uploads";
    private static final String ADMIN="diagnostics-fixture-admin";
    private static final String PASSWORD="diagnostics-fixture-password";
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("pk.admin-password-hash",()->new BCryptPasswordEncoder(4).encode(PASSWORD));
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserService users;
    @Autowired UserRepository repository;
    @Autowired AccountService accounts;
    @Autowired DiagnosticService diagnostics;
    @Autowired AccountApiGuard guard;
    @Autowired JdbcTemplate db;
    @BeforeEach void reset() { db.update("delete from pk_diagnostic"); }
    private UserAccount legacy() { return users.register(UUID.randomUUID().toString(),"Legacy tester"); }
    private DiagnosticService.Upload upload(String content) {
        return new DiagnosticService.Upload(1,UUID.randomUUID().toString(),"1.2.3","2026-10-14",100L,200L,content,"abcdef123456");
    }
    private DiagnosticService.Upload upload() { return upload("{\"event\":\"ws_connected\",\"ts\":100,\"seq\":1,\"matchId\":\"abcdef123456\"}\n"); }
    private MockHttpServletRequestBuilder postUpload(String token,DiagnosticService.Upload upload) throws Exception {
        var request=post(PATH).secure(true).contentType("application/json").content(json.writeValueAsBytes(upload));
        if(token!=null) request.header("Authorization","Bearer "+token);
        return request;
    }
    private JsonNode receipt(String token,DiagnosticService.Upload upload) throws Exception {
        return json.readTree(mvc.perform(postUpload(token,upload)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsByteArray());
    }
    @Test void migrationsAndLegacyAndV2IdentityAreServerOwned() throws Exception {
        assertEquals("7",db.queryForObject("select \"version\" from \"flyway_schema_history\" where \"version\"='7'",String.class));
        var old=legacy(); var a=receipt(old.token,upload());
        UUID.fromString(a.get("id").textValue()); assertTrue(a.get("receivedAt").longValue()>0);
        assertEquals(old.id,db.queryForObject("select user_id from pk_diagnostic where id=?",Long.class,a.get("id").textValue()));
        assertTrue(users.byToken(old.token).isPresent()); assertFalse(repository.findById(old.id).orElseThrow().legacyAuthDisabled);
        var v2=accounts.guest(UUID.randomUUID().toString(),AccountService.randomToken(),"V2 tester","Offline fixture");
        var b=receipt(v2.accessToken(),upload());
        assertEquals(v2.user().userId(),db.queryForObject("select user_id from pk_diagnostic where id=?",Long.class,b.get("id").textValue()));
        var body=json.valueToTree(upload()); ((com.fasterxml.jackson.databind.node.ObjectNode)body).put("userId",999);
        mvc.perform(post(PATH).secure(true).header("Authorization","Bearer "+old.token).contentType("application/json").content(json.writeValueAsBytes(body)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
    @Test void missingInvalidBannedExpiredRevokedAndDisabledAuthentication() throws Exception {
        mvc.perform(postUpload(null,upload())).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mvc.perform(postUpload("not-a-token",upload())).andExpect(status().isUnauthorized());
        var old=legacy(); users.setBan(old,1);
        mvc.perform(postUpload(old.token,upload())).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_BANNED"));
        db.update("update pk_user set legacy_auth_disabled=true where id=?",old.id);
        mvc.perform(postUpload(old.token,upload())).andExpect(status().isUnauthorized());
        var v2=accounts.guest(UUID.randomUUID().toString(),AccountService.randomToken(),"V2 tester","Offline fixture");
        db.update("update pk_session set access_expires_at=1 where id=?",v2.sessionId());
        mvc.perform(postUpload(v2.accessToken(),upload())).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
        db.update("update pk_session set revoked=true where id=?",v2.sessionId());
        mvc.perform(postUpload(v2.accessToken(),upload())).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
        assertEquals(0,db.queryForObject("select count(*) from pk_diagnostic",Integer.class));
    }
    @Test void httpsAndMediaAndDeclaredLimitsAndOtherV2PathsRemainSmall() throws Exception {
        mvc.perform(postUpload(legacy().token,upload()).secure(false).header("X-Forwarded-Proto","https"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("HTTPS_REQUIRED"))
                .andExpect(header().string("Cache-Control","no-store"));
        for(String type:List.of("text/plain","application/jsonp","application/json;charset=ISO-8859-1"))
            mvc.perform(post(PATH).secure(true).contentType(type).content("{}"))
                    .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        mvc.perform(post(PATH).secure(true).content("{}" )).andExpect(status().isUnsupportedMediaType());
        mvc.perform(post(PATH).secure(true).contentType("application/json").header("Content-Encoding","gzip").content("{}"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(post(PATH).secure(true).contentType("application/json").content(new byte[256*1024+1]))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
        for(String path:List.of("/api/v2/auth/guest",PATH+"/",PATH+"-other"))
            mvc.perform(post(path).secure(true).contentType("application/json").content(new byte[16385])).andExpect(status().isBadRequest());
        mvc.perform(put(PATH).secure(true).contentType("application/json").content(new byte[16385])).andExpect(status().isBadRequest());
    }
    @Test void explicitFalseRejectsHttpAvatarPatchButStillAllowsHttps() throws Exception {
        var guest=accounts.guest(UUID.randomUUID().toString(),AccountService.randomToken(),null,"Offline fixture");
        mvc.perform(patch("/api/v2/me/profile").secure(false).header("X-Forwarded-Proto","https")
                        .header("Authorization","Bearer "+guest.accessToken()).contentType("application/json")
                        .content("{\"avatarValue\":\"avatar_2\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("HTTPS_REQUIRED"))
                .andExpect(header().string("Cache-Control","no-store"));
        var unchanged=repository.findById(guest.user().userId()).orElseThrow();
        assertEquals("avatar_1",unchanged.avatarValue); assertEquals(guest.user().nickname(),unchanged.nickname);
        mvc.perform(patch("/api/v2/me/profile").secure(true)
                        .header("Authorization","Bearer "+guest.accessToken()).contentType("application/json")
                        .content("{\"avatarValue\":\"avatar_2\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.avatarValue").value("avatar_2"))
                .andExpect(jsonPath("$.nickname").value(guest.user().nickname()));
    }
    @Test void actualUnknownLengthIsBoundedAtLimitPlusOne() throws Exception {
        for(String path:List.of(PATH,"/api/v2/auth/guest")) {
            MockHttpServletRequest request=new MockHttpServletRequest("POST",path) {
                @Override public long getContentLengthLong() { return -1; }
                @Override public int getContentLength() { return -1; }
            };
            request.setSecure(true); request.setContentType("application/json"); request.setContent(new byte[300*1024]);
            MockHttpServletResponse response=new MockHttpServletResponse(); AtomicBoolean passed=new AtomicBoolean();
            guard.doFilter(request,response,(req,res)->passed.set(true));
            assertFalse(passed.get()); assertEquals(path.equals(PATH)?413:400,response.getStatus());
            assertEquals("no-store",response.getHeader("Cache-Control"));
            assertEquals(300*1024-(path.equals(PATH)?256*1024+1:16385),request.getInputStream().available());
        }
    }
    @Test void utf8ContentLimitAndWireUtf8Validation() throws Exception {
        var old=legacy();
        // UTF-8 is 3 bytes/character: wire JSON <256 KiB, decoded content >192 KiB.
        String content="{\"event\":\"fatal\",\"reason\":\""+"界".repeat(66000)+"\"}";
        mvc.perform(postUpload(old.token,upload(content))).andExpect(status().isPayloadTooLarge());
        String ascii="{\"event\":\"fatal\",\"reason\":\""+"a".repeat(180000)+"\"}";
        receipt(old.token,upload(ascii)); // Uploads really accept >16 KiB JSON.
        mvc.perform(post(PATH).secure(true).contentType("application/json").content(new byte[]{'{',(byte)0xc3,0x28,'}'}))
                .andExpect(status().isBadRequest());
    }
    @Test void contractRejectsBadTypesVersionsIdsAndMalformedNdjson() throws Exception {
        var old=legacy();
        for(String content:List.of("not json","[]","{\"event\":\"unknown_event\"}","{\"event\":\"fatal\"}{}","{\"event\":\"fatal\",\"event\":\"ws_error\"}"))
            mvc.perform(postUpload(old.token,upload(content))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        for(String key:List.of("schemaVersion","uploadId","appVersion","buildTime","startedAt","endedAt","content")) {
            var body=(com.fasterxml.jackson.databind.node.ObjectNode)json.valueToTree(upload()); body.set(key,json.createObjectNode());
            mvc.perform(post(PATH).secure(true).header("Authorization","Bearer "+old.token).contentType("application/json").content(json.writeValueAsBytes(body))).andExpect(status().isBadRequest());
        }
        for(var bad:List.of(new DiagnosticService.Upload(2,UUID.randomUUID().toString(),"v","b",1L,2L,"{}",null),
                new DiagnosticService.Upload(1,"bad-id","v","b",1L,2L,"{}",null),
                new DiagnosticService.Upload(1,UUID.randomUUID().toString(),"a".repeat(81),"b",1L,2L,"{}",null),
                new DiagnosticService.Upload(1,UUID.randomUUID().toString(),"v","b",3L,2L,"{}",null)))
            mvc.perform(postUpload(old.token,bad)).andExpect(status().isBadRequest());
    }
    @Test void strictWhitelistDropsPayloadSecretsUrlsAndExceptionMessagesButPreservesTraceIds() throws Exception {
        var old=legacy(); String run=UUID.randomUUID().toString();
        String content=json.writeValueAsString(Map.of("event","frame_received","run",run,"matchId","abcdef123456","reason","round_start",
                "payload",Map.of("token",old.token),"token",old.token,"remote","https://host/ws?token=private","state",Map.of("secret","bad")))+"\n"+
                json.writeValueAsString(Map.of("event","fatal","reason","Bearer private","stack","java.lang.IllegalStateException: SECRET\ncom.cube.Game.run:42\ncom.cube.Token.value:3","password","SECRET"));
        var r=receipt(old.token,upload(content));
        String stored=db.queryForObject("select content from pk_diagnostic where id=?",String.class,r.get("id").textValue());
        assertNotNull(stored); assertTrue(stored.contains(run)); assertTrue(stored.contains("abcdef123456")); assertTrue(stored.contains("round_start"));
        assertTrue(stored.contains("com.cube.Game.run:42"));
        for(String secret:List.of(old.token,"payload","password","SECRET","https://","private","Token","IllegalStateException")) assertFalse(stored.contains(secret),secret);
        assertEquals(stored.getBytes(StandardCharsets.UTF_8).length,r.get("bytes").intValue());
        assertFalse(json.readTree(stored.split("\n")[0]).has("state"));
        String game=json.writeValueAsString(Map.of("event","game_launch","gameId","22","state",3,"stack","com.cube.Game.<init>:-1\ncom.cube.Game.<clinit>:-2\ncom.cube.Game.run:42"));
        JsonNode gameReceipt=receipt(old.token,upload(game));
        String gameStored=db.queryForObject("select content from pk_diagnostic where id=?",String.class,gameReceipt.get("id").textValue());
        JsonNode dropped=json.readTree(new DiagnosticSanitizer(json).sanitize("{\"event\":\"log_dropped\",\"code\":123}"));
        assertEquals(123,dropped.get("code").intValue());
        JsonNode parsed=json.readTree(gameStored);
        assertEquals("22",parsed.get("gameId").textValue()); assertEquals(3,parsed.get("state").intValue());
        assertTrue(parsed.get("stack").textValue().contains("<init>:-1")); assertTrue(parsed.get("stack").textValue().contains("<clinit>:-2"));
    }
    @Test void sequentialIdempotencyAndChangedOriginalConflictWithoutExtraQuota() throws Exception {
        var old=legacy(); var input=upload(); JsonNode first=receipt(old.token,input);
        assertEquals(first,receipt(old.token,input));
        var changed=new DiagnosticService.Upload(1,input.uploadId(),input.appVersion(),input.buildTime(),input.startedAt(),input.endedAt(),input.content()+" ",input.matchId());
        mvc.perform(postUpload(old.token,changed)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DIAGNOSTIC_CONFLICT"));
        assertEquals(1,db.queryForObject("select count(*) from pk_diagnostic",Integer.class));
        assertEquals(first,receipt(old.token,input));
        assertNotEquals(first.get("id"),receipt(legacy().token,input).get("id")); // Scoped by authenticated user.
    }
    @Test void concurrentIdenticalAndDifferentUploadsSerialize() throws Exception {
        var old=legacy(); var same=upload(); ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start=new CountDownLatch(1);
            Callable<DiagnosticService.Receipt> action=()->{ start.await(); return diagnostics.upload(old.id,same); };
            Future<DiagnosticService.Receipt> a=pool.submit(action),b=pool.submit(action); start.countDown();
            assertEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
            assertEquals(1,db.queryForObject("select count(*) from pk_diagnostic",Integer.class));
            db.update("delete from pk_diagnostic");
            var changed=new DiagnosticService.Upload(1,same.uploadId(),"different",same.buildTime(),100L,200L,same.content(),same.matchId());
            CountDownLatch next=new CountDownLatch(1);
            Callable<Object> left=()->{ next.await(); try { return diagnostics.upload(old.id,same); } catch(AccountException ex) { return ex.code; } };
            Callable<Object> right=()->{ next.await(); try { return diagnostics.upload(old.id,changed); } catch(AccountException ex) { return ex.code; } };
            Future<Object> l=pool.submit(left),r=pool.submit(right); next.countDown();
            List<Object> result=List.of(l.get(10,TimeUnit.SECONDS),r.get(10,TimeUnit.SECONDS));
            assertTrue(result.contains("DIAGNOSTIC_CONFLICT")); assertEquals(1,result.stream().filter(v->v instanceof DiagnosticService.Receipt).count());
        } finally { pool.shutdownNow(); }
    }
    @Test void minuteAndUtcDayQuotasAndRetrySuccess() throws Exception {
        var old=legacy(); var same=upload(); JsonNode first=receipt(old.token,same); receipt(old.token,upload());
        mvc.perform(postUpload(old.token,upload())).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("DIAGNOSTIC_QUOTA_EXCEEDED"));
        assertEquals(first,receipt(old.token,same));
        // Spread accepted reports over the current UTC day without sleeping.
        long now=System.currentTimeMillis(),day=Math.floorDiv(now,86400000)*86400000;
        db.update("update pk_diagnostic set received_at=?",Math.max(day,now-120000));
        for(int i=2;i<10;i++) {
            diagnostics.upload(old.id,upload());
            db.update("update pk_diagnostic set received_at=? where user_id=?",Math.max(day,System.currentTimeMillis()-120000),old.id);
        }
        mvc.perform(postUpload(old.token,upload())).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("DIAGNOSTIC_QUOTA_EXCEEDED"));
        assertEquals(10,db.queryForObject("select count(*) from pk_diagnostic where user_id=?",Integer.class,old.id));
    }
    @Test void globalCapacityCleanupAndExpiredReportsInvisible() throws Exception {
        String expiredId=null;
        for(int i=0;i<12;i++) { var r=diagnostics.upload(legacy().id,upload()); if(i==0) expiredId=r.id(); }
        var fresh=legacy();
        mvc.perform(postUpload(fresh.token,upload())).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("DIAGNOSTIC_CAPACITY_REACHED"));
        db.update("update pk_diagnostic set received_at=? where id=?",System.currentTimeMillis()-15L*86400000,expiredId);
        mvc.perform(get("/api/admin/diagnostics/"+expiredId).header("X-Admin-Token",ADMIN)).andExpect(status().isNotFound());
        assertEquals(11,diagnostics.list(null,null,50).size()); diagnostics.cleanup();
        assertEquals(11,db.queryForObject("select count(*) from pk_diagnostic",Integer.class));
        receipt(fresh.token,upload()); assertEquals(12,db.queryForObject("select count(*) from pk_diagnostic",Integer.class));
        db.update("update pk_diagnostic set received_at=1"); // Upload also reclaims expired capacity.
        receipt(legacy().token,upload()); assertEquals(1,db.queryForObject("select count(*) from pk_diagnostic",Integer.class));
    }
    @Test void adminPermissionsFiltersListWithoutBodyDetailsAndCookieSession() throws Exception {
        var old=legacy(); var r=receipt(old.token,upload()); String id=r.get("id").textValue();
        for(String path:List.of("/api/admin/diagnostics","/api/admin/diagnostics/"+id)) {
            mvc.perform(get(path)).andExpect(status().isNotFound()).andExpect(header().string("Cache-Control","no-store"));
            mvc.perform(get(path).header("Authorization","Bearer "+old.token)).andExpect(status().isNotFound());
            mvc.perform(get(path).header("X-Admin-Token","wrong")).andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/admin/diagnostics").header("X-Admin-Token",ADMIN).param("userId",Long.toString(old.id)).param("matchId","abcdef123456").param("limit","500"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reports.length()").value(1)).andExpect(jsonPath("$.reports[0].userId").value(old.id))
                .andExpect(jsonPath("$.reports[0].content").doesNotExist()).andExpect(jsonPath("$.reports[0].fingerprint").doesNotExist());
        mvc.perform(get("/api/admin/diagnostics").header("X-Admin-Token",ADMIN).param("userId","999999")).andExpect(jsonPath("$.reports.length()").value(0));
        mvc.perform(get("/api/admin/diagnostics/"+id).header("X-Admin-Token",ADMIN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isString()).andExpect(jsonPath("$.matchId").value("abcdef123456"));
        mvc.perform(get("/api/admin/diagnostics").header("X-Admin-Token",ADMIN).param("limit","0")).andExpect(status().isBadRequest());
        var login=mvc.perform(post("/api/admin/auth/login").secure(true).contentType("application/json").content("{\"password\":\""+PASSWORD+"\"}"))
                .andExpect(status().isOk()).andReturn();
        String setCookie=login.getResponse().getHeader("Set-Cookie"); assertNotNull(setCookie);
        Cookie cookie=new Cookie("PK_ADMIN_SESSION",setCookie.substring(setCookie.indexOf('=')+1,setCookie.indexOf(';')));
        mvc.perform(get("/api/admin/diagnostics/"+id).secure(true).cookie(cookie)).andExpect(status().isOk());
    }
}
