package org.son.haruharu.domain.cohort.implement;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CohortApplicationRepository {

    Optional<CohortApplication> findById(Long id);

    /** 유저의 가장 최근 신청(id 내림차순 첫 건). */
    Optional<CohortApplication> findLatestByUserId(Long userId);

    boolean existsByUserIdAndStatusIn(Long userId, Collection<CohortApplicationStatus> statuses);

    boolean existsByCohortIdAndStatus(Long cohortId, CohortApplicationStatus status);

    /** 기수의 신청을 id 순으로 행 잠금해 읽는다. 잠금 순서(신청은 id 순)를 지키기 위해 정렬한다. */
    List<CohortApplication> findAllByCohortIdAndStatusInForUpdate(Long cohortId,
                                                                  Collection<CohortApplicationStatus> statuses);

    /** 새 신청만 저장한다. 이후 변경은 아래 조건부 UPDATE 로만 한다. */
    CohortApplication insert(CohortApplication application);

    /**
     * 조건부 종료: 지금 상태가 from 중 하나일 때만 닫는다. 바뀐 행 수(0 또는 1)를 돌려준다.
     * refundedAt 이 null 이 아니면 반환 시각도 함께 적는다.
     */
    int close(Long id, Collection<CohortApplicationStatus> from, CohortApplicationStatus to,
              ApplicationCloseReason reason, LocalDateTime closedAt, LocalDateTime refundedAt);

    /**
     * 조건부 PASS 판정: 지금 상태가 from 일 때만 판정 결과(상태·신청 성별·PASS 성별·생년·대기 시각·확인 사유)를 적는다.
     * 바뀐 행 수(0 또는 1)를 돌려준다.
     */
    int screen(Long id, CohortApplicationStatus from, CohortApplication screened, LocalDateTime updatedAt);

    /** 조건부 설문 저장: 본인 신청이고 지금 상태가 from 중 하나일 때만. 바뀐 행 수를 돌려준다. */
    int updateSurvey(Long id, Long userId, Collection<CohortApplicationStatus> from, ApplicationSurvey survey,
                     LocalDateTime submittedAt);
}
