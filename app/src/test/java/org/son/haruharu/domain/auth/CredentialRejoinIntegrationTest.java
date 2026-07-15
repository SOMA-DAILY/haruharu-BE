package org.son.haruharu.domain.auth;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.auth.business.AuthService;
import org.son.haruharu.domain.auth.business.command.SocialAuthCommand;
import org.son.haruharu.domain.auth.business.dto.AuthTokenResponse;
import org.son.haruharu.domain.auth.implement.ProviderType;
import org.son.haruharu.domain.auth.implement.UserCredential;
import org.son.haruharu.domain.auth.implement.UserCredentialReader;
import org.son.haruharu.domain.auth.implement.UserCredentialWriter;
import org.son.haruharu.infrastructure.oauth.OAuthProvider;
import org.son.haruharu.infrastructure.oauth.OAuthProviderRegistry;
import org.son.haruharu.infrastructure.oauth.OAuthUserInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 같은 OAuth 재가입 쿨다운(#129)을 실제 PostgreSQL로 검증한다.
 * 리포지토리를 mock한 단위 테스트가 못 잡는 native 쿼리 동작과
 * unique(provider_type, provider_id) 재삽입 충돌을 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CredentialRejoinIntegrationTest {

    private static final String PROVIDER = "KAKAO";
    private static final String SUB = "kakao-rejoin-sub";

    @Autowired UserCredentialReader userCredentialReader;
    @Autowired UserCredentialWriter userCredentialWriter;
    @Autowired AuthService authService;
    @Autowired EntityManager em;

    @MockitoBean OAuthProviderRegistry oAuthProviderRegistry;

    @Test
    @DisplayName("탈퇴로 soft-delete된 credential은 일반 조회에서 제외되고 탈퇴 직후 purge로 즉시 물리 파기된다")
    void givenJustWithdrawnCredential_whenPurgeNow_thenExcludedFromFindAndPurged() {
        userCredentialWriter.save(new UserCredential(null, 9001L, PROVIDER, SUB));
        em.flush();

        userCredentialWriter.deleteByUserId(9001L);
        em.flush();
        em.clear();

        assertThat(userCredentialReader.findByProviderTypeAndProviderId(PROVIDER, SUB)).isEmpty();

        // 차단 기간 없이 now 기준 → 탈퇴 직후에도 즉시 파기 대상
        assertThat(userCredentialWriter.purgeWithdrawnBefore(PROVIDER, SUB, LocalDateTime.now())).isEqualTo(1);
    }

    @Test
    @DisplayName("탈퇴 직후 purge된 credential은 같은 OAuth 재삽입이 unique 충돌 없이 성공한다")
    void givenJustWithdrawn_whenPurgeAndReinsert_thenNoUniqueConflict() {
        userCredentialWriter.save(new UserCredential(null, 9002L, PROVIDER, SUB));
        em.flush();
        userCredentialWriter.deleteByUserId(9002L);
        em.flush();
        em.clear();

        assertThat(userCredentialWriter.purgeWithdrawnBefore(PROVIDER, SUB, LocalDateTime.now())).isEqualTo(1);
        em.clear();

        // 죽은 행이 물리 파기됐으므로 같은 provider_id 재삽입이 충돌 없이 성공한다
        assertThatCode(() -> {
            userCredentialWriter.save(new UserCredential(null, 9003L, PROVIDER, SUB));
            em.flush();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("탈퇴 직후 같은 OAuth로 authenticate 시 차단 없이 재가입되어 ONBOARDING이 반환된다")
    void givenJustWithdrawn_whenAuthenticateSameOAuth_thenReSignupOnboarding() {
        userCredentialWriter.save(new UserCredential(null, 9004L, PROVIDER, SUB));
        em.flush();
        userCredentialWriter.deleteByUserId(9004L);
        em.flush();
        em.clear();

        OAuthProvider provider = mock(OAuthProvider.class);
        given(oAuthProviderRegistry.get(ProviderType.KAKAO)).willReturn(provider);
        given(provider.verify("id-token")).willReturn(new OAuthUserInfo(SUB, PROVIDER));

        AuthTokenResponse result = authService.authenticate(new SocialAuthCommand(ProviderType.KAKAO, "id-token"));

        assertThat(result.userStatus()).isEqualTo("ONBOARDING");
        assertThat(result.accessToken()).isNotEmpty();
    }
}
