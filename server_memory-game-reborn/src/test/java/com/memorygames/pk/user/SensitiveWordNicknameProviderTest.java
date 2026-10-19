package com.memorygames.pk.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SensitiveWordNicknameProviderTest {
    private final ObjectMapper json=new ObjectMapper();
    private final HttpClient http=mock(HttpClient.class);
    private final SensitiveWordNicknameProvider provider=new SensitiveWordNicknameProvider(json,250,http);

    @SuppressWarnings("unchecked")
    private void respond(int status,String body) throws Exception {
        HttpResponse<String> response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status); when(response.body()).thenReturn(body);
        when(http.send(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(response);
    }
    @Test void sendsOnlyTextToFixedLoopbackCheckAndUsesStrictBoolean() throws Exception {
        respond(200,"{\"code\":0,\"message\":\"ok\",\"data\":{\"contains\":false,\"firstWord\":\"\",\"words\":[]}}");
        var result=provider.review("晴空\"小鹿","offline-id");
        assertEquals(NicknameProvider.Decision.PASS,result.decision()); assertEquals("offline-id",result.requestId());
        var capture=ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(capture.capture(),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        var request=capture.getValue();
        assertEquals("http://127.0.0.1:18081/api/v1/check",request.uri().toString());
        assertEquals("POST",request.method()); assertEquals(250,request.timeout().orElseThrow().toMillis());
        assertEquals("application/json; charset=utf-8",request.headers().firstValue("Content-Type").orElseThrow());
        assertEquals(json.readTree("{\"text\":\"晴空\\\"小鹿\"}"),json.readTree(body(request)));
        respond(200,"{\"data\":{\"contains\":true,\"firstWord\":\"private-word\",\"words\":[\"private-word\"]}}");
        var rejected=provider.review("测试昵称","offline-id");
        assertEquals(NicknameProvider.Decision.REJECT,rejected.decision());
        assertFalse(rejected.toString().contains("private-word"));
    }
    @Test void missingMalformedErrorAndNonBooleanResponsesFailClosed() throws Exception {
        for(String body:new String[]{"", "not-json", "null", "[]", "{}", "{\"contains\":false}",
                "{\"data\":null}","{\"data\":[]}","{\"data\":{\"contains\":\"false\"}}",
                "{\"data\":{\"contains\":0}}","{\"data\":{\"contains\":null}}",
                "{\"success\":false,\"data\":{\"contains\":false}}",
                "{\"error\":\"unavailable\",\"data\":{\"contains\":false}}",
                "{\"code\":500,\"data\":{\"contains\":false}}",
                "{\"code\":\"0\",\"data\":{\"contains\":false}}",
                "{\"code\":null,\"data\":{\"contains\":false}}",
                "{\"code\":4294967296,\"data\":{\"contains\":false}}",
                "{\"data\":{\"contains\":false}} {}"}) {
            respond(200,body);
            assertEquals(NicknameProvider.Decision.UNAVAILABLE,provider.review("测试昵称","offline").decision(),body);
        }
    }
    @Test void httpErrorsRedirectsLargeResponsesAndTimeoutFailClosed() throws Exception {
        for(int status:new int[]{201,302,400,429,500,503}) {
            respond(status,"{\"data\":{\"contains\":false}}");
            assertEquals(NicknameProvider.Decision.UNAVAILABLE,provider.review("测试昵称","offline").decision());
        }
        respond(200," ".repeat(262145));
        assertEquals(NicknameProvider.Decision.UNAVAILABLE,provider.review("测试昵称","offline").decision());
        when(http.send(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenThrow(new HttpTimeoutException("private body must not escape"));
        assertEquals(NicknameProvider.Decision.UNAVAILABLE,provider.review("测试昵称","offline").decision());
    }
    @Test void multibyteResponseOverByteLimitFailsClosed() throws Exception {
        respond(200,"{\"data\":{\"contains\":false},\"padding\":\""+"猫".repeat(100000)+"\"}");
        assertEquals(NicknameProvider.Decision.UNAVAILABLE,provider.review("测试昵称","offline").decision());
    }
    @Test void subscriberCancelsImmediatelyAtByteLimitAndIgnoresFurtherData() {
        var subscriber=new SensitiveWordNicknameProvider.LimitedBodySubscriber();
        var subscription=mock(Flow.Subscription.class);
        subscriber.onSubscribe(subscription);
        subscriber.onNext(java.util.List.of(ByteBuffer.wrap(new byte[262144])));
        verify(subscription,never()).cancel();
        subscriber.onNext(java.util.List.of(ByteBuffer.wrap("猫".getBytes(StandardCharsets.UTF_8))));
        verify(subscription).cancel();
        subscriber.onNext(java.util.List.of(ByteBuffer.wrap(new byte[100])));
        subscriber.onComplete();
        assertThrows(java.util.concurrent.CompletionException.class,()->subscriber.getBody().toCompletableFuture().join());
    }
    @Test void subscriberDecodesMultibyteCharactersAcrossChunks() {
        var subscriber=new SensitiveWordNicknameProvider.LimitedBodySubscriber();
        subscriber.onSubscribe(mock(Flow.Subscription.class));
        byte[] bytes="小猫".getBytes(StandardCharsets.UTF_8);
        for(byte b:bytes) subscriber.onNext(java.util.List.of(ByteBuffer.wrap(new byte[]{b})));
        subscriber.onComplete();
        assertEquals("小猫",subscriber.getBody().toCompletableFuture().join());
    }
    @Test void interruptionRestoresInterruptFlag() throws Exception {
        when(http.send(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenThrow(new InterruptedException());
        try {
            assertEquals(NicknameProvider.Decision.UNAVAILABLE,provider.review("测试昵称","offline").decision());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }
    @Test void productionClientNeverRedirectsOrUsesSystemProxyAndClampsTimeout() {
        var real=new SensitiveWordNicknameProvider(json,1);
        var client=(HttpClient)org.springframework.test.util.ReflectionTestUtils.getField(real,"http");
        assertNotNull(client); assertEquals(HttpClient.Redirect.NEVER,client.followRedirects());
        assertEquals(200,client.connectTimeout().orElseThrow().toMillis());
        assertEquals(java.net.Proxy.NO_PROXY,client.proxy().orElseThrow().select(java.net.URI.create("http://127.0.0.1:18081")).get(0));
        assertEquals(10000,org.springframework.test.util.ReflectionTestUtils.getField(new SensitiveWordNicknameProvider(json,20000),"timeoutMs"));
    }
    @Test void configurationSelectsOneProviderAndDisabledOrUnknownNeverAllow() {
        var context=new ApplicationContextRunner().withUserConfiguration(NicknameProviderConfig.class)
                .withBean(ObjectMapper.class,ObjectMapper::new);
        context.withPropertyValues("pk.nickname.mode=sensitive-word").run(c -> {
            assertEquals(1,c.getBeansOfType(NicknameProvider.class).size());
            assertInstanceOf(SensitiveWordNicknameProvider.class,c.getBean(NicknameProvider.class));
        });
        context.withPropertyValues("pk.nickname.mode=tencent").run(c -> assertInstanceOf(TencentNicknameProvider.class,c.getBean(NicknameProvider.class)));
        for(String mode:new String[]{"disabled","unknown","allow"}) context.withPropertyValues("pk.nickname.mode="+mode)
                .run(c -> assertEquals(NicknameProvider.Decision.UNAVAILABLE,c.getBean(NicknameProvider.class).review("测试昵称","offline").decision()));
        context.run(c -> assertEquals(NicknameProvider.Decision.UNAVAILABLE,c.getBean(NicknameProvider.class).review("测试昵称","offline").decision()));
    }
    private static String body(HttpRequest request) {
        var content=new StringBuilder(); var done=new CompletableFuture<String>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription s) { s.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer b) { content.append(StandardCharsets.UTF_8.decode(b)); }
            public void onError(Throwable t) { done.completeExceptionally(t); }
            public void onComplete() { done.complete(content.toString()); }
        });
        return done.join();
    }
}
