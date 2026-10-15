package com.memorygames.pk.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Tencent TMS TextModeration, API 2020-12-29, TC3-HMAC-SHA256. No configurable URL. */
@Component
public class TencentNicknameProvider implements NicknameProvider {
    private final String mode, secretId, secretKey, region, bizType, sessionToken;
    private final int timeoutMs;
    private final ObjectMapper json;
    private final HttpClient http;
    public TencentNicknameProvider(ObjectMapper json,
            @Value("${pk.nickname.mode:disabled}") String mode,
            @Value("${pk.nickname.tencent-secret-id:}") String secretId,
            @Value("${pk.nickname.tencent-secret-key:}") String secretKey,
            @Value("${pk.nickname.tencent-region:ap-guangzhou}") String region,
            @Value("${pk.nickname.tencent-biz-type:}") String bizType,
            @Value("${pk.nickname.tencent-session-token:}") String sessionToken,
            @Value("${pk.nickname.timeout-ms:3000}") int timeoutMs) {
        this.json=json; this.mode=mode; this.secretId=secretId; this.secretKey=secretKey;
        this.region=region; this.bizType=bizType; this.sessionToken=sessionToken;
        this.timeoutMs=Math.max(200,Math.min(timeoutMs,10000));
        this.http=HttpClient.newBuilder().connectTimeout(Duration.ofMillis(this.timeoutMs))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }
    @Override public Result review(String nickname,String dataId) {
        if(!"tencent".equals(mode) || secretId.isBlank() || secretKey.isBlank() || bizType.isBlank()
                || !region.matches("[a-z]+-[a-z]+(?:-[0-9]+)?")) return unavailable();
        try {
            Map<String,String> payload=new LinkedHashMap<>();
            payload.put("Content",Base64.getEncoder().encodeToString(nickname.getBytes(StandardCharsets.UTF_8)));
            payload.put("BizType",bizType); payload.put("DataId",dataId);
            String body=json.writeValueAsString(payload);
            long timestamp=Instant.now().getEpochSecond();
            HttpRequest.Builder request=HttpRequest.newBuilder(URI.create("https://tms.tencentcloudapi.com/"))
                    .timeout(Duration.ofMillis(timeoutMs)).header("Content-Type","application/json; charset=utf-8")
                    .header("Authorization",authorization(secretId,secretKey,timestamp,body))
                    .header("X-TC-Action","TextModeration").header("X-TC-Version","2020-12-29")
                    .header("X-TC-Timestamp",Long.toString(timestamp)).header("X-TC-Region",region);
            if(!sessionToken.isBlank()) request.header("X-TC-Token",sessionToken);
            HttpResponse<String> response=http.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200 || response.body().length()>262144) return unavailable();
            return parse(json.readTree(response.body()));
        } catch(InterruptedException e) { Thread.currentThread().interrupt(); return unavailable(); }
        catch(Exception e) { return unavailable(); } // Never log exceptions: HTTP bodies may contain private content.
    }
    static Result parse(JsonNode root) {
        JsonNode r=root.path("Response");
        String id=r.path("RequestId").asText("");
        if(!id.matches("[A-Za-z0-9_-]{1,128}")) return unavailable();
        if(r.has("Error")) return new Result(Decision.UNAVAILABLE,id);
        return new Result(switch(r.path("Suggestion").asText("")) {
            case "Pass" -> Decision.PASS;
            case "Block" -> Decision.REJECT;
            case "Review" -> Decision.PENDING;
            default -> Decision.UNAVAILABLE;
        },id);
    }
    static Result unavailable() { return new Result(Decision.UNAVAILABLE,""); }
    static String authorization(String id,String key,long timestamp,String body) throws Exception {
        String date=Instant.ofEpochSecond(timestamp).atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE);
        String headers="content-type:application/json; charset=utf-8\nhost:tms.tencentcloudapi.com\n";
        String signed="content-type;host";
        String canonical="POST\n/\n\n"+headers+"\n"+signed+"\n"+sha(body);
        String scope=date+"/tms/tc3_request";
        String toSign="TC3-HMAC-SHA256\n"+timestamp+"\n"+scope+"\n"+sha(canonical);
        byte[] signing=hmac(hmac(hmac(("TC3"+key).getBytes(StandardCharsets.UTF_8),date),"tms"),"tc3_request");
        return "TC3-HMAC-SHA256 Credential="+id+"/"+scope+", SignedHeaders="+signed+", Signature="+HexFormat.of().formatHex(hmac(signing,toSign));
    }
    private static String sha(String s) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); }
    static byte[] hmac(byte[] key,String s) throws Exception {
        Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key,"HmacSHA256")); return mac.doFinal(s.getBytes(StandardCharsets.UTF_8));
    }
}
