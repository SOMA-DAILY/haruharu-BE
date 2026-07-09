package org.son.haruharu.infrastructure.revenuecat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookSecretVerifierTest {

    private WebhookSecretVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new WebhookSecretVerifier("secret-123");
    }

    @Test
    @DisplayName("설정된 시크릿과 일치하는 Authorization 헤더는 통과한다")
    void givenMatchingSecret_whenVerify_thenPass() {
        assertThatCode(() -> verifier.verify("secret-123")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("시크릿이 일치하지 않으면 WebhookUnauthorizedException이 발생한다")
    void givenWrongSecret_whenVerify_thenThrows() {
        assertThatThrownBy(() -> verifier.verify("wrong-secret"))
                .isInstanceOf(WebhookUnauthorizedException.class);
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 WebhookUnauthorizedException이 발생한다")
    void givenNullHeader_whenVerify_thenThrows() {
        assertThatThrownBy(() -> verifier.verify(null))
                .isInstanceOf(WebhookUnauthorizedException.class);
    }
}
