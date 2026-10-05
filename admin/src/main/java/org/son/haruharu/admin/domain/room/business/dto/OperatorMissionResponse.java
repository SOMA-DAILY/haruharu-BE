package org.son.haruharu.admin.domain.room.business.dto;
import org.son.haruharu.domain.mission.implement.OperatorMission;
import org.son.haruharu.domain.mission.business.dto.MissionImageResponse;
public record OperatorMissionResponse(Long id, Long roomId, String templateId, String content, boolean notifyParticipants, java.time.LocalDateTime scheduledAt, boolean immediate, org.son.haruharu.domain.mission.implement.OperatorMissionStatus status, Long createdBy, String requestKey, Long messageId, java.time.LocalDateTime sentAt, String failReason, java.time.LocalDateTime deleteRequestedAt, java.time.LocalDateTime deleteProcessedAt, java.time.LocalDateTime createdAt, Long imageId, MissionImageResponse image) {
    public static OperatorMissionResponse from(OperatorMission m, MissionImageResponse image) {
        return new OperatorMissionResponse(m.id(),m.roomId(),m.templateId(),m.content(),m.notifyParticipants(),m.scheduledAt(),m.immediate(),m.status(),m.createdBy(),m.requestKey(),m.messageId(),m.sentAt(),m.failReason(),m.deleteRequestedAt(),m.deleteProcessedAt(),m.createdAt(),m.imageId(),image);
    }
}
