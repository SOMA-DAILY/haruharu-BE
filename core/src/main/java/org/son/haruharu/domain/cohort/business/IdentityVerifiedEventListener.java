package org.son.haruharu.domain.cohort.business;

import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.identity.business.event.IdentityVerifiedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 본인인증 커밋 후 PASS 대기 기수 신청을 판정한다(#512).
 * 신청 판정이 실패해도 PASS 결과는 이미 커밋됐고 응답도 성공이어야 하므로 예외를 삼키고 남긴다 —
 * 판정은 내 신청 조회(GET /api/v1/recruit/applications/me)가 다시 시도한다.
 */
@Slf4j
@Component
public class IdentityVerifiedEventListener {

    private final CohortApplicationScreeningService screeningService;

    public IdentityVerifiedEventListener(CohortApplicationScreeningService screeningService) {
        this.screeningService = screeningService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(IdentityVerifiedEvent event) {
        try {
            screeningService.screenAfterIdentityVerified(event.userId());
        } catch (RuntimeException e) {
            log.warn("cohort-application screen-after-identity failed userId={} (내 신청 조회에서 다시 판정)",
                    event.userId(), e);
        }
    }
}
