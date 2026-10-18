package org.son.haruharu.domain.cohort.storage;

import jakarta.persistence.LockModeType;
import org.son.haruharu.domain.cohort.implement.ApplicantGender;
import org.son.haruharu.domain.cohort.implement.ApplicationCloseReason;
import org.son.haruharu.domain.cohort.implement.ApplicationReviewReason;
import org.son.haruharu.domain.cohort.implement.CohortApplicationStatus;
import org.son.haruharu.domain.cohort.implement.FriendPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CohortApplicationJpaRepository extends JpaRepository<CohortApplicationEntity, Long> {

    Optional<CohortApplicationEntity> findFirstByUserIdOrderByIdDesc(Long userId);

    boolean existsByUserIdAndStatusIn(Long userId, Collection<CohortApplicationStatus> statuses);

    boolean existsByCohortIdAndStatus(Long cohortId, CohortApplicationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CohortApplicationEntity a where a.cohortId = :cohortId and a.status in :statuses order by a.id")
    List<CohortApplicationEntity> findAllByCohortIdAndStatusInForUpdate(
            @Param("cohortId") Long cohortId, @Param("statuses") Collection<CohortApplicationStatus> statuses);

    // 벌크 UPDATE 는 Auditing 을 거치지 않으므로 updated_at 을 직접 적는다.
    // clearAutomatically: 같은 트랜잭션에서 다시 읽을 때 영속성 컨텍스트의 옛 상태 대신 DB 값을 보게 한다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update CohortApplicationEntity a
            set a.status = :to, a.closeReason = :reason, a.closedAt = :closedAt,
                a.refundedAt = :refundedAt, a.updatedAt = :closedAt
            where a.id = :id and a.status in :from
            """)
    int close(@Param("id") Long id, @Param("from") Collection<CohortApplicationStatus> from,
              @Param("to") CohortApplicationStatus to, @Param("reason") ApplicationCloseReason reason,
              @Param("closedAt") LocalDateTime closedAt, @Param("refundedAt") LocalDateTime refundedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update CohortApplicationEntity a
            set a.status = :to, a.declaredGender = :declaredGender, a.gender = :gender, a.birthday = :birthday,
                a.queuedAt = :queuedAt, a.reviewReason = :reviewReason, a.updatedAt = :updatedAt
            where a.id = :id and a.status = :from
            """)
    int screen(@Param("id") Long id, @Param("from") CohortApplicationStatus from,
               @Param("to") CohortApplicationStatus to, @Param("declaredGender") ApplicantGender declaredGender,
               @Param("gender") String gender, @Param("birthday") LocalDate birthday,
               @Param("queuedAt") LocalDateTime queuedAt, @Param("reviewReason") ApplicationReviewReason reviewReason,
               @Param("updatedAt") LocalDateTime updatedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update CohortApplicationEntity a
            set a.referralSource = :referralSource, a.friendPreference = :friendPreference,
                a.motivation = :motivation, a.surveySubmittedAt = :submittedAt, a.updatedAt = :submittedAt
            where a.id = :id and a.userId = :userId and a.status in :from
            """)
    int updateSurvey(@Param("id") Long id, @Param("userId") Long userId,
                     @Param("from") Collection<CohortApplicationStatus> from,
                     @Param("referralSource") String referralSource,
                     @Param("friendPreference") FriendPreference friendPreference,
                     @Param("motivation") String motivation, @Param("submittedAt") LocalDateTime submittedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update CohortApplicationEntity a
            set a.status = :to, a.declaredGender = :declaredGender, a.queuedAt = :queuedAt, a.updatedAt = :updatedAt
            where a.id = :id and a.status = :from
            """)
    int approve(@Param("id") Long id, @Param("from") CohortApplicationStatus from,
                @Param("to") CohortApplicationStatus to, @Param("declaredGender") ApplicantGender declaredGender,
                @Param("queuedAt") LocalDateTime queuedAt, @Param("updatedAt") LocalDateTime updatedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update CohortApplicationEntity a set a.adminMemo = :memo, a.updatedAt = :updatedAt where a.id = :id")
    int updateMemo(@Param("id") Long id, @Param("memo") String memo, @Param("updatedAt") LocalDateTime updatedAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CohortApplicationEntity a where a.id in :ids order by a.id")
    List<CohortApplicationEntity> findAllByIdInForUpdate(@Param("ids") Collection<Long> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update CohortApplicationEntity a
            set a.status = :to, a.roomId = :roomId, a.assignedAt = :assignedAt, a.assignedBy = :assignedBy,
                a.updatedAt = :assignedAt
            where a.id = :id and a.status = :from
            """)
    int assign(@Param("id") Long id, @Param("from") CohortApplicationStatus from,
               @Param("to") CohortApplicationStatus to, @Param("roomId") Long roomId,
               @Param("assignedAt") LocalDateTime assignedAt, @Param("assignedBy") String assignedBy);
}
