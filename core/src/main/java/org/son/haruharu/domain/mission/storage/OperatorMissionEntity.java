package org.son.haruharu.domain.mission.storage;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.mission.implement.OperatorMission;
import org.son.haruharu.domain.mission.implement.OperatorMissionStatus;
import java.time.LocalDateTime;

@Entity
@Table(name = "operator_missions", uniqueConstraints = @UniqueConstraint(name = "uq_operator_mission_request", columnNames = {"room_id", "request_key"}),
        indexes = {@Index(name = "idx_operator_mission_due", columnList = "status, scheduled_at"),
                @Index(name = "idx_operator_mission_history", columnList = "room_id, id")})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OperatorMissionEntity extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long roomId;
    @Column(nullable = false, length = 20) private String templateId;
    @Column(nullable = false, length = 500) private String content;
    @Column(nullable = false) private boolean notifyParticipants;
    @Column(nullable = false) private LocalDateTime scheduledAt;
    @Column(nullable = false) private boolean immediate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private OperatorMissionStatus status;
    @Column(nullable = false) private Long createdBy;
    @Column(nullable = false, length = 80) private String requestKey;
    private Long imageId;
    private Long messageId;
    private LocalDateTime sentAt;
    @Column(length = 200) private String failReason;
    private LocalDateTime deleteRequestedAt;
    private LocalDateTime deleteProcessedAt;

    public static OperatorMissionEntity from(OperatorMission m) {
        OperatorMissionEntity e = new OperatorMissionEntity();
        e.id = m.id(); e.roomId = m.roomId(); e.templateId = m.templateId(); e.content = m.content();
        e.notifyParticipants = m.notifyParticipants(); e.scheduledAt = m.scheduledAt(); e.immediate = m.immediate();
        e.status = m.status(); e.createdBy = m.createdBy(); e.requestKey = m.requestKey(); e.messageId = m.messageId();
        e.imageId = m.imageId(); e.sentAt = m.sentAt(); e.failReason = m.failReason(); e.deleteRequestedAt = m.deleteRequestedAt();
        e.deleteProcessedAt = m.deleteProcessedAt(); e.createdAt = m.createdAt();
        return e;
    }

    public OperatorMission toDomain() {
        return new OperatorMission(id, roomId, templateId, content, notifyParticipants, scheduledAt, immediate,
                status, createdBy, requestKey, messageId, sentAt, failReason, deleteRequestedAt, deleteProcessedAt, createdAt, imageId);
    }
}
