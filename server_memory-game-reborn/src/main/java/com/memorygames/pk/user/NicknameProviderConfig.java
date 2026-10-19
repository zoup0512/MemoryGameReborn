package com.memorygames.pk.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NicknameProviderConfig {
    @Bean NicknameProvider nicknameProvider(ObjectMapper json,
            @Value("${pk.nickname.mode:disabled}") String mode,
            @Value("${pk.nickname.tencent-secret-id:}") String secretId,
            @Value("${pk.nickname.tencent-secret-key:}") String secretKey,
            @Value("${pk.nickname.tencent-region:ap-guangzhou}") String region,
            @Value("${pk.nickname.tencent-biz-type:}") String bizType,
            @Value("${pk.nickname.tencent-session-token:}") String sessionToken,
            @Value("${pk.nickname.timeout-ms:3000}") int timeoutMs) {
        if("sensitive-word".equals(mode)) return new SensitiveWordNicknameProvider(json,timeoutMs);
        if("tencent".equals(mode)) return new TencentNicknameProvider(json,mode,secretId,secretKey,region,bizType,sessionToken,timeoutMs);
        return (nickname,dataId) -> TencentNicknameProvider.unavailable();
    }
}
