package org.son.haruharu.domain.mission.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface MissionMemberStateJpaRepository extends JpaRepository<MissionMemberStateEntity, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO mission_member_states (
                room_mission_id, room_member_id, expected_channel,
                expected_activity_count, cross_channel_activity_count,
                created_at, updated_at)
            SELECT rm.id, member.id, revision.activation_channel, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
            FROM room_missions rm
            JOIN mission_revisions revision ON revision.id = rm.mission_revision_id
            JOIN room_members member ON member.room_id = rm.room_id
            WHERE rm.opens_at <= :now
              AND rm.opens_at > :fromExclusive
              AND member.joined_at <= rm.opens_at
              AND rm.deleted_at IS NULL
              AND member.deleted_at IS NULL
              AND NOT EXISTS (
                  SELECT 1
                  FROM mission_member_states state
                  WHERE state.room_mission_id = rm.id
                    AND state.room_member_id = member.id)
            ORDER BY rm.opens_at, rm.id, member.id
            LIMIT :batchSize
            ON CONFLICT (room_mission_id, room_member_id) DO NOTHING
            """, nativeQuery = true)
    int initializeOpenedTargets(
            @Param("fromExclusive") LocalDateTime fromExclusive,
            @Param("now") LocalDateTime now,
            @Param("batchSize") int batchSize);

    @Modifying
    @Query(value = """
            INSERT INTO mission_member_states (
                room_mission_id, room_member_id, expected_channel,
                expected_activity_count, cross_channel_activity_count,
                created_at, updated_at)
            SELECT rm.id, member.id, revision.activation_channel, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
            FROM room_missions rm
            JOIN mission_revisions revision ON revision.id = rm.mission_revision_id
            JOIN room_members member ON member.id = :roomMemberId
            WHERE rm.id = :roomMissionId
              AND member.room_id = rm.room_id
              AND member.joined_at <= rm.opens_at
              AND rm.deleted_at IS NULL
              AND member.deleted_at IS NULL
            ON CONFLICT (room_mission_id, room_member_id) DO NOTHING
            """, nativeQuery = true)
    int initializeTarget(
            @Param("roomMissionId") Long roomMissionId,
            @Param("roomMemberId") Long roomMemberId);

    @Modifying
    @Query(value = """
            INSERT INTO mission_member_states (
                room_mission_id, room_member_id, expected_channel,
                expected_activity_count, cross_channel_activity_count,
                created_at, updated_at)
            SELECT rm.id, member.id, revision.activation_channel, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
            FROM room_missions rm
            JOIN mission_revisions revision ON revision.id = rm.mission_revision_id
            JOIN room_members member ON member.id = :roomMemberId
            WHERE rm.room_id = :roomId
              AND member.room_id = rm.room_id
              AND member.joined_at <= rm.opens_at
              AND rm.opens_at <= :occurredAt
              AND (rm.closes_at IS NULL OR :occurredAt < rm.closes_at)
              AND rm.deleted_at IS NULL
              AND member.deleted_at IS NULL
            ON CONFLICT (room_mission_id, room_member_id) DO NOTHING
            """, nativeQuery = true)
    int initializeActivityTargets(
            @Param("roomId") Long roomId,
            @Param("roomMemberId") Long roomMemberId,
            @Param("occurredAt") LocalDateTime occurredAt);

    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE mission_member_states
            SET first_impressed_at = CASE
                    WHEN first_impressed_at IS NULL OR :occurredAt < first_impressed_at THEN :occurredAt
                    ELSE first_impressed_at END,
                last_impressed_at = CASE
                    WHEN last_impressed_at IS NULL OR :occurredAt > last_impressed_at THEN :occurredAt
                    ELSE last_impressed_at END,
                resolution = CASE
                    WHEN resolution IS NULL
                     AND last_expected_activity_at IS NOT NULL
                     AND last_expected_activity_at >= CASE
                         WHEN first_impressed_at IS NULL OR :occurredAt < first_impressed_at THEN :occurredAt
                         ELSE first_impressed_at END
                    THEN CASE expected_channel
                        WHEN 'VIDEO' THEN 'VIDEO_UPLOAD_AFTER_VIEW'
                        WHEN 'GROUP_CHAT' THEN 'CHAT_MESSAGE_AFTER_VIEW'
                        END
                    ELSE resolution END,
                resolved_at = CASE
                    WHEN resolution IS NULL
                     AND last_expected_activity_at IS NOT NULL
                     AND last_expected_activity_at >= CASE
                         WHEN first_impressed_at IS NULL OR :occurredAt < first_impressed_at THEN :occurredAt
                         ELSE first_impressed_at END
                    THEN CASE
                        WHEN first_expected_activity_at >= CASE
                            WHEN first_impressed_at IS NULL OR :occurredAt < first_impressed_at THEN :occurredAt
                            ELSE first_impressed_at END
                        THEN first_expected_activity_at
                        ELSE last_expected_activity_at END
                    ELSE resolved_at END,
                updated_at = CURRENT_TIMESTAMP
            WHERE room_mission_id = :roomMissionId
              AND room_member_id = :roomMemberId
              AND deleted_at IS NULL
            """, nativeQuery = true)
    int impress(
            @Param("roomMissionId") Long roomMissionId,
            @Param("roomMemberId") Long roomMemberId,
            @Param("occurredAt") LocalDateTime occurredAt);

    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE mission_member_states
            SET resolution = COALESCE(resolution, 'DISMISSED'),
                resolved_at = COALESCE(resolved_at, :occurredAt),
                updated_at = CURRENT_TIMESTAMP
            WHERE room_mission_id = :roomMissionId
              AND room_member_id = :roomMemberId
              AND deleted_at IS NULL
            """, nativeQuery = true)
    int dismiss(
            @Param("roomMissionId") Long roomMissionId,
            @Param("roomMemberId") Long roomMemberId,
            @Param("occurredAt") LocalDateTime occurredAt);

    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE mission_member_states state
            SET expected_activity_count = expected_activity_count
                    + CASE WHEN expected_channel = :#{#channel.name()} THEN 1 ELSE 0 END,
                first_expected_activity_at = CASE
                    WHEN expected_channel <> :#{#channel.name()} THEN first_expected_activity_at
                    WHEN first_expected_activity_at IS NULL OR :occurredAt < first_expected_activity_at THEN :occurredAt
                    ELSE first_expected_activity_at END,
                last_expected_activity_at = CASE
                    WHEN expected_channel <> :#{#channel.name()} THEN last_expected_activity_at
                    WHEN last_expected_activity_at IS NULL OR :occurredAt > last_expected_activity_at THEN :occurredAt
                    ELSE last_expected_activity_at END,
                cross_channel_activity_count = cross_channel_activity_count
                    + CASE WHEN expected_channel <> :#{#channel.name()} THEN 1 ELSE 0 END,
                first_cross_channel_activity_at = CASE
                    WHEN expected_channel = :#{#channel.name()} THEN first_cross_channel_activity_at
                    WHEN first_cross_channel_activity_at IS NULL OR :occurredAt < first_cross_channel_activity_at THEN :occurredAt
                    ELSE first_cross_channel_activity_at END,
                last_cross_channel_activity_at = CASE
                    WHEN expected_channel = :#{#channel.name()} THEN last_cross_channel_activity_at
                    WHEN last_cross_channel_activity_at IS NULL OR :occurredAt > last_cross_channel_activity_at THEN :occurredAt
                    ELSE last_cross_channel_activity_at END,
                resolution = CASE
                    WHEN resolution IS NULL
                     AND expected_channel = :#{#channel.name()}
                     AND first_impressed_at IS NOT NULL
                     AND :occurredAt >= first_impressed_at THEN :#{#resolution.name()}
                    ELSE resolution END,
                resolved_at = CASE
                    WHEN resolution IS NULL
                     AND expected_channel = :#{#channel.name()}
                     AND first_impressed_at IS NOT NULL
                     AND :occurredAt >= first_impressed_at THEN :occurredAt
                    ELSE resolved_at END,
                updated_at = CURRENT_TIMESTAMP
            WHERE room_member_id = :roomMemberId
              AND deleted_at IS NULL
              AND room_mission_id IN (
                  SELECT rm.id FROM room_missions rm
                  WHERE rm.room_id = :roomId
                    AND rm.opens_at <= :occurredAt
                    AND (rm.closes_at IS NULL OR :occurredAt < rm.closes_at)
                    AND rm.deleted_at IS NULL)
            """, nativeQuery = true)
    int recordActivity(
            @Param("roomId") Long roomId,
            @Param("roomMemberId") Long roomMemberId,
            @Param("channel") org.son.haruharu.domain.mission.implement.ActivationChannel channel,
            @Param("resolution") org.son.haruharu.domain.mission.implement.MissionCardResolution resolution,
            @Param("occurredAt") LocalDateTime occurredAt);

    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE mission_member_states
            SET resolution = 'EXPIRED',
                resolved_at = :closesAt,
                updated_at = CURRENT_TIMESTAMP
            WHERE room_mission_id = :roomMissionId
              AND resolution IS NULL
              AND deleted_at IS NULL
            """, nativeQuery = true)
    int expireUnresolved(
            @Param("roomMissionId") Long roomMissionId,
            @Param("closesAt") LocalDateTime closesAt);

    /**
     * outcome 확정용 단일 행 집계. 활동(activated)과 카드 해소(resolution)는 별개다 —
     * 노출 없이 활동한 멤버도 활동으로 센다.
     * 컬럼 순서: target, impressed, activated, expectedChannel, crossChannel, dismissed, expired.
     */
    @Query(value = """
            SELECT
                COUNT(*),
                COUNT(*) FILTER (WHERE first_impressed_at IS NOT NULL),
                COUNT(*) FILTER (WHERE expected_activity_count > 0 OR cross_channel_activity_count > 0),
                COUNT(*) FILTER (WHERE expected_activity_count > 0),
                COUNT(*) FILTER (WHERE cross_channel_activity_count > 0),
                COUNT(*) FILTER (WHERE resolution = 'DISMISSED'),
                COUNT(*) FILTER (WHERE resolution = 'EXPIRED')
            FROM mission_member_states
            WHERE room_mission_id = :roomMissionId
              AND deleted_at IS NULL
            """, nativeQuery = true)
    List<Object[]> summarize(@Param("roomMissionId") Long roomMissionId);

    @Query("""
            select state.roomMissionId from MissionMemberStateEntity state
            where state.roomMemberId = :roomMemberId
              and state.roomMissionId in :roomMissionIds
              and state.resolvedAt is not null
            """)
    Set<Long> findResolvedRoomMissionIds(
            @Param("roomMemberId") Long roomMemberId,
            @Param("roomMissionIds") Collection<Long> roomMissionIds);
}
