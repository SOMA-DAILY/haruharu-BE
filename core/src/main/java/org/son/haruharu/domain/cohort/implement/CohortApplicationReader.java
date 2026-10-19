package org.son.haruharu.domain.cohort.implement;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.cohort.storage.exception.CohortApplicationNotFoundException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CohortApplicationReader {

    private final CohortApplicationRepository applicationRepository;

    public CohortApplication getById(Long id) {
        return applicationRepository.findById(id).orElseThrow(CohortApplicationNotFoundException::new);
    }

    /** 본인 신청만 돌려준다. 남의 신청은 있는지조차 드러내지 않도록 없는 것과 같이 404 로 막는다. */
    public CohortApplication getOwned(Long id, Long userId) {
        return applicationRepository.findById(id)
                .filter(application -> application.userId().equals(userId))
                .orElseThrow(CohortApplicationNotFoundException::new);
    }

    public Optional<CohortApplication> findLatestByUserId(Long userId) {
        return applicationRepository.findLatestByUserId(userId);
    }

    public boolean existsInProgress(Long userId) {
        return applicationRepository.existsByUserIdAndStatusIn(userId, CohortApplicationStatus.IN_PROGRESS);
    }

    public boolean existsAssigned(Long cohortId) {
        return applicationRepository.existsByCohortIdAndStatus(cohortId, CohortApplicationStatus.ASSIGNED);
    }

    /** 기수의 배정 전 신청을 id 순으로 잠근다. 잠금 순서: 기수 → 신청(id 순) → 지갑. */
    public List<CohortApplication> lockBeforeAssignment(Long cohortId) {
        return applicationRepository.findAllByCohortIdAndStatusInForUpdate(cohortId,
                CohortApplicationStatus.BEFORE_ASSIGNMENT);
    }
}
