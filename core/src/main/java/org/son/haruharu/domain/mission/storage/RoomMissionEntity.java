package org.son.haruharu.domain.mission.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.mission.implement.MissionAssignmentContextSnapshot;
import org.son.haruharu.domain.mission.implement.MissionCandidateSnapshot;
import org.son.haruharu.domain.mission.implement.MissionAssignmentRelaxationLevel;
import org.son.haruharu.domain.mission.implement.RoomMission;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 방별 미션 인스턴스. (room_id, sequence) 유니크로 방 내 순번 중복을 막고,
 * room_mission_slot_id 유니크로 동일 슬롯의 중복 배정을 막는다.
 */
@Entity
@Table(
        name = "room_missions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_room_missions_room_seq", columnNames = {"room_id", "sequence"}),
                @UniqueConstraint(name = "uq_room_missions_slot", columnNames = "room_mission_slot_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomMissionEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "room_mission_slot_id", nullable = false)
    private Long roomMissionSlotId;

    @Column(name = "mission_revision_id", nullable = false)
    private Long missionRevisionId;

    @Column(name = "opens_at", nullable = false)
    private LocalDateTime opensAt;

    /** null이면 무기한 열림. */
    @Column(name = "closes_at")
    private LocalDateTime closesAt;

    @Column(name = "sequence", nullable = false)
    private int sequence;

    @Column(name = "policy_version", nullable = false, length = 40)
    private String policyVersion;

    @Column(name = "catalog_version", nullable = false, length = 64)
    private String catalogVersion;

    @Column(name = "context_schema_version", nullable = false, length = 40)
    private String contextSchemaVersion;

    @Column(name = "context_as_of", nullable = false)
    private LocalDateTime contextAsOf;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context_snapshot", nullable = false)
    private MissionAssignmentContextSnapshot contextSnapshot;

    @Column(name = "candidate_schema_version", nullable = false, length = 40)
    private String candidateSchemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "candidate_snapshot", nullable = false)
    private MissionCandidateSnapshot candidateSnapshot;

    @Column(name = "selected_probability", nullable = false, precision = 14, scale = 12)
    private BigDecimal selectedProbability;

    @Column(name = "exploration_id", nullable = false)
    private UUID explorationId;

    @Column(name = "rng_seed", nullable = false)
    private long rngSeed;

    @Column(name = "rng_algorithm", nullable = false, length = 40)
    private String rngAlgorithm;

    @Enumerated(EnumType.STRING)
    @Column(name = "relaxation_level", nullable = false, length = 24)
    private MissionAssignmentRelaxationLevel relaxationLevel;

    @Column(name = "application_commit_sha", nullable = false, length = 64)
    private String applicationCommitSha;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    @Column(name = "assignment_delay_seconds", nullable = false)
    private long assignmentDelaySeconds;

    public static RoomMissionEntity from(RoomMission roomMission) {
        RoomMissionEntity entity = new RoomMissionEntity();
        entity.id = roomMission.id();
        entity.roomId = roomMission.roomId();
        entity.roomMissionSlotId = roomMission.roomMissionSlotId();
        entity.missionRevisionId = roomMission.missionRevisionId();
        entity.opensAt = roomMission.opensAt();
        entity.closesAt = roomMission.closesAt();
        entity.sequence = roomMission.sequence();
        entity.policyVersion = roomMission.policyVersion();
        entity.catalogVersion = roomMission.catalogVersion();
        entity.contextSchemaVersion = roomMission.contextSchemaVersion();
        entity.contextAsOf = roomMission.contextAsOf();
        entity.contextSnapshot = roomMission.contextSnapshot();
        entity.candidateSchemaVersion = roomMission.candidateSchemaVersion();
        entity.candidateSnapshot = roomMission.candidateSnapshot();
        entity.selectedProbability = roomMission.selectedProbability();
        entity.explorationId = roomMission.explorationId();
        entity.rngSeed = roomMission.rngSeed();
        entity.rngAlgorithm = roomMission.rngAlgorithm();
        entity.relaxationLevel = roomMission.relaxationLevel();
        entity.applicationCommitSha = roomMission.applicationCommitSha();
        entity.assignedAt = roomMission.assignedAt();
        entity.assignmentDelaySeconds = roomMission.assignmentDelaySeconds();
        entity.createdAt = roomMission.createdAt();
        return entity;
    }

    public RoomMission toDomain() {
        return new RoomMission(
                id, roomId, roomMissionSlotId, missionRevisionId, opensAt, closesAt, sequence,
                policyVersion, catalogVersion, contextSchemaVersion, contextAsOf, contextSnapshot,
                candidateSchemaVersion, candidateSnapshot, selectedProbability, explorationId,
                rngSeed, rngAlgorithm, relaxationLevel, applicationCommitSha, assignedAt,
                assignmentDelaySeconds, createdAt);
    }
}
