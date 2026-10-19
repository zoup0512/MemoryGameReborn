package com.memorygames.pk.user;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.ProxySelector;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

public class SensitiveWordNicknameProvider implements NicknameProvider {
    private static final URI CHECK_URI=URI.create("http://127.0.0.1:18081/api/v1/check");
    private final ObjectMapper json;
    private final HttpClient http;
    private final int timeoutMs;

    public SensitiveWordNicknameProvider(ObjectMapper json,int timeoutMs) {
        this(json,timeoutMs,HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(200,Math.min(timeoutMs,10000))))
                .proxy(ProxySelector.of(null)).followRedirects(HttpClient.Redirect.NEVER).build());
    }
    SensitiveWordNicknameProvider(ObjectMapper json,int timeoutMs,HttpClient http) {
        this.json=json; this.http=http; this.timeoutMs=Math.max(200,Math.min(timeoutMs,10000));
    }
    @Override public Result review(String nickname,String dataId) {
        try {
            String body=json.writeValueAsString(Map.of("text",nickname));
            HttpRequest request=HttpRequest.newBuilder(CHECK_URI).timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type","application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response=http.send(request,info -> new LimitedBodySubscriber());
            if(response.statusCode()!=200 || response.body()==null || response.body().getBytes(StandardCharsets.UTF_8).length>262144)
                return TencentNicknameProvider.unavailable();
            return parse(json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(response.body()),dataId);
        } catch(InterruptedException e) { Thread.currentThread().interrupt(); return TencentNicknameProvider.unavailable(); }
        catch(Exception e) { return TencentNicknameProvider.unavailable(); }
    }
    static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<String> {
        private final HttpResponse.BodySubscriber<String> delegate=HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);
        private Flow.Subscription subscription;
        private long bytes;
        private boolean done;
        @Override public CompletionStage<String> getBody() { return delegate.getBody(); }
        @Override public void onSubscribe(Flow.Subscription subscription) {
            this.subscription=subscription; delegate.onSubscribe(subscription);
        }
        @Override public void onNext(List<ByteBuffer> buffers) {
            if(done) return;
            for(ByteBuffer buffer:buffers) {
                bytes+=buffer.remaining();
                if(bytes>262144) {
                    done=true; subscription.cancel();
                    delegate.onError(new IllegalStateException("nickname moderation response too large"));
                    return;
                }
            }
            delegate.onNext(buffers);
        }
        @Override public void onError(Throwable error) { if(!done) { done=true; delegate.onError(error); } }
        @Override public void onComplete() { if(!done) { done=true; delegate.onComplete(); } }
    }
    static Result parse(JsonNode root,String dataId) {
        if(root==null || !root.isObject() || root.hasNonNull("error")
                || (root.has("code") && (!root.path("code").isIntegralNumber() || !root.path("code").canConvertToInt() || root.path("code").intValue()!=0))
                || (root.has("success") && !root.path("success").isBoolean())
                || (root.has("success") && !root.path("success").booleanValue()))
            return TencentNicknameProvider.unavailable();
        JsonNode contains=root.path("data").path("contains");
        if(!contains.isBoolean()) return TencentNicknameProvider.unavailable();
        String id=dataId!=null && dataId.matches("[A-Za-z0-9_-]{1,128}")?dataId:"";
        return new Result(contains.booleanValue()?Decision.REJECT:Decision.PASS,id);
    }
}
