package org.son.haruharu.domain.auth.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import org.son.haruharu.domain.identity.implement.UserIdentityReader;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserReader;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.implement.UserWriter;
import org.son.haruharu.infrastructure.oauth.OAuthProvider;
import org.son.haruharu.infrastructure.oauth.OAuthProviderRegistry;
import org.son.haruharu.infrastructure.oauth.OAuthUserInfo;
import org.son.haruharu.utils.JwtProvider;
import org.son.haruharu.utils.TokenHashUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock OAuthProviderRegistry oAuthProviderRegistry;
    @Mock OAuthProvider oAuthProvider;
    @Mock UserReader userReader;
    @Mock UserWriter userWriter;
    @Mock UserCredentialReader userCredentialReader;
    @Mock UserCredentialWriter userCredentialWriter;
    @Mock UserIdentityReader userIdentityReader;
    @Mock RefreshTokenReader refreshTokenReader;
    @Mock RefreshTokenWriter refreshTokenWriter;
    @Mock JwtProvider jwtProvider;

    AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
            oAuthProviderRegistry, userReader, userWriter,
            userCredentialReader, userCredentialWriter,
            userIdentityReader,
            refreshTokenReader, refreshTokenWriter, jwtProvider
        );
    }

    // ===================== authenticate =====================

    @Test
    @DisplayName("신규 소셜 계정으로 인증 시 User+Credential을 생성하고 UNVERIFIED 상태를 반환한다")
    void givenNewSocialAccount_whenAuthenticate_thenCreatesUserAndReturnsUnverified() {
        Long userId = 1L;
        SocialAuthCommand command = new SocialAuthCommand(ProviderType.KAKAO, "valid-id-token");
        OAuthUserInfo userInfo = new OAuthUserInfo("kakao-sub-123", "KAKAO");
        User savedUser = new User(userId, UserStatus.ACTIVE, LocalDateTime.now());

        given(oAuthProviderRegistry.get(ProviderType.KAKAO)).willReturn(oAuthProvider);
        given(oAuthProvider.verify("valid-id-token")).willReturn(userInfo);
        given(userCredentialReader.findByProviderTypeAndProviderId("KAKAO", "kakao-sub-123")).willReturn(Optional.empty());
        given(userWriter.save(any())).willReturn(savedUser);
        given(userCredentialWriter.save(any())).willReturn(new UserCredential(10L, userId, "KAKAO", "kakao-sub-123"));
        given(userIdentityReader.existsByUserId(userId)).willReturn(false);
        given(jwtProvider.generateAccessToken(userId)).willReturn("access-token");
        given(jwtProvider.generateRefreshToken(userId)).willReturn("refresh-token");
        given(jwtProvider.getRefreshTokenExpiryMs()).willReturn(2592000000L);

        AuthTokenResponse result = authService.authenticate(command);

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.userStatus()).isEqualTo("UNVERIFIED");
        then(userWriter).should().save(any());
        then(userCredentialWriter).should().save(any());
    }

    @Test
    @DisplayName("기존 계정 + identity 없음: 로그인 성공 후 UNVERIFIED 반환")
    void givenExistingAccountWithoutIdentity_whenAuthenticate_thenReturnsUnverified() {
        Long userId = 1L;
        SocialAuthCommand command = new SocialAuthCommand(ProviderType.KAKAO, "valid-id-token");
        OAuthUserInfo userInfo = new OAuthUserInfo("kakao-sub-123", "KAKAO");
        UserCredential credential = new UserCredential(10L, userId, "KAKAO", "kakao-sub-123");
        User user = new User(userId, UserStatus.ACTIVE, LocalDateTime.now());

        given(oAuthProviderRegistry.get(ProviderType.KAKAO)).willReturn(oAuthProvider);
        given(oAuthProvider.verify("valid-id-token")).willReturn(userInfo);
        given(userCredentialReader.findByProviderTypeAndProviderId("KAKAO", "kakao-sub-123")).willReturn(Optional.of(credential));
        given(userReader.getById(userId)).willReturn(user);
        given(userIdentityReader.existsByUserId(userId)).willReturn(false);
        given(jwtProvider.generateAccessToken(userId)).willReturn("access-token");
        given(jwtProvider.generateRefreshToken(userId)).willReturn("refresh-token");
        given(jwtProvider.getRefreshTokenExpiryMs()).willReturn(2592000000L);

        AuthTokenResponse result = authService.authenticate(command);

        assertThat(result.userStatus()).isEqualTo("UNVERIFIED");
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
        given(userIdentityReader.existsByUserId(userId)).willReturn(true);
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

    // ===================== refresh =====================

    @Test
    @DisplayName("유효한 리프레시 토큰으로 갱신 시 새 토큰 쌍이 반환된다")
    void givenValidRefreshToken_whenRefresh_thenReturnsNewTokenPair() {
        Long userId = 1L;
        String rawToken = "valid-refresh-token";
        String tokenHash = TokenHashUtils.sha256Hex(rawToken);
        RefreshToken refreshToken = new RefreshToken(10L, userId, tokenHash,
            LocalDateTime.now().plusDays(30), null);

        given(jwtProvider.isValid(rawToken)).willReturn(true);
        given(refreshTokenReader.findByTokenHash(tokenHash)).willReturn(Optional.of(refreshToken));
        given(userReader.getById(userId)).willReturn(new User(userId, UserStatus.ACTIVE, LocalDateTime.now()));
        given(jwtProvider.generateAccessToken(userId)).willReturn("new-access-token");
        given(jwtProvider.generateRefreshToken(userId)).willReturn("new-refresh-token");
        given(jwtProvider.getRefreshTokenExpiryMs()).willReturn(2592000000L);

        AuthTokenResponse result = authService.refresh(new TokenRefreshCommand(rawToken));

        assertThat(result.accessToken()).isEqualTo("new-access-token");
        assertThat(result.refreshToken()).isEqualTo("new-refresh-token");
        assertThat(result.userStatus()).isNull();
        then(refreshTokenWriter).should().revokeByTokenHash(tokenHash);
    }

    @Test
    @DisplayName("만료된 리프레시 토큰으로 갱신 시 401 예외가 발생한다")
    void givenExpiredRefreshToken_whenRefresh_thenThrowsUnauthorized() {
        String rawToken = "expired-refresh-token";
        String tokenHash = TokenHashUtils.sha256Hex(rawToken);
        RefreshToken expiredToken = new RefreshToken(10L, 20L, tokenHash,
            LocalDateTime.now().minusDays(1), null);

        given(jwtProvider.isValid(rawToken)).willReturn(true);
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

        given(jwtProvider.isValid(rawToken)).willReturn(true);
        given(refreshTokenReader.findByTokenHash(tokenHash)).willReturn(Optional.of(revokedToken));

        assertThatThrownBy(() -> authService.refresh(new TokenRefreshCommand(rawToken)))
            .isInstanceOf(InvalidRefreshTokenException.class);

        then(refreshTokenWriter).should().revokeAllByUserId(userId);
    }

    @Test
    @DisplayName("DB에 없는 리프레시 토큰으로 갱신 시 401 예외가 발생한다")
    void givenUnknownRefreshToken_whenRefresh_thenThrowsUnauthorized() {
        String rawToken = "unknown-refresh-token";
        String tokenHash = TokenHashUtils.sha256Hex(rawToken);

        given(jwtProvider.isValid(rawToken)).willReturn(true);
        given(refreshTokenReader.findByTokenHash(tokenHash)).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(new TokenRefreshCommand(rawToken)))
            .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    @DisplayName("JWT 형식이 잘못된 리프레시 토큰으로 갱신 시 401 예외가 발생한다")
    void givenMalformedRefreshToken_whenRefresh_thenThrowsUnauthorized() {
        given(jwtProvider.isValid("malformed")).willReturn(false);

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

        authService.logout(new LogoutCommand(rawToken));

        then(refreshTokenWriter).should().revokeByTokenHash(tokenHash);
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
