package org.son.haruharu.domain.auth.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.auth.business.command.LogoutCommand;
import org.son.haruharu.domain.auth.business.command.SocialAuthCommand;
import org.son.haruharu.domain.auth.business.command.TokenRefreshCommand;
import org.son.haruharu.domain.auth.business.dto.AuthTokenResponse;
import org.son.haruharu.domain.auth.business.exception.InactiveMemberException;
import org.son.haruharu.domain.auth.business.exception.InvalidRefreshTokenException;
import org.son.haruharu.domain.auth.business.exception.InvalidTokenException;
import org.son.haruharu.domain.auth.implement.ProviderType;
import org.son.haruharu.domain.auth.implement.RefreshToken;
import org.son.haruharu.domain.auth.implement.RefreshTokenReader;
import org.son.haruharu.domain.auth.implement.RefreshTokenWriter;
import org.son.haruharu.domain.auth.implement.UserCredential;
import org.son.haruharu.domain.auth.implement.UserCredentialReader;
import org.son.haruharu.domain.auth.implement.UserCredentialWriter;
import org.son.haruharu.domain.coin.business.CoinService;
import org.son.haruharu.domain.identity.business.IdentityService;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserReader;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.implement.UserWriter;
import org.son.haruharu.infrastructure.oauth.OAuthProvider;
import org.son.haruharu.infrastructure.oauth.OAuthProviderRegistry;
import org.son.haruharu.infrastructure.oauth.OAuthUserInfo;
import org.son.haruharu.utils.JwtProvider;
import org.son.haruharu.utils.JwtValidation;
import org.son.haruharu.support.LogCapture;
import org.son.haruharu.utils.TokenHashUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import ch.qos.logback.classic.Level;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock OAuthProviderRegistry oAuthProviderRegistry;
    @Mock OAuthProvider oAuthProvider;
    @Mock UserReader userReader;
    @Mock UserWriter userWriter;
    @Mock UserCredentialReader userCredentialReader;
    @Mock UserCredentialWriter userCredentialWriter;
    @Mock IdentityService identityService;
    @Mock CoinService coinService;
    @Mock RefreshTokenReader refreshTokenReader;
    @Mock RefreshTokenWriter refreshTokenWriter;
    @Mock JwtProvider jwtProvider;

    AuthService authService;

    @BeforeEach
    void setUp() {
        // Clock을 주입하지 않는다 — 토큰 만료는 시스템 시각 기준이다(#296).
        // 만료 시나리오는 시각을 옮기는 대신 expiresAt을 과거로 세팅한 RefreshToken으로 만든다.
        authService = new AuthService(
            oAuthProviderRegistry, userReader, userWriter,
            userCredentialReader, userCredentialWriter,
            identityService, coinService,
            refreshTokenReader, refreshTokenWriter, jwtProvider
        );
    }

    // ===================== authenticate =====================

    @Test
    @DisplayName("신규 소셜 계정으로 인증 시 User를 ONBOARDING으로 생성하고 ONBOARDING 상태를 반환한다")
    void givenNewSocialAccount_whenAuthenticate_thenCreatesOnboardingUserAndReturnsOnboarding() {
        Long userId = 1L;
        SocialAuthCommand command = new SocialAuthCommand(ProviderType.KAKAO, "valid-id-token");
        OAuthUserInfo userInfo = new OAuthUserInfo("kakao-sub-123", "KAKAO");
        User savedUser = new User(userId, UserStatus.ONBOARDING, LocalDateTime.now());

        given(oAuthProviderRegistry.get(ProviderType.KAKAO)).willReturn(oAuthProvider);
        given(oAuthProvider.verify("valid-id-token")).willReturn(userInfo);
        given(userCredentialReader.findByProviderTypeAndProviderId("KAKAO", "kakao-sub-123")).willReturn(Optional.empty());
        given(userWriter.save(any())).willReturn(savedUser);
        given(userCredentialWriter.save(any())).willReturn(new UserCredential(10L, userId, "KAKAO", "kakao-sub-123"));
        given(jwtProvider.generateAccessToken(userId)).willReturn("access-token");
        given(jwtProvider.generateRefreshToken(userId)).willReturn("refresh-token");
        given(jwtProvider.getRefreshTokenExpiryMs()).willReturn(2592000000L);

        AuthTokenResponse result = authService.authenticate(command);

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.userStatus()).isEqualTo("ONBOARDING");

        ArgumentCaptor<User> savedCaptor = ArgumentCaptor.forClass(User.class);
        then(userWriter).should().save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().status()).isEqualTo(UserStatus.ONBOARDING);
        then(userCredentialWriter).should().save(any());
        then(coinService).should().grantSignupBonus(userId);
    }

    @Test
    @DisplayName("기존 온보딩 미완 계정(ONBOARDING) 로그인 시 ONBOARDING을 반환한다")
    void givenOnboardingUser_whenAuthenticate_thenReturnsOnboarding() {
        Long userId = 1L;
        SocialAuthCommand command = new SocialAuthCommand(ProviderType.KAKAO, "valid-id-token");
        OAuthUserInfo userInfo = new OAuthUserInfo("kakao-sub-123", "KAKAO");
        UserCredential credential = new UserCredential(10L, userId, "KAKAO", "kakao-sub-123");
        User user = new User(userId, UserStatus.ONBOARDING, LocalDateTime.now());

        given(oAuthProviderRegistry.get(ProviderType.KAKAO)).willReturn(oAuthProvider);
        given(oAuthProvider.verify("valid-id-token")).willReturn(userInfo);
        given(userCredentialReader.findByProviderTypeAndProviderId("KAKAO", "kakao-sub-123")).willReturn(Optional.of(credential));
        given(userReader.getById(userId)).willReturn(user);
        given(jwtProvider.generateAccessToken(userId)).willReturn("access-token");
        given(jwtProvider.generateRefreshToken(userId)).willReturn("refresh-token");
        given(jwtProvider.getRefreshTokenExpiryMs()).willReturn(2592000000L);

        AuthTokenResponse result = authService.authenticate(command);

        assertThat(result.userStatus()).isEqualTo("ONBOARDING");
        then(userWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("온보딩 완료(ACTIVE) + identity 없음: 로그인 성공 후 ACTIVE 반환")
    void givenActiveAccountWithoutIdentity_whenAuthenticate_thenReturnsActive() {
        Long userId = 1L;
        SocialAuthCommand command = new SocialAuthCommand(ProviderType.KAKAO, "valid-id-token");
        OAuthUserInfo userInfo = new OAuthUserInfo("kakao-sub-123", "KAKAO");
        UserCredential credential = new UserCredential(10L, userId, "KAKAO", "kakao-sub-123");
        User user = new User(userId, UserStatus.ACTIVE, LocalDateTime.now());

        given(oAuthProviderRegistry.get(ProviderType.KAKAO)).willReturn(oAuthProvider);
        given(oAuthProvider.verify("valid-id-token")).willReturn(userInfo);
        given(userCredentialReader.findByProviderTypeAndProviderId("KAKAO", "kakao-sub-123")).willReturn(Optional.of(credential));
        given(userReader.getById(userId)).willReturn(user);
        given(identityService.isVerified(userId)).willReturn(false);
        given(jwtProvider.generateAccessToken(userId)).willReturn("access-token");
        given(jwtProvider.generateRefreshToken(userId)).willReturn("refresh-token");
        given(jwtProvider.getRefreshTokenExpiryMs()).willReturn(2592000000L);

        AuthTokenResponse result = authService.authenticate(command);

        assertThat(result.userStatus()).isEqualTo("ACTIVE");
        then(userWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("기존 계정 + identity 있음: 로그인 성공 후 VERIFIED 반환")
    void givenExistingAccountWithIdentity_whenAuthenticate_thenReturnsVerified() {
        Long userId = 1L;
        SocialAuthCommand command = new SocialAuthCommand(ProviderType.APPLE, "valid-id-token");
        OAuthUserInfo userInfo = new OAuthUserInfo("apple-sub-456", "APPLE");
        UserCredential credential = new UserCredential(10L, userId, "APPLE", "apple-sub-456");
        User user = new User(userId, UserStatus.ACTIVE, LocalDateTime.now());

        given(oAuthProviderRegistry.get(ProviderType.APPLE)).willReturn(oAuthProvider);
        given(oAuthProvider.verify("valid-id-token")).willReturn(userInfo);
        given(userCredentialReader.findByProviderTypeAndProviderId("APPLE", "apple-sub-456")).willReturn(Optional.of(credential));
        given(userReader.getById(userId)).willReturn(user);
        given(identityService.isVerified(userId)).willReturn(true);
        given(jwtProvider.generateAccessToken(userId)).willReturn("access-token");
        given(jwtProvider.generateRefreshToken(userId)).willReturn("refresh-token");
        given(jwtProvider.getRefreshTokenExpiryMs()).willReturn(2592000000L);

        AuthTokenResponse result = authService.authenticate(command);

        assertThat(result.userStatus()).isEqualTo("VERIFIED");
    }

    @Test
    @DisplayName("비활성화된 계정으로 인증 시 InactiveMemberException이 발생한다")
    void givenBannedMember_whenAuthenticate_thenThrowsForbidden() {
        Long userId = 1L;
        SocialAuthCommand command = new SocialAuthCommand(ProviderType.KAKAO, "valid-id-token");
        OAuthUserInfo userInfo = new OAuthUserInfo("kakao-sub-banned", "KAKAO");
        UserCredential credential = new UserCredential(10L, userId, "KAKAO", "kakao-sub-banned");
        User bannedUser = new User(userId, UserStatus.BANNED, LocalDateTime.now());

        given(oAuthProviderRegistry.get(ProviderType.KAKAO)).willReturn(oAuthProvider);
        given(oAuthProvider.verify("valid-id-token")).willReturn(userInfo);
        given(userCredentialReader.findByProviderTypeAndProviderId("KAKAO", "kakao-sub-banned")).willReturn(Optional.of(credential));
        given(userReader.getById(userId)).willReturn(bannedUser);

        assertThatThrownBy(() -> authService.authenticate(command))
            .isInstanceOf(InactiveMemberException.class);

        then(jwtProvider).should(never()).generateAccessToken(any());
    }

    @Test
    @DisplayName("탈퇴 직후 같은 OAuth로 재로그인 시 차단 없이 재가입되고 signup 보너스는 지급되지 않는다")
    void givenJustWithdrawnCredential_whenAuthenticate_thenReSignupWithoutBonus() {
        Long newUserId = 2L;
        SocialAuthCommand command = new SocialAuthCommand(ProviderType.KAKAO, "valid-id-token");
        OAuthUserInfo userInfo = new OAuthUserInfo("kakao-sub-123", "KAKAO");
        User created = new User(newUserId, UserStatus.ONBOARDING, LocalDateTime.now());

        given(oAuthProviderRegistry.get(ProviderType.KAKAO)).willReturn(oAuthProvider);
        given(oAuthProvider.verify("valid-id-token")).willReturn(userInfo);
        given(userCredentialReader.findByProviderTypeAndProviderId("KAKAO", "kakao-sub-123")).willReturn(Optional.empty());
        // 탈퇴 잔존 행이 파기됨(=복귀 유저) → 보너스 skip
        given(userCredentialWriter.purgeWithdrawnBefore(eq("KAKAO"), eq("kakao-sub-123"), any())).willReturn(1);
        given(userWriter.save(any())).willReturn(created);
        given(userCredentialWriter.save(any())).willReturn(new UserCredential(20L, newUserId, "KAKAO", "kakao-sub-123"));
        given(jwtProvider.generateAccessToken(newUserId)).willReturn("access-token");
        given(jwtProvider.generateRefreshToken(newUserId)).willReturn("refresh-token");
        given(jwtProvider.getRefreshTokenExpiryMs()).willReturn(2592000000L);

        AuthTokenResponse result = authService.authenticate(command);

        assertThat(result.userStatus()).isEqualTo("ONBOARDING");
        then(userCredentialWriter).should().save(any());
        then(coinService).should(never()).grantSignupBonus(any());
    }

    // ===================== refresh =====================

    @Test
    @DisplayName("유효한 리프레시 토큰으로 갱신 시 새 토큰 쌍과 user_status(ACTIVE)가 반환된다")
    void givenValidRefreshToken_whenRefresh_thenReturnsNewTokenPairWithStatus() {
        Long userId = 1L;
        String rawToken = "valid-refresh-token";
        String tokenHash = TokenHashUtils.sha256Hex(rawToken);
        RefreshToken refreshToken = new RefreshToken(10L, userId, tokenHash,
            LocalDateTime.now().plusDays(30), null);

        given(jwtProvider.validate(rawToken)).willReturn(JwtValidation.VALID);
        given(refreshTokenReader.findByTokenHash(tokenHash)).willReturn(Optional.of(refreshToken));
        given(refreshTokenWriter.revokeByTokenHash(tokenHash)).willReturn(true);
        given(userReader.getById(userId)).willReturn(new User(userId, UserStatus.ACTIVE, LocalDateTime.now()));
        given(identityService.isVerified(userId)).willReturn(false);
        given(jwtProvider.generateAccessToken(userId)).willReturn("new-access-token");
        given(jwtProvider.generateRefreshToken(userId)).willReturn("new-refresh-token");
        given(jwtProvider.getRefreshTokenExpiryMs()).willReturn(2592000000L);

        AuthTokenResponse result = authService.refresh(new TokenRefreshCommand(rawToken));

        assertThat(result.accessToken()).isEqualTo("new-access-token");
        assertThat(result.refreshToken()).isEqualTo("new-refresh-token");
        assertThat(result.userStatus()).isEqualTo("ACTIVE");
        then(refreshTokenWriter).should().revokeByTokenHash(tokenHash);
    }

    @Test
    @DisplayName("온보딩 미완 유저가 갱신 시 user_status가 ONBOARDING으로 반환된다")
    void givenOnboardingUser_whenRefresh_thenReturnsOnboardingStatus() {
        Long userId = 1L;
        String rawToken = "valid-refresh-token";
        String tokenHash = TokenHashUtils.sha256Hex(rawToken);
        RefreshToken refreshToken = new RefreshToken(10L, userId, tokenHash,
            LocalDateTime.now().plusDays(30), null);

        given(jwtProvider.validate(rawToken)).willReturn(JwtValidation.VALID);
        given(refreshTokenReader.findByTokenHash(tokenHash)).willReturn(Optional.of(refreshToken));
        given(refreshTokenWriter.revokeByTokenHash(tokenHash)).willReturn(true);
        given(userReader.getById(userId)).willReturn(new User(userId, UserStatus.ONBOARDING, LocalDateTime.now()));
        given(jwtProvider.generateAccessToken(userId)).willReturn("new-access-token");
        given(jwtProvider.generateRefreshToken(userId)).willReturn("new-refresh-token");
        given(jwtProvider.getRefreshTokenExpiryMs()).willReturn(2592000000L);

        AuthTokenResponse result = authService.refresh(new TokenRefreshCommand(rawToken));

        assertThat(result.userStatus()).isEqualTo("ONBOARDING");
    }

    @Test
    @DisplayName("만료된 리프레시 토큰으로 갱신 시 401 예외가 발생한다")
    void givenExpiredRefreshToken_whenRefresh_thenThrowsUnauthorized() {
        String rawToken = "expired-refresh-token";
        String tokenHash = TokenHashUtils.sha256Hex(rawToken);
        RefreshToken expiredToken = new RefreshToken(10L, 20L, tokenHash,
            LocalDateTime.now().minusDays(1), null);

        given(jwtProvider.validate(rawToken)).willReturn(JwtValidation.VALID);
        given(refreshTokenReader.findByTokenHash(tokenHash)).willReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> authService.refresh(new TokenRefreshCommand(rawToken)))
            .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    @DisplayName("이미 revoke된 리프레시 토큰으로 갱신 시 전체 세션 무효화 후 401 예외가 발생한다")
    void givenRevokedRefreshToken_whenRefresh_thenRevokesAllSessionsAndThrowsUnauthorized() {
        Long userId = 1L;
        String rawToken = "revoked-refresh-token";
        String tokenHash = TokenHashUtils.sha256Hex(rawToken);
        RefreshToken revokedToken = new RefreshToken(10L, userId, tokenHash,
            LocalDateTime.now().plusDays(30), LocalDateTime.now().minusHours(1));

        given(jwtProvider.validate(rawToken)).willReturn(JwtValidation.VALID);
        given(refreshTokenReader.findByTokenHash(tokenHash)).willReturn(Optional.of(revokedToken));

        assertThatThrownBy(() -> authService.refresh(new TokenRefreshCommand(rawToken)))
            .isInstanceOf(InvalidRefreshTokenException.class);

        then(refreshTokenWriter).should().revokeAllByUserId(userId);
    }

    @Test
    @DisplayName("revoke 직후 grace 창 안에 같은 토큰이 다시 오면 401만 주고 전체 세션은 유지한다(#417 병렬 재발급)")
    void givenTokenRevokedWithinGrace_whenRefresh_thenThrowsUnauthorizedWithoutRevokingAll() {
        Long userId = 1L;
        String rawToken = "just-revoked-refresh-token";
        String tokenHash = TokenHashUtils.sha256Hex(rawToken);
        // 앱이 refresh 를 병렬 2회 호출 → 첫 요청이 revoke 한 지 1초 뒤 늦은 요청이 도착
        RefreshToken revokedToken = new RefreshToken(10L, userId, tokenHash,
            LocalDateTime.now().plusDays(30), LocalDateTime.now().minusSeconds(1));

        given(jwtProvider.validate(rawToken)).willReturn(JwtValidation.VALID);
        given(refreshTokenReader.findByTokenHash(tokenHash)).willReturn(Optional.of(revokedToken));

        assertThatThrownBy(() -> authService.refresh(new TokenRefreshCommand(rawToken)))
            .isInstanceOf(InvalidRefreshTokenException.class);

        then(refreshTokenWriter).should(never()).revokeAllByUserId(any());
    }

    @Test
    @DisplayName("refresh 거절 사유는 응답이 아닌 서버 로그에만 남는다 — 토큰 원문은 남기지 않는다(#417)")
    void givenRejectedRefresh_whenRefresh_thenLogsReasonWithoutRawToken() {
        String rawToken = "expired-refresh-token";
        String tokenHash = TokenHashUtils.sha256Hex(rawToken);
        RefreshToken expiredToken = new RefreshToken(10L, 20L, tokenHash,
            LocalDateTime.now().minusDays(1), null);
        given(jwtProvider.validate(rawToken)).willReturn(JwtValidation.VALID);
        given(refreshTokenReader.findByTokenHash(tokenHash)).willReturn(Optional.of(expiredToken));

        try (LogCapture logs = LogCapture.of(AuthService.class)) {
            assertThatThrownBy(() -> authService.refresh(new TokenRefreshCommand(rawToken)))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessage(new InvalidRefreshTokenException().getMessage()); // 응답 메시지는 사유를 드러내지 않는다

            assertThat(logs.at(Level.WARN)).hasSize(1);
            assertThat(logs.text()).contains("EXPIRED").contains("userId=20").doesNotContain(rawToken);
        }
    }

    @Test
    @DisplayName("조회 후 다른 요청이 먼저 토큰을 소비했으면(원자적 revoke 0건) 401만 주고 전체 세션은 유지한다(#417 동시 재발급)")
    void givenTokenConsumedConcurrently_whenRefresh_thenThrowsUnauthorizedWithoutIssuingOrRevokingAll() {
        Long userId = 1L;
        String rawToken = "raced-refresh-token";
        String tokenHash = TokenHashUtils.sha256Hex(rawToken);
        RefreshToken active = new RefreshToken(10L, userId, tokenHash, LocalDateTime.now().plusDays(30), null);

        given(jwtProvider.validate(rawToken)).willReturn(JwtValidation.VALID);
        given(refreshTokenReader.findByTokenHash(tokenHash)).willReturn(Optional.of(active));
        given(userReader.getById(userId)).willReturn(new User(userId, UserStatus.ACTIVE, LocalDateTime.now()));
        given(refreshTokenWriter.revokeByTokenHash(tokenHash)).willReturn(false); // 승자가 먼저 소비

        assertThatThrownBy(() -> authService.refresh(new TokenRefreshCommand(rawToken)))
            .isInstanceOf(InvalidRefreshTokenException.class);

        then(refreshTokenWriter).should(never()).save(any());
        then(refreshTokenWriter).should(never()).revokeAllByUserId(any());
    }

    @Test
    @DisplayName("DB에 없는 리프레시 토큰으로 갱신 시 401 예외가 발생한다")
    void givenUnknownRefreshToken_whenRefresh_thenThrowsUnauthorized() {
        String rawToken = "unknown-refresh-token";
        String tokenHash = TokenHashUtils.sha256Hex(rawToken);

        given(jwtProvider.validate(rawToken)).willReturn(JwtValidation.VALID);
        given(refreshTokenReader.findByTokenHash(tokenHash)).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(new TokenRefreshCommand(rawToken)))
            .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    @DisplayName("JWT 형식이 잘못된 리프레시 토큰으로 갱신 시 401 예외가 발생한다")
    void givenMalformedRefreshToken_whenRefresh_thenThrowsUnauthorized() {
        given(jwtProvider.validate("malformed")).willReturn(JwtValidation.MALFORMED);

        assertThatThrownBy(() -> authService.refresh(new TokenRefreshCommand("malformed")))
            .isInstanceOf(InvalidRefreshTokenException.class);

        then(refreshTokenReader).should(never()).findByTokenHash(anyString());
    }

    // ===================== logout =====================

    @Test
    @DisplayName("유효한 리프레시 토큰으로 로그아웃 시 토큰이 무효화된다")
    void givenValidRefreshToken_whenLogout_thenTokenIsRevoked() {
        Long userId = 1L;
        String rawToken = "valid-refresh-token";
        String tokenHash = TokenHashUtils.sha256Hex(rawToken);
        RefreshToken refreshToken = new RefreshToken(10L, userId, tokenHash,
            LocalDateTime.now().plusDays(30), null);

        given(jwtProvider.isValid(rawToken)).willReturn(true);
        given(refreshTokenReader.findByTokenHash(tokenHash)).willReturn(Optional.of(refreshToken));
        given(refreshTokenWriter.revokeByTokenHash(tokenHash)).willReturn(true);

        authService.logout(new LogoutCommand(rawToken));

        then(refreshTokenWriter).should().revokeByTokenHash(tokenHash);
    }

    @Test
    @DisplayName("로그아웃 조회 후 다른 요청이 먼저 토큰을 소비했으면 401 예외가 발생한다(#417)")
    void givenTokenConsumedConcurrently_whenLogout_thenThrowsUnauthorized() {
        String rawToken = "raced-logout-token";
        String tokenHash = TokenHashUtils.sha256Hex(rawToken);
        RefreshToken active = new RefreshToken(10L, 1L, tokenHash, LocalDateTime.now().plusDays(30), null);

        given(jwtProvider.isValid(rawToken)).willReturn(true);
        given(refreshTokenReader.findByTokenHash(tokenHash)).willReturn(Optional.of(active));
        given(refreshTokenWriter.revokeByTokenHash(tokenHash)).willReturn(false);

        assertThatThrownBy(() -> authService.logout(new LogoutCommand(rawToken)))
            .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("이미 revoke된 토큰으로 로그아웃 시 401 예외가 발생한다")
    void givenRevokedToken_whenLogout_thenThrowsUnauthorized() {
        String rawToken = "revoked-token";
        String tokenHash = TokenHashUtils.sha256Hex(rawToken);
        RefreshToken revokedToken = new RefreshToken(10L, 20L, tokenHash,
            LocalDateTime.now().plusDays(30), LocalDateTime.now().minusHours(1));

        given(jwtProvider.isValid(rawToken)).willReturn(true);
        given(refreshTokenReader.findByTokenHash(tokenHash)).willReturn(Optional.of(revokedToken));

        assertThatThrownBy(() -> authService.logout(new LogoutCommand(rawToken)))
            .isInstanceOf(InvalidTokenException.class);
    }
}
