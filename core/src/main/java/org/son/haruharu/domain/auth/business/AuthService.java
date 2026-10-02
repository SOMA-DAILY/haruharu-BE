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
import org.son.haruharu.utils.JwtValidation;
import org.son.haruharu.utils.TokenHashUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 인증·세션 관리. 토큰 만료 시각만은 주입 Clock이 아니라 시스템 시각을 기준으로 삼는다(#296).
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuthService {

    /**
     * 재사용 탐지 grace(#417). 앱이 refresh 를 병렬로 호출하면 늦게 도착한 요청은 첫 요청이 방금 revoke 한 토큰을
     * 들고 온다. 이를 탈취 재사용으로 간주해 revokeAllByUserId 하면 정상 세션까지 전 기기 로그아웃된다.
     * revoke 직후 이 창 안의 재제시는 401 만 주고 전체 폐기는 하지 않는다. 창 밖 재제시는 기존대로 전체 폐기.
     * 창 안에서는 탈취 봉쇄가 꺼지는 셈이라 짧게 둔다 — 운영 실측 병렬 간격 87ms 를 넉넉히 덮는 2초.
     */
    static final Duration REUSE_DETECTION_GRACE = Duration.ofSeconds(2);

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
     * @SoftDelete(TIMESTAMP)가 시스템 시각으로 기록하므로 같은 기준으로 비교한다.
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

    /**
     * noRollbackFor: 재사용 탐지의 revokeAllByUserId 는 거절 응답과 함께 커밋돼야 한다. 기본 규칙대로면
     * InvalidRefreshTokenException(RuntimeException) 이 트랜잭션을 롤백해 전체 폐기가 저장되지 않았다(#417 발견).
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthTokenResponse refresh(TokenRefreshCommand command) {
        JwtValidation validation = jwtProvider.validate(command.refreshToken());
        if (validation != JwtValidation.VALID) {
            throw rejectRefresh("JWT_" + validation, null);
        }

        String tokenHash = TokenHashUtils.sha256Hex(command.refreshToken());
        RefreshToken refreshToken = refreshTokenReader.findByTokenHash(tokenHash)
            .orElseThrow(() -> rejectRefresh("NOT_FOUND", null));

        if (refreshToken.isRevokedWithin(REUSE_DETECTION_GRACE)) {
            throw rejectRefresh("REVOKED_IN_GRACE", refreshToken.userId());
        }

        if (refreshToken.isRevoked()) {
            refreshTokenWriter.revokeAllByUserId(refreshToken.userId());
            throw rejectRefresh("REVOKED_REUSE", refreshToken.userId());
        }

        if (refreshToken.isExpired()) {
            throw rejectRefresh("EXPIRED", refreshToken.userId());
        }

        User user = userReader.getById(refreshToken.userId());
        if (!user.status().canAuthenticate()) {
            throw new InactiveMemberException();
        }

        // 조회~여기 사이에 다른 요청이 먼저 소비했을 수 있다. 원자적 소비에 실패한 쪽은 병렬 재발급의 패자다 —
        // 방금 revoke 된 토큰이므로 grace 재제시와 같이 401 만 주고 전체 폐기는 하지 않는다.
        if (!refreshTokenWriter.revokeByTokenHash(tokenHash)) {
            throw rejectRefresh("CONSUMED_CONCURRENTLY", user.id());
        }
        return issueNewTokenPair(user.id(), routingStatus(user));
    }

    /**
     * refresh 거절 사유를 서버 로그에만 남긴다(#417, #259 패턴). 응답은 단일 코드(AUTH_INVALID_REFRESH_TOKEN)를
     * 유지한다 — 사유를 노출하면 계정 열거에 악용된다. 토큰 원문·해시는 남기지 않는다.
     */
    private InvalidRefreshTokenException rejectRefresh(String reason, Long userId) {
        log.warn("auth refresh rejected reason={} userId={}", reason, userId);
        return new InvalidRefreshTokenException();
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

        // 조회~여기 사이에 refresh 가 먼저 소비했으면 조회 시 revoked 로 본 것과 같은 결과(#417)
        if (!refreshTokenWriter.revokeByTokenHash(tokenHash)) {
            throw new InvalidTokenException();
        }
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
