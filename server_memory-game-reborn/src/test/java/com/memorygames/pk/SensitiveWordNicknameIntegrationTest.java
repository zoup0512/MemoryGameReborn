package com.memorygames.pk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memorygames.pk.user.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.net.http.*;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.datasource.url=jdbc:h2:mem:sensitive-word-tests;DB_CLOSE_DELAY=-1",
        "pk.nickname.mode=sensitive-word","pk.auth.allow-http=true"})
@ActiveProfiles("test")
class SensitiveWordNicknameIntegrationTest {
    @Autowired AccountService accounts;
    @Autowired UserRepository users;
    @Autowired NicknameProvider provider;
    @Autowired ObjectMapper json;
    @LocalServerPort int port;
    private HttpClient moderationHttp;
    @BeforeEach void setup() {
        assertInstanceOf(SensitiveWordNicknameProvider.class,provider);
        moderationHttp=mock(HttpClient.class);
        ReflectionTestUtils.setField(provider,"http",moderationHttp);
    }
    @SuppressWarnings("unchecked")
    private void respond(int status,String body) throws Exception {
        HttpResponse<String> response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status); when(response.body()).thenReturn(body);
        when(moderationHttp.send(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(response);
    }
    private AccountService.AuthResponse guest() {
        return accounts.guest(UUID.randomUUID().toString(),AccountService.randomToken(),null,"Offline");
    }
    private HttpResponse<String> patch(String token,String body) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v2/me/profile"))
                .header("Authorization","Bearer "+token).header("Content-Type","application/json")
                .method("PATCH",HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void cleanNicknameIsSavedAfterRealProfileEndpointChecksServiceResponse() throws Exception {
        var g=guest(); respond(200,"{\"data\":{\"contains\":false}}");
        var response=patch(g.accessToken(),"{\"nickname\":\"山间晨光\",\"avatarValue\":\"avatar_4\"}");
        assertEquals(200,response.statusCode()); assertEquals("山间晨光",json.readTree(response.body()).path("nickname").asText());
        var row=users.findById(g.user().userId()).orElseThrow();
        assertEquals("山间晨光",row.nickname); assertEquals("avatar_4",row.avatarValue); assertTrue(row.nicknameChangedAt>0);
        verify(moderationHttp).send(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    }
    @Test void hitUnavailableAndMalformedReplyNeverSaveNicknameAvatarOrCooldown() throws Exception {
        for(String body:new String[]{"{\"data\":{\"contains\":true,\"firstWord\":\"private-word\"}}","{}","{\"data\":{\"contains\":false}}"}) {
            var g=guest(); boolean hit=body.contains("true");
            respond(body.contains("false")?503:200,body);
            var response=patch(g.accessToken(),"{\"nickname\":\"测试新昵称\",\"avatarValue\":\"avatar_8\"}");
            assertEquals(hit?422:503,response.statusCode());
            assertEquals(hit?"NICKNAME_REJECTED":"NICKNAME_MODERATION_UNAVAILABLE",json.readTree(response.body()).path("code").asText());
            assertFalse(response.body().contains("private-word"));
            var row=users.findById(g.user().userId()).orElseThrow();
            assertEquals(g.user().nickname(),row.nickname); assertEquals("avatar_1",row.avatarValue); assertEquals(0,row.nicknameChangedAt);
        }
    }
    @Test void avatarOnlyAndSameNicknameDoNotDependOnSensitiveWordService() throws Exception {
        var g=guest();
        assertEquals(200,patch(g.accessToken(),"{\"avatarValue\":\"avatar_2\"}").statusCode());
        assertEquals(200,patch(g.accessToken(),json.writeValueAsString(java.util.Map.of("nickname",g.user().nickname(),"avatarValue","avatar_3"))).statusCode());
        verifyNoInteractions(moderationHttp);
    }
    @Test void customNewGuestCannotBypassSensitiveWordService() throws Exception {
        respond(200,"{\"data\":{\"contains\":true}}");
        String installation=UUID.randomUUID().toString();
        var e=assertThrows(AccountException.class,()->accounts.guest(installation,AccountService.randomToken(),"测试游客昵称","Offline"));
        assertEquals("NICKNAME_REJECTED",e.code);
        verify(moderationHttp).send(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    }
}
