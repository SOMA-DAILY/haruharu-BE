package org.son.haruharu.domain.identity.business;

import org.son.haruharu.domain.identity.business.command.VerifyIdentityCommand;
import org.son.haruharu.domain.identity.business.dto.VerifiedIdentity;
import org.son.haruharu.domain.identity.business.exception.DuplicateCiException;
import org.son.haruharu.domain.identity.business.exception.UnderageNotAllowedException;
import org.son.haruharu.domain.identity.business.exception.UserAlreadyVerifiedException;
import org.son.haruharu.domain.identity.implement.UserIdentity;
import org.son.haruharu.domain.identity.implement.UserIdentityReader;
import org.son.haruharu.domain.identity.implement.UserIdentityWriter;
import org.son.haruharu.infrastructure.pass.PassVerificationPort;
import org.son.haruharu.infrastructure.pass.PassVerificationResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class IdentityService {

    /** 서비스 이용 최소 만 나이. AGE_19 약관 자기신고와 별개로 실명인증 생년월일로 서버가 강제한다(#207). */
    private static final int MINIMUM_AGE = 19;

    private final UserIdentityReader userIdentityReader;
    private final UserIdentityWriter userIdentityWriter;
    private final PassVerificationPort passVerificationPort;
    private final Clock clock;

    public IdentityService(
        UserIdentityReader userIdentityReader,
        UserIdentityWriter userIdentityWriter,
        PassVerificationPort passVerificationPort,
        Clock clock
    ) {
        this.userIdentityReader = userIdentityReader;
        this.userIdentityWriter = userIdentityWriter;
        this.passVerificationPort = passVerificationPort;
        this.clock = clock;
    }

    public boolean isVerified(Long userId) {
        return userIdentityReader.existsByUserId(userId);
    }

    /** 본인인증된 유저의 성별·생년월일을 반환한다. 미인증(인증 정보 없음)이면 empty. */
    public Optional<VerifiedIdentity> findVerified(Long userId) {
        return userIdentityReader.findByUserId(userId)
                .map(i -> new VerifiedIdentity(i.gender(), i.birthday()));
    }

    @Transactional
    public void deleteIdentity(Long userId) {
        userIdentityWriter.deleteByUserId(userId);
    }

    /**
     * 데모/심사 계정 본인인증 시드(#264). 리뷰어(해외)는 다날 PASS를 통과할 수 없으므로,
     * PASS 검증을 우회해 결정적 더미 CI/DI로 인증 상태를 만든다(실서비스 verifyIdentity와 분리).
     * 이미 인증된 유저면 멱등적으로 skip. CI는 "DEMO_CI_{userId}"라 실사용자 CI와 충돌하지 않는다.
     */
    @Transactional
    public void seedDemoIdentity(Long userId, String gender, LocalDate birthday) {
        if (userIdentityReader.existsByUserId(userId)) {
            return;
        }
        // PASS 우회 경로여도 미성년 차단 불변식은 verifyIdentity와 동일하게 자체 강제한다(#207) —
        // 호출자(시더) 신뢰에만 의존하지 않아, 향후 이 메서드가 외부 입력에 노출돼도 미성년이 새지 않는다.
        if (Period.between(birthday, LocalDate.now(clock)).getYears() < MINIMUM_AGE) {
            throw new UnderageNotAllowedException();
        }
        UserIdentity identity = new UserIdentity(
            null, userId,
            "DEMO_CI_" + userId, "DEMO_DI_" + userId,
            null, birthday, gender, "데모심사", null, false,
            LocalDateTime.now(clock)
        );
        userIdentityWriter.save(identity);
    }

    @Transactional
    public void verifyIdentity(Long userId, VerifyIdentityCommand command) {
        if (userIdentityReader.existsByUserId(userId)) {
            throw new UserAlreadyVerifiedException();
        }
        PassVerificationResult result = passVerificationPort.verify(command.identityVerificationId());
        // 만 나이 기준 미성년 차단 — 오늘이 19번째 생일이면 통과(만 19세)
        if (Period.between(result.birthday(), LocalDate.now(clock)).getYears() < MINIMUM_AGE) {
            throw new UnderageNotAllowedException();
        }
        if (userIdentityReader.existsByCi(result.ci())) {
            throw new DuplicateCiException();
        }
        // 탈퇴 후 재인증은 즉시 허용한다(#245). 탈퇴 잔존 행은 물리 파기하지 않는다 —
        // ci_hash 유니크가 partial index(WHERE deleted_at IS NULL)라 soft-delete된 행은 슬롯을 점유하지 않는다.
        // CI/DI는 IdentityCleanupScheduler가 보관기간(30일) 경과 후 파기한다.
        UserIdentity identity = new UserIdentity(
            null, userId,
            result.ci(), result.di(),
            result.phoneNumber(), result.birthday(), result.gender(),
            result.userName(), result.operator(), result.isForeigner(),
            LocalDateTime.now(clock)
        );
        userIdentityWriter.save(identity);
    }
}
