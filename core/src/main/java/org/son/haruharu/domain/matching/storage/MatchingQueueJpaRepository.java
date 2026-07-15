package org.son.haruharu.domain.matching.storage;

import org.son.haruharu.domain.matching.implement.MatchingQueueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MatchingQueueJpaRepository extends JpaRepository<MatchingQueueEntity, Long> {

    boolean existsByUserIdAndStatus(Long userId, MatchingQueueStatus status);

    Optional<MatchingQueueEntity> findByUserIdAndStatus(Long userId, MatchingQueueStatus status);

    List<MatchingQueueEntity> findAllByStatusOrderByQueuedAtAsc(MatchingQueueStatus status);

    /** 유저의 가장 최근 큐 행(id 내림차순 1건). 홈의 직전 신청 결과 판정용 — 재신청 시 최신 QUEUED 행이 잡힌다. */
    Optional<MatchingQueueEntity> findTopByUserIdOrderByIdDesc(Long userId);

    /** 조건부 상태 전이. 현재 상태가 from일 때만 갱신되므로 동시 취소·만료의 이중 처리(이중 환불)를 원천 차단한다. */
    @Modifying
    @Query("update MatchingQueueEntity q set q.status = :to, q.updatedAt = :now "
            + "where q.id = :id and q.status = :from")
    int transitionStatus(@Param("id") Long id,
                         @Param("from") MatchingQueueStatus from,
                         @Param("to") MatchingQueueStatus to,
                         @Param("now") LocalDateTime now);
}
