package org.son.haruharu.domain.mission.chat.storage;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MissionAnnouncementJpaRepository extends JpaRepository<MissionAnnouncementEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from MissionAnnouncementEntity a where a.id = :id")
    Optional<MissionAnnouncementEntity> findByIdForUpdate(@Param("id") Long id);

    List<MissionAnnouncementEntity> findByRoomMissionIdAndIdGreaterThanOrderByIdAsc(Long missionId, Long after, Pageable pageable);
    List<MissionAnnouncementEntity> findByRoomIdOrderByRunAtAscIdAsc(Long roomId);
    List<MissionAnnouncementEntity> findByRoomMissionIdOrderByRunAtAscIdAsc(Long missionId);

    @Query("select a from MissionAnnouncementEntity a where a.status = :status and a.runAt <= :now order by a.runAt, a.id")
    List<MissionAnnouncementEntity> findDue(@Param("status") MissionAnnouncementStatus status,
                                           @Param("now") LocalDateTime now, Pageable pageable);

    @Query("select a from MissionAnnouncementEntity a where a.status = :status and a.updatedAt < :threshold order by a.id")
    List<MissionAnnouncementEntity> findSendingSince(@Param("status") MissionAnnouncementStatus status,
                                                    @Param("threshold") LocalDateTime threshold, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update MissionAnnouncementEntity a set a.status = :to, a.updatedAt = :now "
            + "where a.id = :id and a.status = :from and a.runAt <= :now")
    int claim(@Param("id") Long id, @Param("from") MissionAnnouncementStatus from,
              @Param("to") MissionAnnouncementStatus to, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update MissionAnnouncementEntity a set a.status = :to, a.sentMessageId = :messageId, "
            + "a.sentAt = :now, a.updatedAt = :now where a.id = :id and a.status = :from")
    int markSent(@Param("id") Long id, @Param("messageId") Long messageId, @Param("now") LocalDateTime now,
                 @Param("from") MissionAnnouncementStatus from, @Param("to") MissionAnnouncementStatus to);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update MissionAnnouncementEntity a set a.status = :to, a.failReason = :reason, a.updatedAt = :now "
            + "where a.id = :id and a.status = :from")
    int markFailed(@Param("id") Long id, @Param("from") MissionAnnouncementStatus from,
                   @Param("to") MissionAnnouncementStatus to, @Param("reason") String reason,
                   @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update MissionAnnouncementEntity a set a.status = :to, a.failReason = :reason, a.updatedAt = :now "
            + "where a.id = :id and a.status = :from and a.runAt = :runAt and a.updatedAt = :updatedAt")
    int markFailedIfUnchanged(@Param("id") Long id, @Param("from") MissionAnnouncementStatus from,
                             @Param("to") MissionAnnouncementStatus to, @Param("runAt") LocalDateTime runAt,
                             @Param("updatedAt") LocalDateTime updatedAt, @Param("reason") String reason,
                             @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update MissionAnnouncementEntity a set a.runAt = :runAt, a.content = :content, a.push = :push, "
            + "a.pushTitle = :title, a.pushBody = :body, a.updatedBy = :actor, a.updatedAt = :now "
            + "where a.id = :id and a.status = :scheduled")
    int updateIfScheduled(@Param("id") Long id, @Param("runAt") LocalDateTime runAt, @Param("content") String content,
                          @Param("push") boolean push, @Param("title") String title, @Param("body") String body,
                          @Param("actor") String actor, @Param("now") LocalDateTime now,
                          @Param("scheduled") MissionAnnouncementStatus scheduled);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update MissionAnnouncementEntity a set a.status = :to, a.updatedBy = :actor, a.updatedAt = :now "
            + "where a.id = :id and a.status = :from")
    int cancelIfScheduled(@Param("id") Long id, @Param("actor") String actor, @Param("now") LocalDateTime now,
                          @Param("from") MissionAnnouncementStatus from, @Param("to") MissionAnnouncementStatus to);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update MissionAnnouncementEntity a set a.status = :to, a.updatedBy = :actor, a.updatedAt = :now "
            + "where a.roomMissionId = :missionId and a.status = :from")
    int cancelScheduledForMission(@Param("missionId") Long missionId, @Param("actor") String actor,
                                  @Param("now") LocalDateTime now, @Param("from") MissionAnnouncementStatus from,
                                  @Param("to") MissionAnnouncementStatus to);
}
