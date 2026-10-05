package org.son.haruharu.domain.mission.implement;

import java.time.LocalDateTime;
import java.util.Objects;
import org.son.haruharu.domain.mission.business.command.OperatorMissionCommand;
import org.son.haruharu.domain.mission.implement.exception.InvalidOperatorMissionContentException;
import org.son.haruharu.domain.mission.implement.exception.OperatorMissionStateConflictException;

public record OperatorMission(Long id, Long roomId, String templateId, String content,
                              boolean notifyParticipants, LocalDateTime scheduledAt, boolean immediate,
                              OperatorMissionStatus status, Long createdBy, String requestKey,
                              Long messageId, LocalDateTime sentAt, String failReason,
                              LocalDateTime deleteRequestedAt, LocalDateTime deleteProcessedAt, LocalDateTime createdAt, Long imageId) {
    public OperatorMission(Long id, Long roomId, String templateId, String content, boolean notifyParticipants, LocalDateTime scheduledAt, boolean immediate, OperatorMissionStatus status, Long createdBy, String requestKey, Long messageId, LocalDateTime sentAt, String failReason, LocalDateTime deleteRequestedAt, LocalDateTime deleteProcessedAt, LocalDateTime createdAt) {
        this(id,roomId,templateId,content,notifyParticipants,scheduledAt,immediate,status,createdBy,requestKey,messageId,sentAt,failReason,deleteRequestedAt,deleteProcessedAt,createdAt,null);
    }
    public static OperatorMission create(Long roomId, Long adminId, OperatorMissionCommand command, LocalDateTime now) {
        validateContent(command);
        return new OperatorMission(null, roomId, command.templateId(), command.content().strip(),
                command.notifyParticipants(), command.scheduledAt() == null ? now : command.scheduledAt(),
                command.scheduledAt() == null, command.scheduledAt() == null ? OperatorMissionStatus.SENDING : OperatorMissionStatus.SCHEDULED,
                adminId, command.requestKey(), null, null, null, null, null, now, command.imageId());
    }

    private static void validateContent(OperatorMissionCommand command) {
        OperatorMissionTemplate.get(command.templateId());
        if (command.content() == null || command.content().isBlank() || command.content().strip().length() > 500
                || command.requestKey() == null || command.requestKey().isBlank() || command.requestKey().length() > 80) {
            throw new InvalidOperatorMissionContentException();
        }
    }

    public boolean matches(OperatorMissionCommand command) {
        return Objects.equals(templateId, command.templateId())
                && Objects.equals(content, command.content() == null ? null : command.content().strip())
                && Objects.equals(imageId, command.imageId())
                && notifyParticipants == command.notifyParticipants()
                && (immediate ? command.scheduledAt() == null : Objects.equals(scheduledAt, command.scheduledAt()));
    }

    public boolean dueAt(LocalDateTime now) {
        return (status == OperatorMissionStatus.SCHEDULED || status == OperatorMissionStatus.SENDING)
                && !scheduledAt.isAfter(now);
    }

    public OperatorMission edited(OperatorMissionCommand command) {
        requireScheduled();
        validateContent(command);
        if (command.scheduledAt() == null) throw new OperatorMissionStateConflictException();
        return new OperatorMission(id, roomId, command.templateId(), command.content().strip(), command.notifyParticipants(),
                command.scheduledAt(), false, status, createdBy, requestKey, messageId, sentAt, failReason,
                deleteRequestedAt, deleteProcessedAt, createdAt, command.imageId());
    }

    public OperatorMission cancelled() {
        requireScheduled();
        return state(OperatorMissionStatus.CANCELLED, messageId, sentAt, failReason, deleteRequestedAt, deleteProcessedAt);
    }

    private void requireScheduled() {
        if (status != OperatorMissionStatus.SCHEDULED) throw new OperatorMissionStateConflictException();
    }

    public OperatorMission sent(Long savedMessageId, LocalDateTime now) {
        return state(OperatorMissionStatus.SENT, savedMessageId, now, null, null, null);
    }

    public OperatorMission failed(String reason) {
        return state(OperatorMissionStatus.FAILED, messageId, sentAt, reason, deleteRequestedAt, deleteProcessedAt);
    }

    public OperatorMission requestDelete(LocalDateTime now) {
        if (status != OperatorMissionStatus.SENT) throw new OperatorMissionStateConflictException();
        return state(status, messageId, sentAt, failReason, deleteRequestedAt == null ? now : deleteRequestedAt, deleteProcessedAt);
    }

    public OperatorMission deleteProcessed(LocalDateTime now) {
        return state(status, messageId, sentAt, failReason, deleteRequestedAt, now);
    }

    private OperatorMission state(OperatorMissionStatus next, Long message, LocalDateTime sent,
                                  String reason, LocalDateTime requested, LocalDateTime processed) {
        return new OperatorMission(id, roomId, templateId, content, notifyParticipants, scheduledAt, immediate,
                next, createdBy, requestKey, message, sent, reason, requested, processed, createdAt, imageId);
    }
}
