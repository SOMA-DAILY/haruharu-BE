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
import org.son.haruharu.domain.identity.implement.UserIdentityReader;
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
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuthService {

    private final OAuthProviderRegistry oAuthProviderRegistry;
    private final UserReader userReader;
    private final UserWriter userWriter;
    private final UserCredentialReader userCredentialReader;
    private final UserCredentialWriter userCredentialWriter;
    private final UserIdentityReader userIdentityReader;
    private final RefreshTokenReader refreshTokenReader;
    private final RefreshTokenWriter refreshTokenWriter;
    private final JwtProvider jwtProvider;

    @Transactional
    public AuthTokenResponse authenticate(SocialAuthCommand command) {
        OAuthUserInfo userInfo = oAuthProviderRegistry.get(command.providerType()).verify(command.idToken());

        UUID userId = userCredentialReader
            .findByProviderTypeAndProviderId(command.providerType().name(), userInfo.providerId())
            .map(credential -> {
                User user = userReader.getById(credential.userId());
                if (user.status() != UserStatus.ACTIVE) {
                    throw new InactiveMemberException();
                }
                return credential.userId();
            })
            .orElseGet(() -> {
                User user = userWriter.save(new User(null, UserStatus.ACTIVE, null));
                userCredentialWriter.save(new UserCredential(null, user.id(), command.providerType().name(), userInfo.providerId()));
                return user.id();
            });

        String userStatus = userIdentityReader.existsByUserId(userId) ? "VERIFIED" : "UNVERIFIED";
        return issueNewTokenPair(userId, userStatus);
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
        if (user.status() != UserStatus.ACTIVE) {
            throw new InactiveMemberException();
        }

        refreshTokenWriter.revokeByTokenHash(tokenHash);
        return issueNewTokenPairOnly(refreshToken.userId());
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

    private AuthTokenResponse issueNewTokenPair(UUID userId, String userStatus) {
        String accessToken = jwtProvider.generateAccessToken(userId);
        String refreshToken = jwtProvider.generateRefreshToken(userId);
        saveRefreshToken(userId, refreshToken);
        return AuthTokenResponse.withStatus(accessToken, refreshToken, userStatus);
    }

    private AuthTokenResponse issueNewTokenPairOnly(UUID userId) {
        String accessToken = jwtProvider.generateAccessToken(userId);
        String refreshToken = jwtProvider.generateRefreshToken(userId);
        saveRefreshToken(userId, refreshToken);
        return AuthTokenResponse.tokenOnly(accessToken, refreshToken);
    }

    private void saveRefreshToken(UUID userId, String refreshToken) {
        String tokenHash = TokenHashUtils.sha256Hex(refreshToken);
        LocalDateTime expiresAt = LocalDateTime.now().plus(jwtProvider.getRefreshTokenExpiryMs(), ChronoUnit.MILLIS);
        refreshTokenWriter.save(new RefreshToken(null, userId, tokenHash, expiresAt, null));
    }
}
