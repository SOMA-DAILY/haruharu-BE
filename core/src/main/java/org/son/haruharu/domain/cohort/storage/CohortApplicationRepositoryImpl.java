package org.son.haruharu.domain.cohort.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.cohort.implement.ApplicationCloseReason;
import org.son.haruharu.domain.cohort.implement.ApplicationSurvey;
import org.son.haruharu.domain.cohort.implement.CohortApplication;
import org.son.haruharu.domain.cohort.implement.CohortApplicationRepository;
import org.son.haruharu.domain.cohort.implement.CohortApplicationStatus;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CohortApplicationRepositoryImpl implements CohortApplicationRepository {

    private final CohortApplicationJpaRepository jpaRepository;

    @Override
    public Optional<CohortApplication> findById(Long id) {
        return jpaRepository.findById(id).map(CohortApplicationEntity::toDomain);
    }

    @Override
    public Optional<CohortApplication> findLatestByUserId(Long userId) {
        return jpaRepository.findFirstByUserIdOrderByIdDesc(userId).map(CohortApplicationEntity::toDomain);
    }

    @Override
    public boolean existsByUserIdAndStatusIn(Long userId, Collection<CohortApplicationStatus> statuses) {
        return jpaRepository.existsByUserIdAndStatusIn(userId, statuses);
    }

    @Override
    public boolean existsByCohortIdAndStatus(Long cohortId, CohortApplicationStatus status) {
        return jpaRepository.existsByCohortIdAndStatus(cohortId, status);
    }

    @Override
    public List<CohortApplication> findAllByCohortIdAndStatusInForUpdate(Long cohortId,
                                                                         Collection<CohortApplicationStatus> statuses) {
        return jpaRepository.findAllByCohortIdAndStatusInForUpdate(cohortId, statuses).stream()
                .map(CohortApplicationEntity::toDomain).toList();
    }

    @Override
    public CohortApplication insert(CohortApplication application) {
        if (application.id() != null) throw new IllegalArgumentException("이미 저장된 신청은 다시 저장하지 않는다");
        return jpaRepository.saveAndFlush(CohortApplicationEntity.from(application)).toDomain();
    }

    @Override
    public int close(Long id, Collection<CohortApplicationStatus> from, CohortApplicationStatus to,
                     ApplicationCloseReason reason, LocalDateTime closedAt, LocalDateTime refundedAt) {
        return jpaRepository.close(id, from, to, reason, closedAt, refundedAt);
    }

    @Override
    public int screen(Long id, CohortApplicationStatus from, CohortApplication screened, LocalDateTime updatedAt) {
        return jpaRepository.screen(id, from, screened.status(), screened.declaredGender(), screened.passGender(),
                screened.birthday(), screened.queuedAt(), screened.reviewReason(), updatedAt);
    }

    @Override
    public int updateSurvey(Long id, Long userId, Collection<CohortApplicationStatus> from, ApplicationSurvey survey,
                            LocalDateTime submittedAt) {
        return jpaRepository.updateSurvey(id, userId, from, survey.referralSource(), survey.friendPreference(),
                survey.motivation(), submittedAt);
    }

    @Override
    public int approve(Long id, CohortApplicationStatus from, CohortApplication approved, LocalDateTime updatedAt) {
        return jpaRepository.approve(id, from, approved.status(), approved.declaredGender(), approved.queuedAt(),
                updatedAt);
    }

    @Override
    public int updateMemo(Long id, String memo, LocalDateTime updatedAt) {
        return jpaRepository.updateMemo(id, memo, updatedAt);
    }
}
