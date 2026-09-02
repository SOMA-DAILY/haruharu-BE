package org.son.haruharu.domain.mission.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.mission.implement.ActivationChannel;
import org.son.haruharu.domain.mission.implement.MissionCardResolution;
import org.son.haruharu.domain.mission.implement.MissionMemberState;

import java.time.LocalDateTime;

@Entity
@Table(name = "mission_member_states",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_mission_member_states_mission_member",
                columnNames = {"room_mission_id", "room_member_id"}),
        indexes = @Index(name = "idx_mission_member_states_member_resolution",
                columnList = "room_member_id,resolved_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MissionMemberStateEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_mission_id", nullable = false)
    private Long roomMissionId;

    @Column(name = "room_member_id", nullable = false)
    private Long roomMemberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "expected_channel", nullable = false, length = 20)
    private ActivationChannel expectedChannel;

    @Column(name = "first_impressed_at")
    private LocalDateTime firstImpressedAt;

    @Column(name = "last_impressed_at")
    private LocalDateTime lastImpressedAt;

    @Column(name = "expected_activity_count", nullable = false)
    private int expectedActivityCount;

    @Column(name = "first_expected_activity_at")
    private LocalDateTime firstExpectedActivityAt;

    @Column(name = "last_expected_activity_at")
    private LocalDateTime lastExpectedActivityAt;

    @Column(name = "cross_channel_activity_count", nullable = false)
    private int crossChannelActivityCount;

    @Column(name = "first_cross_channel_activity_at")
    private LocalDateTime firstCrossChannelActivityAt;

    @Column(name = "last_cross_channel_activity_at")
    private LocalDateTime lastCrossChannelActivityAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution", length = 32)
    private MissionCardResolution resolution;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    public MissionMemberState toDomain() {
        return new MissionMemberState(
                id, roomMissionId, roomMemberId, expectedChannel,
                firstImpressedAt, lastImpressedAt,
                expectedActivityCount, firstExpectedActivityAt, lastExpectedActivityAt,
                crossChannelActivityCount, firstCrossChannelActivityAt, lastCrossChannelActivityAt,
                resolution, resolvedAt, createdAt, updatedAt);
    }
}
