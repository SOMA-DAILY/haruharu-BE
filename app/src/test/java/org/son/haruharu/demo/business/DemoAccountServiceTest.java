package org.son.haruharu.demo.business;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.demo.business.exception.DemoAccessDeniedException;
import org.son.haruharu.domain.auth.business.dto.AuthTokenResponse;
import org.son.haruharu.domain.auth.implement.RefreshTokenWriter;
import org.son.haruharu.domain.auth.implement.UserCredential;
import org.son.haruharu.domain.auth.implement.UserCredentialReader;
import org.son.haruharu.domain.demoaccess.implement.DemoAccessCode;
import org.son.haruharu.domain.demoaccess.implement.DemoAccessCodeReader;
import org.son.haruharu.utils.JwtProvider;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DemoAccountServiceTest {

    private static final String PROVIDER_ID = "haruharu-demo";
    private static final String CODE = "HARU-DEMO-2026";
    private static final Long DEMO_USER_ID = 7L;

    @Mock DemoAccessCodeReader demoAccessCodeReader;
    @Mock UserCredentialReader userCredentialReader;
    @Mock DemoUserProvisioner demoUserProvisioner;
    @Mock RefreshTokenWriter refreshTokenWriter;
    @Mock JwtProvider jwtProvider;

    DemoAccountService service;

    @BeforeEach
    void setUp() {
        service = new DemoAccountService(demoAccessCodeReader, userCredentialReader, demoUserProvisioner,
                refreshTokenWriter, jwtProvider, PROVIDER_ID);
        lenient().when(jwtProvider.generateAccessToken(any())).thenReturn("access");
        lenient().when(jwtProvider.generateRefreshToken(any())).thenReturn("refresh");
        lenient().when(jwtProvider.getRefreshTokenExpiryMs()).thenReturn(1_000_000L);
    }

    private void codeConfigured() {
        given(demoAccessCodeReader.findCurrent()).willReturn(Optional.of(DemoAccessCode.of(CODE)));
    }

    private UserCredential demoCredential() {
        return new UserCredential(1L, DEMO_USER_ID, "DEMO", PROVIDER_ID);
    }

    @Test
    @DisplayName("코드가 맞고 계정이 없으면 프로비저너로 생성하고 토큰을 발급한다")
    void givenValidCodeNoAccount_whenLogin_thenProvisionsAndIssues() {
        codeConfigured();
        given(userCredentialReader.findByProviderTypeAndProviderId("DEMO", PROVIDER_ID)).willReturn(Optional.empty());
        given(demoUserProvisioner.create(PROVIDER_ID)).willReturn(DEMO_USER_ID);

        AuthTokenResponse result = service.login(CODE);

        verify(demoUserProvisioner).create(PROVIDER_ID);
        verify(refreshTokenWriter).save(any());
        assertThat(result.accessToken()).isEqualTo("access");
        assertThat(result.userStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("코드가 틀리면 DemoAccessDeniedException이고 생성도 토큰 발급도 하지 않는다")
    void givenWrongCode_whenLogin_thenDeniedNoSideEffect() {
        codeConfigured();

        assertThatThrownBy(() -> service.login("WRONG"))
                .isInstanceOf(DemoAccessDeniedException.class);
        verify(demoUserProvisioner, never()).create(any());
        verify(refreshTokenWriter, never()).save(any());
    }

    @Test
    @DisplayName("코드가 미설정(닫힘)이면 어떤 값을 넣어도 DemoAccessDeniedException")
    void givenNoCodeConfigured_whenLogin_thenDenied() {
        given(demoAccessCodeReader.findCurrent()).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(CODE))
                .isInstanceOf(DemoAccessDeniedException.class);
        verify(refreshTokenWriter, never()).save(any());
    }

    @Test
    @DisplayName("코드가 맞고 계정이 이미 있으면 재사용하고 생성하지 않는다(멱등)")
    void givenValidCodeExistingAccount_whenLogin_thenReuses() {
        codeConfigured();
        given(userCredentialReader.findByProviderTypeAndProviderId("DEMO", PROVIDER_ID))
                .willReturn(Optional.of(demoCredential()));

        AuthTokenResponse result = service.login(CODE);

        verify(demoUserProvisioner, never()).create(any());
        verify(refreshTokenWriter).save(any());
        assertThat(result.accessToken()).isEqualTo("access");
    }

    @Test
    @DisplayName("동시 최초 호출로 생성이 unique 충돌하면 경쟁에서 이긴 계정을 재조회해 재사용한다(TOCTOU 방어)")
    void givenRaceOnCreate_whenLogin_thenRefindsAndReuses() {
        codeConfigured();
        given(userCredentialReader.findByProviderTypeAndProviderId("DEMO", PROVIDER_ID))
                .willReturn(Optional.empty())            // 최초 확인: 없음 → 생성 시도
                .willReturn(Optional.of(demoCredential())); // 충돌 후 재조회: 이긴 계정
        given(demoUserProvisioner.create(PROVIDER_ID)).willThrow(new DataIntegrityViolationException("dup"));

        AuthTokenResponse result = service.login(CODE);

        assertThat(result.accessToken()).isEqualTo("access");
        verify(refreshTokenWriter).save(any());
    }
}
