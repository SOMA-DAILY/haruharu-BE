package org.son.haruharu.domain.auth.business;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

/**
 * 인증·세션 관리. 토큰 만료 시각만은 주입 Clock이 아니라 시스템 시각을 기준으로 삼는다(#296).
 * ClockConfig가 "시간 구동 로직은 Clock을 주입받는다"고 규정하지만 여기는 의도적 예외다 —
 * local·dev의 AdjustableClock(#180)으로 가상 시각을 앞당겨 방 진행·슬롯·최종선택을 테스트할 때
 * 토큰 만료까지 딸려가면 로그인 세션이 풀려 테스트가 불가능해진다. 운영 프로필의 Clock은
 * Clock.system(Asia/Seoul)이라 시스템 시각과 동일하므로 운영 동작 차이는 없다.
 */
@Slf4j
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
        String providerType = command.providerType().name();
        String providerId = userInfo.providerId();

        User user = userCredentialReader
            .findByProviderTypeAndProviderId(providerType, providerId)
            .map(credential -> {
                User existing = userReader.getById(credential.userId());
                if (!existing.status().canAuthenticate()) {
                    throw new InactiveMemberException();
                }
                return existing;
            })
            .orElseGet(() -> signup(providerType, providerId));

        String status = routingStatus(user);
        log.info("auth login userId={} provider={} status={}", user.id(), providerType, status);
        return issueNewTokenPair(user.id(), status);
    }

    /**
     * 활성 연동이 없는 소셜 계정 가입 처리. 탈퇴 후 재가입은 즉시 허용한다(#245).
     * 탈퇴 잔존 행은 물리 파기(unique 슬롯 해제)한 뒤 신규 User를 생성하며, 파기된 행이 있으면
     * 복귀 유저로 판정해 signup 보너스를 재지급하지 않는다(credential 단위 1회성).
     * 주의: 이 returning 판정이 보너스 재지급을 막는 유일한 지점이다 — grantSignupBonus 의 멱등성은
     * userId 기준이라 재가입으로 새 userId 가 생기면 무력하다.
     * threshold 에 주입 Clock 이 아닌 시스템 시각을 쓰는 이유: 비교 대상인 deleted_at 은 Hibernate
     * @SoftDelete(TIMESTAMP) 가 시스템 클록으로 찍는다. local·dev 의 AdjustableClock(#180)으로 가상 시각을
     * 과거 고정한 상태라면 now(clock) 기준으로는 파기 대상이 0건이 되어 복귀 유저를 신규로 오판(보너스 재지급)한다.
     */
    private User signup(String providerType, String providerId) {
        boolean returning = userCredentialWriter.purgeWithdrawnBefore(
            providerType, providerId, LocalDateTime.now()) > 0;

        User created = userWriter.save(new User(null, UserStatus.ONBOARDING, null));
        userCredentialWriter.save(new UserCredential(null, created.id(), providerType, providerId));
        // 보너스 지급 여부와 무관하게 지갑을 먼저 만든다(#298). 예전에는 보너스 지급의 부수효과로
        // 지갑이 생겨서, 보너스를 skip하는 복귀 유저는 지갑 없이 시작했다.
        coinService.createWallet(created.id());
        if (!returning) {
            coinService.grantSignupBonus(created.id());
        }
        // providerId 는 소셜 계정 식별자(PII)라 남기지 않는다.
        log.info("auth signup userId={} provider={} returning={}", created.id(), providerType, returning);
        return created;
    }

    /** 탈퇴 시 소셜 연동을 soft-delete한다. 남은 deleted_at 은 재가입 시 복귀 유저 판정(보너스 skip)에 쓰인다. */
    @Transactional
    public void deleteCredentials(Long userId) {
        userCredentialWriter.deleteByUserId(userId);
        log.info("auth withdraw userId={}", userId);
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

    /**
     * expiresAt 은 시스템 시각 기준이다(클래스 주석의 의도적 예외 참조).
     * JwtProvider 가 JWT exp 클레임을 Instant.now() 로 찍으므로 같은 기준을 써야 둘이 어긋나지 않는다.
     */
    private void saveRefreshToken(Long userId, String refreshToken) {
        String tokenHash = TokenHashUtils.sha256Hex(refreshToken);
        LocalDateTime expiresAt = LocalDateTime.now().plus(jwtProvider.getRefreshTokenExpiryMs(), ChronoUnit.MILLIS);
        refreshTokenWriter.save(new RefreshToken(null, userId, tokenHash, expiresAt, null));
    }
}
