package com.cube.memorygames.diagnostics;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.*;

/** Private app directory, four complete-record files. All disk work is off the caller thread. */
public final class DiagnosticLogStore {
    public static final int FILE_BYTES=128*1024, FILE_COUNT=4, SNAPSHOT_BYTES=192*1024;
    public static final long RETENTION_MS=7L*24*60*60*1000;
    public interface Callback { void done(Snapshot snapshot, Exception error); }
    public static final class Snapshot {
        public final String uploadId, content, matchId;
        public final long startedAt, endedAt;
        public final int bytes, records;
        Snapshot(String id,String text,String match,long start,long end,int count) {
            uploadId=id; content=text; matchId=match; startedAt=start; endedAt=end; records=count;
            bytes=text.getBytes(StandardCharsets.UTF_8).length;
        }
        public String requestJson(String appVersion,String buildTime) {
            return "{\"schemaVersion\":1,\"uploadId\":"+DiagnosticSanitizer.quote(uploadId)
                    +",\"appVersion\":"+DiagnosticSanitizer.quote(appVersion)+",\"buildTime\":"+DiagnosticSanitizer.quote(buildTime)
                    +",\"startedAt\":"+startedAt+",\"endedAt\":"+endedAt+",\"content\":"+DiagnosticSanitizer.quote(content)
                    +(matchId==null ? "" : ",\"matchId\":"+DiagnosticSanitizer.quote(matchId))+"}";
        }
    }
    private final File dir;
    private final String run=UUID.randomUUID().toString();
    private final AtomicLong sequence=new AtomicLong();
    private final AtomicLong dropped=new AtomicLong();
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<Runnable>(256),r -> { Thread t=new Thread(r,"diagnostic-log");t.setDaemon(true);return t; },new ThreadPoolExecutor.AbortPolicy());
    private long lastPrune;
    private static final Pattern TS=Pattern.compile("^\\{\"ts\":([0-9]+),");
    private static final Pattern MATCH=Pattern.compile("\"matchId\":\"([A-Za-z0-9]{12})\"");
    public DiagnosticLogStore(File directory) {
        dir=directory;
        execute(() -> { try { if (!dir.exists() && !dir.mkdirs()) throw new IOException(); prune(); cleanupPending(); } catch(Exception ignored) { } });
    }
    private File file(int index) { return new File(dir,"events-"+index+".ndjson"); }
    private File pending() { return new File(dir,"pending.bin"); }
    private boolean execute(Runnable work) { try { worker.execute(work);return true; } catch(RuntimeException ignored) { return false; } }
    public void log(String event,Object... fields) {
        try {
            final Object[] captured=fields==null ? new Object[0] : Arrays.copyOf(fields,Math.min(fields.length,40));
            if(!execute(() -> {
                try {
                    long lost=dropped.getAndSet(0);
                    if(lost>0) append(DiagnosticSanitizer.record(System.currentTimeMillis(),sequence.incrementAndGet(),run,"log_dropped","code",lost));
                    String record=DiagnosticSanitizer.record(System.currentTimeMillis(),sequence.incrementAndGet(),run,event,captured);
                    if(record!=null) append(record);
                } catch(Exception ignored) { }
            })) dropped.incrementAndGet();
        } catch(RuntimeException ignored) { }
    }
    private void append(String record) throws IOException {
        if(System.currentTimeMillis()-lastPrune>60*60*1000L) prune();
        byte[] bytes=record.getBytes(StandardCharsets.UTF_8);
        if(file(0).length()+bytes.length>FILE_BYTES) {
            if(file(3).exists() && !file(3).delete()) throw new IOException();
            for(int i=2;i>=0;i--) if(file(i).exists() && !file(i).renameTo(file(i+1))) throw new IOException();
        }
        try(FileOutputStream out=new FileOutputStream(file(0),true)) { out.write(bytes); }
    }
    private List<String> lines(File f) throws IOException {
        ArrayList<String> result=new ArrayList<>();
        if(!f.exists()) return result;
        if(f.length()>FILE_BYTES) { if(!f.delete()) throw new IOException();return result; }
        byte[] bytes=new byte[(int)f.length()];
        try(DataInputStream in=new DataInputStream(new FileInputStream(f))) { in.readFully(bytes); }
        int start=0;
        for(int i=0;i<bytes.length;i++) if(bytes[i]=='\n') {
            int size=i-start+1;
            if(size<=2048) try {
                String line=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes,start,size)).toString();
                if(timestamp(line)>=System.currentTimeMillis()-RETENTION_MS && line.endsWith("}\n")) result.add(line);
            } catch(CharacterCodingException ignored) { }
            start=i+1;
        }
        return result; // Crash-truncated tail is never reused or snapshotted.
    }
    private static long timestamp(String line) {
        Matcher m=TS.matcher(line);try {return m.find()?Long.parseLong(m.group(1)):0;}catch(RuntimeException e){return 0;}
    }
    private void prune() throws IOException {
        for(int i=0;i<FILE_COUNT;i++) if(file(i).exists()) {
            List<String> records=lines(file(i));
            if(records.isEmpty()) { if(!file(i).delete()) throw new IOException(); }
            else try(FileOutputStream out=new FileOutputStream(file(i))) {for(String line:records) out.write(line.getBytes(StandardCharsets.UTF_8));}
        }
        lastPrune=System.currentTimeMillis();
        try { readPending(); } catch(IOException ignored) { }
    }
    private void cleanupPending() { new File(dir,"pending.tmp").delete(); if(pending().length()>SNAPSHOT_BYTES+1024) pending().delete(); }
    public void freeze(Callback callback) {
        if(!execute(() -> {
            try {
                Snapshot existing=readPending();
                if(existing!=null) { callback.done(existing,null);return; }
                prune();
                ArrayList<String> records=new ArrayList<>();
                for(int i=FILE_COUNT-1;i>=0;i--) records.addAll(lines(file(i)));
                int size=0,escaped=2,start=records.size();
                for(int i=records.size()-1;i>=0;i--) {
                    String line=records.get(i);int n=line.getBytes(StandardCharsets.UTF_8).length;
                    int encoded=DiagnosticSanitizer.quote(line).getBytes(StandardCharsets.UTF_8).length-2;
                    if(size+n>SNAPSHOT_BYTES || escaped+encoded>250*1024) break;
                    size+=n;escaped+=encoded;start=i;
                }
                if(start==records.size()) throw new IOException("empty");
                StringBuilder content=new StringBuilder();String match=null;
                for(int i=start;i<records.size();i++) {String line=records.get(i);content.append(line);Matcher m=MATCH.matcher(line);if(m.find()) match=m.group(1);}
                Snapshot snapshot=new Snapshot(UUID.randomUUID().toString(),content.toString(),match,timestamp(records.get(start)),timestamp(records.get(records.size()-1)),records.size()-start);
                writePending(snapshot); callback.done(snapshot,null);
            } catch(Exception e) {callback.done(null,e);}
        })) callback.done(null,new IOException("busy"));
    }
    private void writePending(Snapshot s) throws IOException {
        File temp=new File(dir,"pending.tmp");byte[] bytes=s.content.getBytes(StandardCharsets.UTF_8);
        try(FileOutputStream out=new FileOutputStream(temp);DataOutputStream data=new DataOutputStream(out)) {
            data.writeInt(1);data.writeUTF(s.uploadId);data.writeUTF(s.matchId==null?"":s.matchId);data.writeLong(s.startedAt);data.writeLong(s.endedAt);data.writeInt(s.records);data.writeInt(bytes.length);data.write(bytes);data.flush();out.getFD().sync();
        }
        if(!temp.renameTo(pending())) {temp.delete();throw new IOException("pending");}
    }
    private Snapshot readPending() throws IOException {
        if(!pending().exists()) return null;
        cleanupPending();if(!pending().exists()) return null;
        Snapshot result;
        try(DataInputStream in=new DataInputStream(new FileInputStream(pending()))) {
            if(in.readInt()!=1) throw new IOException();
            String id=in.readUTF(),match=in.readUTF();UUID.fromString(id);
            long start=in.readLong(),end=in.readLong();int count=in.readInt(),n=in.readInt();
            if(n<=0 || n>SNAPSHOT_BYTES || count<=0 || start<=0 || end<start || (!match.isEmpty() && !match.matches("[A-Za-z0-9]{12}"))) throw new IOException();
            byte[] bytes=new byte[n];in.readFully(bytes);if(in.read()!=-1) throw new IOException();
            String content=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            if(!content.endsWith("}\n")) throw new IOException();
            result=new Snapshot(id,content,match.isEmpty()?null:match,start,end,count);
        } catch(Exception e) { pending().delete();throw new IOException("pending",e); }
        if(result.startedAt<System.currentTimeMillis()-RETENTION_MS) {
            if(!pending().delete()) throw new IOException("expired pending");
            return null;
        }
        return result;
    }
    /** Deletes only the immutable pending copy, never live rotating events. */
    public void uploaded(String uploadId,Runnable callback) {
        if(!execute(() -> {try {Snapshot s=readPending();if(s!=null && s.uploadId.equals(uploadId) && !pending().delete()) throw new IOException();}catch(Exception ignored){}finally{callback.run();}})) callback.run();
    }
    public boolean flush(long timeoutMs) {
        CountDownLatch latch=new CountDownLatch(1);if(!execute(latch::countDown)) return false;
        try{return latch.await(timeoutMs,TimeUnit.MILLISECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();return false;}
    }
    public void close() {worker.shutdown();try{worker.awaitTermination(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
}
