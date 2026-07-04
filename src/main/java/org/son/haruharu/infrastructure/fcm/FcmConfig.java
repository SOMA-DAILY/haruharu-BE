package org.son.haruharu.infrastructure.fcm;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

@Slf4j
@Configuration
public class FcmConfig {

    @Bean
    public PushPort pushPort(@Value("${fcm.service-account-json:}") String serviceAccountBase64) throws IOException {
        if (serviceAccountBase64 == null || serviceAccountBase64.isBlank()) {
            log.info("FCM_SERVICE_ACCOUNT_JSON 미설정 — no-op 푸시 어댑터로 동작");
            return new NoopPushAdapter();
        }
        byte[] serviceAccountJson = Base64.getDecoder().decode(serviceAccountBase64.trim());
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(new ByteArrayInputStream(serviceAccountJson)))
                .build();
        // 컨텍스트 재기동(테스트 등) 시 이중 초기화 방지
        FirebaseApp app = FirebaseApp.getApps().isEmpty()
                ? FirebaseApp.initializeApp(options)
                : FirebaseApp.getInstance();
        return new FcmAdapter(FirebaseMessaging.getInstance(app));
    }
}
