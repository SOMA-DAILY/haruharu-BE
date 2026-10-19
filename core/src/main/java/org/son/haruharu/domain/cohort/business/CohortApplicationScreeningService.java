package org.son.haruharu.domain.cohort.business;

import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.cohort.implement.CohortApplication;
import org.son.haruharu.domain.cohort.implement.CohortApplicationReader;
import org.son.haruharu.domain.cohort.implement.CohortApplicationStatus;
import org.son.haruharu.domain.cohort.implement.CohortApplicationWriter;
import org.son.haruharu.domain.cohort.implement.RecruitTrackReader;
import org.son.haruharu.domain.identity.business.IdentityService;
import org.son.haruharu.domain.identity.business.dto.VerifiedIdentity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * PASS 결과로 PASS 대기 신청을 편성 대기·확인 필요로 넘긴다(#512). 판정 규칙은 {@link CohortApplication#screen}.
 * 두 곳에서 부른다: 본인인증 커밋 직후({@link IdentityVerifiedEventListener}), 그리고 그게 실패했을 때를 대비한 내 신청 조회.
 * 전이는 {@code WHERE status = IDENTITY_PENDING} 조건부 UPDATE 라 몇 번을 돌려도, 동시에 돌려도 한 번만 바뀐다.
 * 참가권은 건드리지 않는다 — 확인 필요여도 쓴 참가권은 그대로 두고 운영진이 판단한다.
 */
@Slf4j
@Service
public class CohortApplicationScreeningService {

    private final CohortApplicationReader applicationReader;
    private final CohortApplicationWriter applicationWriter;
    private final RecruitTrackReader trackReader;
    private final IdentityService identityService;
    private final Clock clock;

    public CohortApplicationScreeningService(CohortApplicationReader applicationReader,
                                             CohortApplicationWriter applicationWriter,
                                             RecruitTrackReader trackReader, IdentityService identityService,
                                             Clock clock) {
        this.applicationReader = applicationReader;
        this.applicationWriter = applicationWriter;
        this.trackReader = trackReader;
        this.identityService = identityService;
        this.clock = clock;
    }

    /**
     * 본인인증 커밋 후 경로. 커밋이 끝난 트랜잭션에는 쓸 수 없으므로 새 트랜잭션을 연다 —
     * 여기서 실패해도 이미 커밋된 PASS 결과는 그대로다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean screenAfterIdentityVerified(Long userId) {
        return screenPending(userId);
    }

    /**
     * 유저의 PASS 대기 신청을 판정한다. 바꿨으면 true. PASS 대기 신청이 없거나, PASS 를 아직 안 했거나,
     * 그 사이 다른 경로가 먼저 판정·취소했으면 false. 진행 중 신청은 사람당 1건이고 진행 중에는 새로 신청할 수 없어
     * PASS 대기 신청은 언제나 가장 최근 신청이다.
     */
    @Transactional
    public boolean screenPending(Long userId) {
        Optional<CohortApplication> latest = applicationReader.findLatestByUserId(userId)
                .filter(application -> application.status() == CohortApplicationStatus.IDENTITY_PENDING);
        if (latest.isEmpty()) return false;
        Optional<VerifiedIdentity> verified = identityService.findVerified(userId);
        if (verified.isEmpty()) return false;

        CohortApplication pending = latest.get();
        LocalDateTime now = LocalDateTime.now(clock);
        CohortApplication screened = pending.screen(verified.get().gender(), verified.get().birthday(),
                trackReader.getById(pending.trackId()), now);
        if (!applicationWriter.screen(screened, now)) return false;
        log.info("cohort-application screen applicationId={} userId={} status={} reviewReason={}",
                screened.id(), userId, screened.status(), screened.reviewReason());
        return true;
    }
}
