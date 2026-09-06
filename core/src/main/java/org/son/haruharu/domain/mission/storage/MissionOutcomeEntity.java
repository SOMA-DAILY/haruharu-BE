package org.son.haruharu.domain.mission.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.mission.implement.MissionOutcome;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 확정된 미션 성과 스냅샷. (room_mission_id, metric_schema_version) 유니크가 재집계 멱등의 최종 방어선이다.
 * 집계식이 바뀌면 기존 행을 UPDATE 하지 않고 새 버전 행을 추가한다.
 */
@Entity
@Table(
        name = "mission_outcomes",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_mission_outcomes_mission_metric_version",
                columnNames = {"room_mission_id", "metric_schema_version"}),
        indexes = @Index(name = "idx_mission_outcomes_closes_at", columnList = "closes_at")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MissionOutcomeEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_mission_id", nullable = false)
    private Long roomMissionId;

    @Column(name = "metric_schema_version", nullable = false, length = 32)
    private String metricSchemaVersion;

    @Column(name = "target_member_count", nullable = false)
    private int targetMemberCount;

    @Column(name = "impressed_member_count", nullable = false)
    private int impressedMemberCount;

    @Column(name = "activated_member_count", nullable = false)
    private int activatedMemberCount;

    @Column(name = "expected_channel_activated_count", nullable = false)
    private int expectedChannelActivatedCount;

    @Column(name = "cross_channel_activated_count", nullable = false)
    private int crossChannelActivatedCount;

    @Column(name = "expired_member_count", nullable = false)
    private int expiredMemberCount;

    @Column(name = "room_activation_coverage", nullable = false, precision = 5, scale = 4)
    private BigDecimal roomActivationCoverage;

    @Column(name = "exposure_reach", nullable = false, precision = 5, scale = 4)
    private BigDecimal exposureReach;

    @Column(name = "closes_at", nullable = false)
    private LocalDateTime closesAt;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    public static MissionOutcomeEntity from(MissionOutcome outcome) {
        MissionOutcomeEntity entity = new MissionOutcomeEntity();
        entity.id = outcome.id();
        entity.roomMissionId = outcome.roomMissionId();
        entity.metricSchemaVersion = outcome.metricSchemaVersion();
        entity.targetMemberCount = outcome.targetMemberCount();
        entity.impressedMemberCount = outcome.impressedMemberCount();
        entity.activatedMemberCount = outcome.activatedMemberCount();
        entity.expectedChannelActivatedCount = outcome.expectedChannelActivatedCount();
        entity.crossChannelActivatedCount = outcome.crossChannelActivatedCount();
        entity.expiredMemberCount = outcome.expiredMemberCount();
        entity.roomActivationCoverage = outcome.roomActivationCoverage();
        entity.exposureReach = outcome.exposureReach();
        entity.closesAt = outcome.closesAt();
        entity.calculatedAt = outcome.calculatedAt();
        entity.createdAt = outcome.createdAt();
        return entity;
    }

    public MissionOutcome toDomain() {
        return new MissionOutcome(
                id, roomMissionId, metricSchemaVersion,
                targetMemberCount, impressedMemberCount, activatedMemberCount,
                expectedChannelActivatedCount, crossChannelActivatedCount,
                expiredMemberCount,
                roomActivationCoverage, exposureReach,
                closesAt, calculatedAt, createdAt);
    }
}
