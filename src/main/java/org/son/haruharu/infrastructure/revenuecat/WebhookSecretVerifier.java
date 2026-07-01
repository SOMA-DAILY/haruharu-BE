package org.son.haruharu.infrastructure.revenuecat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * RevenueCat webhook 인증 어댑터. RevenueCat 대시보드에 설정한 Authorization 헤더 값(공유 시크릿)을
 * 그대로 전송하므로, 수신한 Authorization 헤더를 설정값과 상수시간 비교한다.
 * 두 값을 SHA-256으로 고정 길이 다이제스트로 변환해 비교하므로 길이 정보도 노출되지 않는다.
 */
@Component
public class WebhookSecretVerifier {

    private final byte[] secretDigest;

    public WebhookSecretVerifier(@Value("${revenuecat.webhook-secret}") String webhookSecret) {
        this.secretDigest = sha256(webhookSecret);
    }

    public void verify(String authorizationHeader) {
        if (authorizationHeader == null) {
            throw new WebhookUnauthorizedException();
        }
        byte[] candidate = sha256(authorizationHeader);
        if (!MessageDigest.isEqual(candidate, secretDigest)) {
            throw new WebhookUnauthorizedException();
        }
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
