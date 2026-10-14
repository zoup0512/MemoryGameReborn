package com.cube.memorygames.api.online;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.cube.memorygames.diagnostics.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.memory.brain.training.games.BuildConfig;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Manual-only uploader, one preparation/confirmation/upload at a time. Never switches the online endpoint. */
public final class DiagnosticUploader {
    public interface Preparation { void ready(Prepared value); void failed(String message); }
    public interface Result { void complete(String id); void failed(String message); }
    public static final class Prepared {
        public final DiagnosticLogStore.Snapshot snapshot;
        public final String destination;
        private final String endpoint;
        private final boolean v2;
        private final AtomicBoolean consumed=new AtomicBoolean();
        private Prepared(DiagnosticLogStore.Snapshot s,String dest,String endpoint,boolean v2) {snapshot=s;destination=dest;this.endpoint=endpoint;this.v2=v2;}
    }
    private static final AtomicBoolean busy=new AtomicBoolean();
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(2),r -> {Thread t=new Thread(r,"diagnostic-upload");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private DiagnosticUploader() { }
    public static void prepare(Context context,Preparation callback) {
        if(!busy.compareAndSet(false,true)) {callback.failed("正在准备或上传日志，请勿重复点击");return;}
        final Context app=context.getApplicationContext();
        try { worker.execute(() -> {
            try {
                String endpoint=OnlineConfig.getEndpoint(app);boolean v2=AccountManager.get(app).usesV2();
                String destination=DiagnosticUploadPolicy.destination(endpoint,v2);
                if(!v2 && (!OnlineIdentity.hasLegacy(app) || OnlineIdentity.getToken(app)==null)) throw new IllegalStateException("请先进入在线大厅建立游客或登录账号，再上传日志");
                DiagnosticLogStore store=DiagnosticLog.store();if(store==null) throw new IllegalStateException("日志暂不可用");
                store.freeze((snapshot,error) -> main.post(() -> {
                    if(error!=null) {busy.set(false);callback.failed("日志为空或暂不可读，请稍后重试");}
                    else callback.ready(new Prepared(snapshot,destination,endpoint,v2));
                }));
            } catch(Exception e) {main.post(() -> {busy.set(false);callback.failed(e instanceof IllegalArgumentException || e instanceof IllegalStateException ? e.getMessage() : "日志准备失败，未上传");});}
        });}catch(RuntimeException e){busy.set(false);callback.failed("日志后台繁忙，未上传");}
    }
    public static void cancel(Prepared prepared) {
        if(prepared.consumed.compareAndSet(false,true)) {
            DiagnosticLogStore store=DiagnosticLog.store();
            if(store==null) busy.set(false);
            else store.uploaded(prepared.snapshot.uploadId,() -> busy.set(false));
        }
    }
    public static void upload(Context context,Prepared prepared,Result callback) {
        if(!prepared.consumed.compareAndSet(false,true)) return;
        final Context app=context.getApplicationContext();
        try {worker.execute(() -> {
            try {
                if(!prepared.endpoint.equals(OnlineConfig.getEndpoint(app)) || prepared.v2!=AccountManager.get(app).usesV2()) throw new IllegalStateException("账户环境已变化，请重新确认上传");
                DiagnosticUploadPolicy.requireHttps(prepared.destination);
                DiagnosticLogStore.Snapshot s=prepared.snapshot;
                String text=s.requestJson(BuildConfig.VERSION_NAME,BuildConfig.BUILD_TIME);
                if(text.getBytes("UTF-8").length>256*1024 || s.bytes>192*1024) throw new IllegalStateException("日志请求过大，未上传");
                JsonObject data=new JsonParser().parse(text).getAsJsonObject();
                if(prepared.v2) {
                    AccountManager.get(app).uploadDiagnostics(prepared.destination,data,new AccountManager.Callback<JsonObject>() {
                        public void onSuccess(JsonObject response) {finish(prepared,response,callback);}
                        public void onError(String message) {failure(callback,"上传失败，请检查在线身份或网络；原快照保留，可重试");}
                    });
                } else {
                    String token=OnlineIdentity.getToken(app);
                    if(token==null || token.isEmpty()) throw new IllegalStateException("请先进入在线大厅建立游客或登录账号，再上传日志");
                    JsonObject response=new AccountRestClient().request(prepared.destination,"POST",DiagnosticUploadPolicy.PATH,data,token);
                    finish(prepared,response,callback);
                }
            } catch(Exception e) {failure(callback,"上传失败或响应无效；原快照保留，请检查在线身份或网络后重试");}
        });}catch(RuntimeException e){failure(callback,"上传后台繁忙，原快照保留");}
    }
    private static void failure(Result callback,String message) {main.post(() -> {busy.set(false);callback.failed(message);});}
    private static void finish(Prepared prepared,JsonObject response,Result callback) {
        try {
            String id=response.get("id").getAsString(),uploadId=response.get("uploadId").getAsString();
            if(!response.get("id").isJsonPrimitive() || !response.get("id").getAsJsonPrimitive().isString()
                    || !response.get("uploadId").isJsonPrimitive() || !response.get("uploadId").getAsJsonPrimitive().isString()
                    || !response.get("receivedAt").isJsonPrimitive() || !response.get("receivedAt").getAsJsonPrimitive().isNumber()
                    || !response.get("receivedAt").getAsString().matches("[0-9]{1,19}")
                    || !response.get("bytes").isJsonPrimitive() || !response.get("bytes").getAsJsonPrimitive().isNumber()
                    || !response.get("bytes").getAsString().matches("[0-9]{1,9}")
                    || !UUID.fromString(id).toString().equals(id) || !UUID.fromString(uploadId).toString().equals(uploadId)
                    || !prepared.snapshot.uploadId.equals(uploadId) || response.get("receivedAt").getAsLong()<=0
                    || response.get("bytes").getAsLong()<=0 || response.get("bytes").getAsLong()>DiagnosticLogStore.SNAPSHOT_BYTES) throw new IllegalStateException();
            DiagnosticLog.store().uploaded(uploadId,() -> main.post(() -> {busy.set(false);callback.complete(id);}));
        } catch(Exception e) {failure(callback,"上传响应校验失败；原快照保留，可用同一上传编号重试");}
    }
}
