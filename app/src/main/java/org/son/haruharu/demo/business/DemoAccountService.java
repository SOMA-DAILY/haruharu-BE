package org.son.haruharu.demo.business;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.son.haruharu.demo.business.exception.DemoAccessDeniedException;
import org.son.haruharu.domain.auth.business.dto.AuthTokenResponse;
import org.son.haruharu.domain.auth.implement.RefreshToken;
import org.son.haruharu.domain.auth.implement.RefreshTokenWriter;
import org.son.haruharu.domain.auth.implement.UserCredential;
import org.son.haruharu.domain.auth.implement.UserCredentialReader;
import org.son.haruharu.domain.demoaccess.implement.DemoAccessCode;
import org.son.haruharu.domain.demoaccess.implement.DemoAccessCodeReader;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.utils.JwtProvider;
import org.son.haruharu.utils.TokenHashUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 심사용 데모 로그인(#352). 운영진이 어드민에서 설정한 접근 코드로 게이트하고, 통과 시 고정 데모 계정을 OAuth 없이 인증한다.
 * <p>
 * <b>데모 식별은 플래그가 아니라 자연키다.</b> 데모 계정은 {@code providerType="DEMO"} 인 UserCredential 을 가진
 * 평범한 유저일 뿐이라, users 테이블에도 core 어디에도 데모 표식이 없다. 정상 로그인이 소셜 크레덴셜로 유저를
 * 찾는 것과 완전히 같은 메커니즘을 재사용한다 — core 도메인은 데모의 존재를 끝까지 모른다.
 * <p>
 * 계정은 로그인 시점에 lazy 하게 멱등 생성한다(서버 시작 훅 없음, DB 리셋에 자가 복구). 토큰 발급은 AuthService 를
 * 건드리지 않으려고 JwtProvider·RefreshTokenWriter 를 직접 조합한다(도메인 무변경 원칙).
 */
@Service
@Transactional(readOnly = true)
public class DemoAccountService {

    private final DemoAccessCodeReader demoAccessCodeReader;
    private final UserCredentialReader userCredentialReader;
    private final DemoUserProvisioner demoUserProvisioner;
    private final DemoRoomService demoRoomService;
    private final RefreshTokenWriter refreshTokenWriter;
    private final JwtProvider jwtProvider;
    private final String demoProviderId;

    public DemoAccountService(DemoAccessCodeReader demoAccessCodeReader,
                              UserCredentialReader userCredentialReader,
                              DemoUserProvisioner demoUserProvisioner,
                              DemoRoomService demoRoomService,
                              RefreshTokenWriter refreshTokenWriter,
                              JwtProvider jwtProvider,
                              @Value("${demo.provider-id:haruharu-demo}") String demoProviderId) {
        this.demoAccessCodeReader = demoAccessCodeReader;
        this.userCredentialReader = userCredentialReader;
        this.demoUserProvisioner = demoUserProvisioner;
        this.demoRoomService = demoRoomService;
        this.refreshTokenWriter = refreshTokenWriter;
        this.jwtProvider = jwtProvider;
        this.demoProviderId = demoProviderId;
    }

    /** 접근 코드를 검증하고, 통과 시 데모 계정을(없으면 생성해) 확보하고 신선한 방을 세운 뒤 JWT 를 발급한다. */
    @Transactional
    public AuthTokenResponse login(String submittedCode) {
        verifyAccessCode(submittedCode);
        Long userId = ensureDemoUserId();
        demoRoomService.rebuildIfStale(userId); // 리뷰어가 접속하는 그 순간 활동 중인 방을 보게 한다
        return issueTokens(userId);
    }

    /** 접근 코드를 검증하고, 데모 계정이 있으면 그 방을 리셋한다(수동). 계정이 없으면 지울 게 없으므로 no-op. */
    @Transactional
    public void reset(String submittedCode) {
        verifyAccessCode(submittedCode);
        findDemoCredential().ifPresent(credential -> demoRoomService.reset(credential.userId()));
    }

    /**
     * 어드민이 설정한 현재 코드와 대조한다. 미설정이면 로그인은 닫힌 것으로 본다. 코드 비교는 타이밍 공격 방어로 상수 시간.
     */
    private void verifyAccessCode(String submittedCode) {
        String current = demoAccessCodeReader.findCurrent()
                .map(DemoAccessCode::code)
                .orElseThrow(DemoAccessDeniedException::new); // 미설정 = 닫힘
        boolean matches = MessageDigest.isEqual(
                current.getBytes(StandardCharsets.UTF_8), submittedCode.getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            throw new DemoAccessDeniedException();
        }
    }

    /** DEMO 크레덴셜로 데모 유저를 찾고, 없으면 생성한다(멱등, 동시 최초 호출 레이스 방어 포함). */
    private Long ensureDemoUserId() {
        Optional<UserCredential> existing = findDemoCredential();
        if (existing.isPresent()) {
            return existing.get().userId();
        }
        try {
            // REQUIRES_NEW 하위 트랜잭션에서 생성 — 레이스에서 지면 이 하위 트랜잭션만 롤백되고 바깥은 살아있다.
            return demoUserProvisioner.create(demoProviderId);
        } catch (DataIntegrityViolationException raceLost) {
            // 동시 최초 호출 — 다른 트랜잭션이 먼저 크레덴셜을 만들었다. 바깥 트랜잭션은 정상이라 재조회로 그 계정을 재사용한다.
            return findDemoCredential()
                    .map(UserCredential::userId)
                    .orElseThrow(() -> raceLost);
        }
    }

    private Optional<UserCredential> findDemoCredential() {
        return userCredentialReader.findByProviderTypeAndProviderId(
                DemoUserProvisioner.DEMO_PROVIDER_TYPE, demoProviderId);
    }

    /**
     * AuthService.issueNewTokenPair 와 동일한 절차를 데모 전용으로 재현한다(refresh 는 해시로 DB 저장 → 이후 갱신 동작).
     * 만료 시각은 시스템 시각 기준 — JwtProvider 가 exp 를 시스템 시각으로 찍으므로 둘을 맞춘다(#296).
     */
    private AuthTokenResponse issueTokens(Long userId) {
        String accessToken = jwtProvider.generateAccessToken(userId);
        String refreshToken = jwtProvider.generateRefreshToken(userId);
        String tokenHash = TokenHashUtils.sha256Hex(refreshToken);
        LocalDateTime expiresAt = LocalDateTime.now().plus(jwtProvider.getRefreshTokenExpiryMs(), ChronoUnit.MILLIS);
        refreshTokenWriter.save(new RefreshToken(null, userId, tokenHash, expiresAt, null));
        return AuthTokenResponse.withStatus(accessToken, refreshToken, UserStatus.ACTIVE.name());
    }
}
