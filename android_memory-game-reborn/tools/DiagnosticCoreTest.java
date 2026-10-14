import com.cube.memorygames.diagnostics.*;
import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public final class DiagnosticCoreTest {
    private static int checks;
    private static void check(boolean condition,String label) {if(!condition)throw new AssertionError(label);checks++;}
    private static DiagnosticLogStore.Snapshot freeze(DiagnosticLogStore store) throws Exception {
        final DiagnosticLogStore.Snapshot[] result=new DiagnosticLogStore.Snapshot[1];final Exception[] error=new Exception[1];CountDownLatch latch=new CountDownLatch(1);
        store.freeze((s,e)->{result[0]=s;error[0]=e;latch.countDown();});check(latch.await(5,TimeUnit.SECONDS),"freeze callback");if(error[0]!=null)throw error[0];return result[0];
    }
    private static void discard(DiagnosticLogStore store,String id) throws Exception {CountDownLatch latch=new CountDownLatch(1);store.uploaded(id,latch::countDown);check(latch.await(5,TimeUnit.SECONDS),"discard callback");}
    private static void fill(DiagnosticLogStore store,int count) {
        String stack=String.join("\n",Collections.nCopies(8,"com.cube.memorygames.games.Game1MemoryGridActivity.showOnlineReplayDialog:1476"));
        for(int i=0;i<count;i++){store.log("game_round_finished","matchId","ab12cd34ef56","gameId","0","score",i,"stack",stack);if(i%100==0)store.flush(3000);}
        check(store.flush(3000),"flush");
    }
    public static void main(String[] args) throws Exception {
        String run=UUID.randomUUID().toString();
        String sanitized=DiagnosticSanitizer.record(System.currentTimeMillis(),1,run,"ws_error","token","BEARER_SECRET","installationId",UUID.randomUUID().toString(),"nickname","SECRET_NAME","email","secret@example.com","password","PASSWORD_SECRET","reason","https://host?token=SECRET","stack","java.lang.Exception: PASSWORD_SECRET","matchId","ab12cd34ef56","gameId","0");
        check(!sanitized.contains("SECRET")&&!sanitized.contains("example.com")&&!sanitized.contains("installationId")&&!sanitized.contains("nickname")&&!sanitized.contains("password"),"secret filtering");
        check(sanitized.contains("ab12cd34ef56")&&sanitized.contains(run),"business identifiers preserved");
        check(DiagnosticSanitizer.record(1,1,run,"untrusted", "reason","abc")==null,"event whitelist");
        check(!DiagnosticSanitizer.record(1,1,run,"ws_error","reason","a.b.token").contains("reason"),"free dotted secrets rejected");
        check(!DiagnosticSanitizer.record(1,1,run,"frame_received","reason","auth").contains("reason"),"auth frames excluded");
        check(DiagnosticSanitizer.record(1,1,run,"frame_received","reason","round_start").contains("round_start"),"safe frame type");
        check(DiagnosticSanitizer.quote("诊断日志🙂\n").getBytes(StandardCharsets.UTF_8).length>10,"UTF8 multibyte encoding");
        check(DiagnosticSanitizer.record(1,1,run,"ws_error","stack",String.join("",Collections.nCopies(3000,"a"))).getBytes(StandardCharsets.UTF_8).length<=2048,"oversized field and record bound");
        StackTraceElement[] frames=new StackTraceElement[20];Arrays.fill(frames,new StackTraceElement("a.b.C","method","SECRET.java",123));
        String stack=DiagnosticSanitizer.stack(frames);check(stack.split("\n").length==8&&!stack.contains("SECRET"),"bounded message-free stack");
        Path path=Files.createTempDirectory("diagnostics-test");DiagnosticLogStore store=new DiagnosticLogStore(path.toFile());
        try {
            fill(store,1200);
            for(int i=0;i<4;i++)check(Files.size(path.resolve("events-"+i+".ndjson"))<=128*1024,"rotation file bound");
            check(Files.list(path).filter(p->p.toString().endsWith("ndjson")).count()==4,"four file rotation");
            DiagnosticLogStore.Snapshot s=freeze(store);check(s.bytes<=192*1024,"snapshot limit");check(s.records>0&&s.content.endsWith("}\n"),"snapshot complete records");
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(s.content.getBytes(StandardCharsets.UTF_8)));checks++;
            check(s.requestJson("3.6.3","2026-10-14 12:00:00").getBytes(StandardCharsets.UTF_8).length<=256*1024,"escaped total request budget");
            store.log("round_complete","matchId","ab12cd34ef56","score",999999);store.flush(3000);
            DiagnosticLogStore.Snapshot retry=freeze(store);check(s.uploadId.equals(retry.uploadId)&&s.content.equals(retry.content),"failed retry stable snapshot UUID");
            store.close();
            Files.write(path.resolve("events-0.ndjson"),"{\"ts\":123,\"token\":\"TRUNCATED_SECRET".getBytes(StandardCharsets.UTF_8),StandardOpenOption.APPEND);
            store=new DiagnosticLogStore(path.toFile());retry=freeze(store);check(s.uploadId.equals(retry.uploadId),"retry UUID stable across restart");
            discard(store,s.uploadId);DiagnosticLogStore.Snapshot newer=freeze(store);
            check(newer.content.contains("999999"),"logs appended during upload retained");check(!newer.content.contains("TRUNCATED_SECRET"),"restart truncates residual tail");
            check(!s.uploadId.equals(newer.uploadId),"success next snapshot UUID changes");discard(store,newer.uploadId);store.close();
            long old=System.currentTimeMillis()-DiagnosticLogStore.RETENTION_MS-1000;
            Files.write(path.resolve("events-3.ndjson"),DiagnosticSanitizer.record(old,1,run,"application_start").getBytes(StandardCharsets.UTF_8));
            store=new DiagnosticLogStore(path.toFile());store.log("application_start");store.flush(3000);s=freeze(store);check(!s.content.contains("\"ts\":"+old),"seven day pruning");
            store.close();
            Path pending=path.resolve("pending.bin");try(RandomAccessFile f=new RandomAccessFile(pending.toFile(),"rw")){f.readInt();f.readUTF();f.readUTF();f.writeLong(old);}
            store=new DiagnosticLogStore(path.toFile());newer=freeze(store);check(!s.uploadId.equals(newer.uploadId),"expired pending discarded");
            discard(store,newer.uploadId);check(!Files.exists(path.resolve("pending.bin")),"cancel removes pending without upload");
            for(int i=0;i<30000;i++)store.log("application_start");
            Thread.sleep(200);check(store.flush(5000),"bounded queue drains");
            store.log("application_start");store.flush(3000);newer=freeze(store);check(newer.content.contains("log_dropped"),"queue saturation counted safely");
        } finally {store.close();try(java.util.stream.Stream<Path> files=Files.walk(path)){files.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException e){throw new RuntimeException(e);}});}}
        check(DiagnosticUploadPolicy.destination("ws://124.220.35.87:8091/ws",false).equals(DiagnosticUploadPolicy.TRUSTED_BASE),"legacy pinned HTTPS mapping");
        check(DiagnosticUploadPolicy.destination("wss://example.org:8443/ws",true).equals("https://example.org:8443/api/v2"),"wss same-origin HTTPS");
        for(String bad:Arrays.asList("ws://example.org/ws","ws://124.220.35.87:8092/ws","wss://example.org/ws?token=x","http://example.org/ws")) {
            boolean rejected=false;try{DiagnosticUploadPolicy.destination(bad,false);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"reject insecure or decorated origin");
        }
        System.out.println("PASS diagnostic core: "+checks+" assertions");
    }
}
