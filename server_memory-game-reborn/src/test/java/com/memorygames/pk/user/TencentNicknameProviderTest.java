package com.memorygames.pk.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TencentNicknameProviderTest {
    private final ObjectMapper json=new ObjectMapper();
    @Test void disabledMissingCredentialsUnknownModeNeverCallNetwork() {
        for(String mode:new String[]{"disabled","test","allow","unknown","tencent"}) {
            var provider=new TencentNicknameProvider(json,mode,"","","ap-guangzhou","","",200);
            assertEquals(NicknameProvider.Decision.UNAVAILABLE,provider.review("小猫小鱼","offline").decision());
        }
    }
    @Test void parseStrictlyRecognizesTencentSuggestionsAndErrors() throws Exception {
        for(String suggestion:new String[]{"Pass","Block","Review","unknown","pass",""}) {
            var result=TencentNicknameProvider.parse(json.readTree("{\"Response\":{\"RequestId\":\"offline-id\",\"Suggestion\":\""+suggestion+"\"}}"));
            assertEquals(switch(suggestion) { case "Pass" -> NicknameProvider.Decision.PASS; case "Block" -> NicknameProvider.Decision.REJECT; case "Review" -> NicknameProvider.Decision.PENDING; default -> NicknameProvider.Decision.UNAVAILABLE; },result.decision());
        }
        assertEquals(NicknameProvider.Decision.UNAVAILABLE,TencentNicknameProvider.parse(json.readTree("{\"Response\":{\"RequestId\":\"offline-id\",\"Suggestion\":\"Pass\",\"Error\":{\"Code\":\"AuthFailure\"}}}")).decision());
        assertEquals(NicknameProvider.Decision.UNAVAILABLE,TencentNicknameProvider.parse(json.readTree("{\"Response\":{\"Suggestion\":\"Pass\"}}")).decision());
    }
    @Test void configuredHttpTimeoutFailsClosedWithoutRealNetwork() throws Exception {
        var http=org.mockito.Mockito.mock(java.net.http.HttpClient.class);
        var provider=new TencentNicknameProvider(json,"tencent","offline-id","offline-key","ap-guangzhou","offline-policy","",250);
        org.springframework.test.util.ReflectionTestUtils.setField(provider,"http",http);
        org.mockito.Mockito.when(http.send(org.mockito.ArgumentMatchers.any(java.net.http.HttpRequest.class),org.mockito.ArgumentMatchers.<java.net.http.HttpResponse.BodyHandler<String>>any()))
                .thenThrow(new java.net.http.HttpTimeoutException("private body must not escape"));
        assertEquals(NicknameProvider.Decision.UNAVAILABLE,provider.review("测试昵称","offline").decision());
        var capture=org.mockito.ArgumentCaptor.forClass(java.net.http.HttpRequest.class);
        org.mockito.Mockito.verify(http).send(capture.capture(),org.mockito.ArgumentMatchers.<java.net.http.HttpResponse.BodyHandler<String>>any());
        assertEquals("https://tms.tencentcloudapi.com/",capture.getValue().uri().toString());
        assertEquals(250,capture.getValue().timeout().orElseThrow().toMillis());
        assertEquals("TextModeration",capture.getValue().headers().firstValue("X-TC-Action").orElseThrow());
    }
    @Test void tc3SignatureMatchesIndependentFixture() throws Exception {
        // Fixture generated independently with Python hashlib/hmac, no credentials or service access.
        String signed=TencentNicknameProvider.authorization("offline-id","offline-key",1728000000L,"{\"Content\":\"5bCP54yr\"}");
        assertEquals("TC3-HMAC-SHA256 Credential=offline-id/2024-10-04/tms/tc3_request, SignedHeaders=content-type;host, Signature=1a276c7b56bd2fc8cf0cd054e86d14e5b1c5ce160ee9faaad46a22cbfa3c7d23",signed);
    }
}
