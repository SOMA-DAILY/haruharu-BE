package org.son.haruharu.admin.domain.room.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import org.son.haruharu.domain.mission.business.command.OperatorMissionCommand;

public record OperatorMissionRequest(@NotBlank @Size(max = 80) String requestKey,
                                     @NotBlank String templateId, @NotBlank @Size(max = 500) String content,
                                     boolean notifyParticipants, LocalDateTime scheduledAt, @jakarta.validation.constraints.Positive Long imageId) {
    public OperatorMissionCommand toCommand() {
        return new OperatorMissionCommand(requestKey, templateId, content, notifyParticipants, scheduledAt, imageId);
    }
}
