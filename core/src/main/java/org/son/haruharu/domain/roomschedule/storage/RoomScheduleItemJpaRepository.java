package org.son.haruharu.domain.roomschedule.storage;

import org.son.haruharu.domain.roomschedule.implement.RoomScheduleStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 상태 전이는 전부 벌크 UPDATE(더티체킹 금지 — 두 인스턴스가 같은 행을 둘 다 보내는 걸 막는다, #457).
 * 벌크 UPDATE 는 @LastModifiedDate 를 타지 않으므로 updatedAt 을 직접 쓴다.
 */
public interface RoomScheduleItemJpaRepository extends JpaRepository<RoomScheduleItemEntity, Long> {

    List<RoomScheduleItemEntity> findByRoomIdOrderByRunAtAscIdAsc(Long roomId);

    @Modifying(clearAutomatically = true)
    @Query("update RoomScheduleItemEntity i set i.runAt = :runAt, i.revealFields = :revealFields, "
            + "i.messageContent = :messageContent, i.push = :push, i.pushTitle = :pushTitle, i.pushBody = :pushBody, "
            + "i.updatedBy = :actor, i.updatedAt = :now "
            + "where i.id = :id and i.roomId = :roomId and i.status = :scheduled")
    int updateIfScheduled(@Param("id") Long id, @Param("roomId") Long roomId, @Param("runAt") LocalDateTime runAt,
                          @Param("revealFields") String revealFields, @Param("messageContent") String messageContent,
                          @Param("push") boolean push, @Param("pushTitle") String pushTitle,
                          @Param("pushBody") String pushBody, @Param("actor") String actor,
                          @Param("now") LocalDateTime now, @Param("scheduled") RoomScheduleStatus scheduled);

    @Modifying(clearAutomatically = true)
    @Query("update RoomScheduleItemEntity i set i.runAt = :runAt, i.updatedBy = :actor, i.updatedAt = :now "
            + "where i.id = :id and i.roomId = :roomId and i.status = :scheduled")
    int reschedule(@Param("id") Long id, @Param("roomId") Long roomId, @Param("runAt") LocalDateTime runAt,
                   @Param("actor") String actor, @Param("now") LocalDateTime now,
                   @Param("scheduled") RoomScheduleStatus scheduled);

    @Modifying(clearAutomatically = true)
    @Query("update RoomScheduleItemEntity i set i.status = :to, i.updatedBy = :actor, i.updatedAt = :now "
            + "where i.id = :id and i.roomId = :roomId and i.status = :from")
    int transitionInRoom(@Param("id") Long id, @Param("roomId") Long roomId, @Param("from") RoomScheduleStatus from,
                         @Param("to") RoomScheduleStatus to, @Param("actor") String actor,
                         @Param("now") LocalDateTime now);

    // claim: run_at 도 다시 확인한다 — 조회 뒤 어드민이 시각을 미래로 미뤘으면 집지 않는다.
    @Modifying(clearAutomatically = true)
    @Query("update RoomScheduleItemEntity i set i.status = :to, i.updatedAt = :now "
            + "where i.id = :id and i.status = :from and i.runAt <= :now")
    int claimDue(@Param("id") Long id, @Param("from") RoomScheduleStatus from, @Param("to") RoomScheduleStatus to,
                 @Param("now") LocalDateTime now);

    boolean existsByRoomIdAndRevealFieldsIsNotNull(Long roomId);

    @Modifying(clearAutomatically = true)
    @Query("update RoomScheduleItemEntity i set i.status = :to, i.updatedAt = :now "
            + "where i.id = :id and i.status = :from")
    int transition(@Param("id") Long id, @Param("from") RoomScheduleStatus from, @Param("to") RoomScheduleStatus to,
                   @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true)
    @Query("update RoomScheduleItemEntity i set i.status = :to, i.sentMessageId = :messageId, i.sentAt = :now, "
            + "i.updatedAt = :now where i.id = :id and i.status = :from")
    int markSent(@Param("id") Long id, @Param("messageId") Long messageId, @Param("now") LocalDateTime now,
                 @Param("from") RoomScheduleStatus from, @Param("to") RoomScheduleStatus to);

    @Modifying(clearAutomatically = true)
    @Query("update RoomScheduleItemEntity i set i.status = :to, i.failReason = :reason, i.updatedAt = :now "
            + "where i.id = :id and i.status = :from")
    int markFailed(@Param("id") Long id, @Param("reason") String reason, @Param("now") LocalDateTime now,
                   @Param("from") RoomScheduleStatus from, @Param("to") RoomScheduleStatus to);

    @Query("select i from RoomScheduleItemEntity i where i.status = :status and i.runAt <= :now "
            + "order by i.runAt, i.id")
    List<RoomScheduleItemEntity> findDue(@Param("status") RoomScheduleStatus status, @Param("now") LocalDateTime now,
                                         Pageable pageable);

    @Query("select i from RoomScheduleItemEntity i where i.status = :status and i.updatedAt < :threshold "
            + "order by i.id")
    List<RoomScheduleItemEntity> findStatusUpdatedBefore(@Param("status") RoomScheduleStatus status,
                                                         @Param("threshold") LocalDateTime threshold,
                                                         Pageable pageable);

    @Query("select i from RoomScheduleItemEntity i where i.roomId = :roomId and i.status <> :canceled "
            + "and i.revealFields is not null")
    List<RoomScheduleItemEntity> findRevealRows(@Param("roomId") Long roomId,
                                                @Param("canceled") RoomScheduleStatus canceled);
}
