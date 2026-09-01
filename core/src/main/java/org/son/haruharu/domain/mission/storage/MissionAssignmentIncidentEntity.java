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
import org.son.haruharu.domain.mission.implement.MissionAssignmentIncident;
import org.son.haruharu.domain.mission.implement.MissionAssignmentIncidentReason;
import org.son.haruharu.domain.mission.implement.MissionAssignmentIncidentStatus;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "mission_assignment_incidents",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_mission_assignment_incidents_slot", columnNames = "room_mission_slot_id"),
        indexes = @Index(
                name = "idx_mission_assignment_incidents_status_last_failed",
                columnList = "status,last_failed_at")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MissionAssignmentIncidentEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_mission_slot_id", nullable = false)
    private Long roomMissionSlotId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MissionAssignmentIncidentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 48)
    private MissionAssignmentIncidentReason reason;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "first_failed_at", nullable = false)
    private LocalDateTime firstFailedAt;

    @Column(name = "last_failed_at", nullable = false)
    private LocalDateTime lastFailedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "last_error_summary", nullable = false, length = 120)
    private String lastErrorSummary;

    public static MissionAssignmentIncidentEntity from(MissionAssignmentIncident incident) {
        MissionAssignmentIncidentEntity entity = new MissionAssignmentIncidentEntity();
        entity.id = incident.id();
        entity.roomMissionSlotId = incident.roomMissionSlotId();
        entity.status = incident.status();
        entity.reason = incident.reason();
        entity.attemptCount = incident.attemptCount();
        entity.firstFailedAt = incident.firstFailedAt();
        entity.lastFailedAt = incident.lastFailedAt();
        entity.resolvedAt = incident.resolvedAt();
        entity.lastErrorSummary = incident.lastErrorSummary();
        entity.createdAt = incident.createdAt();
        return entity;
    }

    public MissionAssignmentIncident toDomain() {
        return new MissionAssignmentIncident(
                id, roomMissionSlotId, status, reason, attemptCount,
                firstFailedAt, lastFailedAt, resolvedAt, lastErrorSummary, createdAt);
    }
}
