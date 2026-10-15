package com.memorygames.pk;

import com.memorygames.pk.user.NicknameProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Deliberately test-source-only. Cannot be activated by any production configuration or profile. */
@TestConfiguration
public class PassingNicknameTestConfig {
    @Bean @Primary NicknameProvider passingNicknameTestProvider() {
        return (name,id) -> new NicknameProvider.Result(NicknameProvider.Decision.PASS,"test-only");
    }
}
