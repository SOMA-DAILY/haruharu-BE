package org.son.haruharu.domain.auth.business;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.auth.business.command.LogoutCommand;
import org.son.haruharu.domain.auth.business.command.SocialAuthCommand;
import org.son.haruharu.domain.auth.business.command.TokenRefreshCommand;
import org.son.haruharu.domain.auth.business.dto.AuthTokenResponse;
import org.son.haruharu.domain.auth.business.exception.InactiveMemberException;
import org.son.haruharu.domain.auth.business.exception.InvalidRefreshTokenException;
import org.son.haruharu.domain.auth.business.exception.InvalidTokenException;
import org.son.haruharu.domain.auth.implement.RefreshToken;
import org.son.haruharu.domain.auth.implement.RefreshTokenReader;
import org.son.haruharu.domain.auth.implement.RefreshTokenWriter;
import org.son.haruharu.domain.auth.implement.UserCredential;
import org.son.haruharu.domain.auth.implement.UserCredentialReader;
import org.son.haruharu.domain.auth.implement.UserCredentialWriter;
import org.son.haruharu.domain.coin.business.CoinService;
import org.son.haruharu.domain.identity.business.IdentityService;
import org.son.haruharu.domain.identity.implement.VerificationStatus;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserReader;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.implement.UserWriter;
import org.son.haruharu.infrastructure.oauth.OAuthProviderRegistry;
import org.son.haruharu.infrastructure.oauth.OAuthUserInfo;
import org.son.haruharu.utils.JwtProvider;
import org.son.haruharu.utils.TokenHashUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuthService {

    private final OAuthProviderRegistry oAuthProviderRegistry;
    private final UserReader userReader;
    private final UserWriter userWriter;
    private final UserCredentialReader userCredentialReader;
    private final UserCredentialWriter userCredentialWriter;
    private final IdentityService identityService;
    private final CoinService coinService;
    private final RefreshTokenReader refreshTokenReader;
    private final RefreshTokenWriter refreshTokenWriter;
    private final JwtProvider jwtProvider;

    @Transactional
    public AuthTokenResponse authenticate(SocialAuthCommand command) {
        OAuthUserInfo userInfo = oAuthProviderRegistry.get(command.providerType()).verify(command.idToken());

        User user = userCredentialReader
            .findByProviderTypeAndProviderId(command.providerType().name(), userInfo.providerId())
            .map(credential -> {
                User existing = userReader.getById(credential.userId());
                if (!existing.status().canAuthenticate()) {
                    throw new InactiveMemberException();
                }
                return existing;
            })
            .orElseGet(() -> {
                User created = userWriter.save(new User(null, UserStatus.ONBOARDING, null));
                userCredentialWriter.save(new UserCredential(null, created.id(), command.providerType().name(), userInfo.providerId()));
                coinService.grantSignupBonus(created.id());
                return created;
            });

        return issueNewTokenPair(user.id(), routingStatus(user));
    }

    @Transactional
    public AuthTokenResponse refresh(TokenRefreshCommand command) {
        if (!jwtProvider.isValid(command.refreshToken())) {
            throw new InvalidRefreshTokenException();
        }

        String tokenHash = TokenHashUtils.sha256Hex(command.refreshToken());
        RefreshToken refreshToken = refreshTokenReader.findByTokenHash(tokenHash)
            .orElseThrow(InvalidRefreshTokenException::new);

        if (refreshToken.isRevoked()) {
            refreshTokenWriter.revokeAllByUserId(refreshToken.userId());
            throw new InvalidRefreshTokenException();
        }

        if (refreshToken.isExpired()) {
            throw new InvalidRefreshTokenException();
        }

        User user = userReader.getById(refreshToken.userId());
        if (!user.status().canAuthenticate()) {
            throw new InactiveMemberException();
        }

        refreshTokenWriter.revokeByTokenHash(tokenHash);
        return issueNewTokenPair(user.id(), routingStatus(user));
    }

    /**
     * 프론트 라우팅용 상태를 파생한다. 저장된 UserStatus(온보딩 여부)와 본인인증 존재 여부를 하나의 값으로 합친다.
     * ONBOARDING → "ONBOARDING", 온보딩 완료 후 본인인증됨 → "VERIFIED", 완료됐지만 미인증 → "ACTIVE".
     */
    private String routingStatus(User user) {
        if (user.status() == UserStatus.ONBOARDING) {
            return UserStatus.ONBOARDING.name();
        }
        return identityService.isVerified(user.id())
            ? VerificationStatus.VERIFIED.name()
            : UserStatus.ACTIVE.name();
    }

    @Transactional
    public void logout(LogoutCommand command) {
        if (!jwtProvider.isValid(command.refreshToken())) {
            throw new InvalidTokenException();
        }

        String tokenHash = TokenHashUtils.sha256Hex(command.refreshToken());
        RefreshToken refreshToken = refreshTokenReader.findByTokenHash(tokenHash)
            .orElseThrow(InvalidTokenException::new);

        if (refreshToken.isRevoked()) {
            throw new InvalidTokenException();
        }

        refreshTokenWriter.revokeByTokenHash(tokenHash);
    }

    private AuthTokenResponse issueNewTokenPair(Long userId, String userStatus) {
        String accessToken = jwtProvider.generateAccessToken(userId);
        String refreshToken = jwtProvider.generateRefreshToken(userId);
        saveRefreshToken(userId, refreshToken);
        return AuthTokenResponse.withStatus(accessToken, refreshToken, userStatus);
    }

    private void saveRefreshToken(Long userId, String refreshToken) {
        String tokenHash = TokenHashUtils.sha256Hex(refreshToken);
        LocalDateTime expiresAt = LocalDateTime.now().plus(jwtProvider.getRefreshTokenExpiryMs(), ChronoUnit.MILLIS);
        refreshTokenWriter.save(new RefreshToken(null, userId, tokenHash, expiresAt, null));
    }
}
