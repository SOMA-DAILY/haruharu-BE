package org.son.haruharu.domain.cohort.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class CohortApplicationWriter {

    private final CohortApplicationRepository applicationRepository;

    public CohortApplication insert(CohortApplication application) {
        return applicationRepository.insert(application);
    }

    /** 배정 전 신청을 닫는다. refunded 면 반환 시각도 적는다. 이미 다른 경로가 닫았으면 false. */
    public boolean closeBeforeAssignment(Long id, CohortApplicationStatus to, ApplicationCloseReason reason,
                                         boolean refunded, LocalDateTime now) {
        return applicationRepository.close(id, CohortApplicationStatus.BEFORE_ASSIGNMENT, to, reason, now,
                refunded ? now : null) == 1;
    }

    /** PASS 대기 신청에 판정 결과를 적는다. 그 사이 다른 경로가 판정·취소했으면 false. */
    public boolean screen(CohortApplication screened, LocalDateTime now) {
        return applicationRepository.screen(screened.id(), CohortApplicationStatus.IDENTITY_PENDING, screened,
                now) == 1;
    }

    /** 진행 중인 본인 신청의 설문을 저장한다. 그 사이 닫혔으면 false. */
    public boolean updateSurvey(Long id, Long userId, ApplicationSurvey survey, LocalDateTime now) {
        return applicationRepository.updateSurvey(id, userId, CohortApplicationStatus.IN_PROGRESS, survey, now) == 1;
    }
}
