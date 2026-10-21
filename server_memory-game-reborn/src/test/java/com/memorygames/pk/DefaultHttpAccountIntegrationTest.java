package com.memorygames.pk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memorygames.pk.user.AccountService;
import com.memorygames.pk.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Uses application.yml's temporary HTTP default, without an allow-http test override. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.datasource.url=jdbc:h2:mem:default-http-account;DB_CLOSE_DELAY=-1",
        "pk.nickname.mode=disabled","logging.file.name=build/test-logs/default-http-account.log"})
class DefaultHttpAccountIntegrationTest {
    @Autowired AccountService accounts;
    @Autowired UserRepository users;
    @Autowired ObjectMapper json;
    @Autowired Environment environment;
    @LocalServerPort int port;

    private void assertHttpAvatarPatch(boolean includeUnchangedNickname,String avatar) throws Exception {
        assertFalse(environment.containsProperty("PK_AUTH_ALLOW_HTTP"),"Default regression requires PK_AUTH_ALLOW_HTTP to be unset");
        assertEquals(Boolean.TRUE,environment.getProperty("pk.auth.allow-http",Boolean.class));
        var guest=accounts.guest(UUID.randomUUID().toString(),AccountService.randomToken(),null,"Offline fixture");
        String nickname=guest.user().nickname();
        var body=includeUnchangedNickname?Map.of("nickname",nickname,"avatarValue",avatar):Map.of("avatarValue",avatar);
        var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var response=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v2/me/profile"))
                .timeout(Duration.ofSeconds(10)).header("Authorization","Bearer "+guest.accessToken())
                .header("Content-Type","application/json")
                .method("PATCH",HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200,response.statusCode());
        assertEquals("no-store",response.headers().firstValue("Cache-Control").orElseThrow());
        var profile=json.readTree(response.body());
        assertEquals(guest.user().userId(),profile.path("userId").longValue());
        assertEquals(avatar,profile.path("avatarValue").textValue());
        assertEquals(nickname,profile.path("nickname").textValue());
        assertEquals(0,profile.path("nextNicknameChangeAt").longValue());
        var stored=users.findById(guest.user().userId()).orElseThrow();
        assertEquals(avatar,stored.avatarValue);
        assertEquals(nickname,stored.nickname);
        assertEquals(0,stored.nicknameChangedAt);
    }

    @Test void unsetHttpSwitchAllowsAvatarOnlyPatchWithoutRenaming() throws Exception {
        assertHttpAvatarPatch(false,"avatar_2");
    }

    @Test void unsetHttpSwitchAllowsAvatarPatchWithUnchangedNicknameWithoutModeration() throws Exception {
        assertHttpAvatarPatch(true,"avatar_3");
    }
}
