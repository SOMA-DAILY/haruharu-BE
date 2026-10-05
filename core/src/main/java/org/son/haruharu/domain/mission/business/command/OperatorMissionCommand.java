package org.son.haruharu.domain.mission.business.command;

import java.time.LocalDateTime;

public record OperatorMissionCommand(String requestKey, String templateId, String content,
                                     boolean notifyParticipants, LocalDateTime scheduledAt, Long imageId) {
    public OperatorMissionCommand(String requestKey,String templateId,String content,boolean notifyParticipants,LocalDateTime scheduledAt) {
        this(requestKey,templateId,content,notifyParticipants,scheduledAt,null);
    }
}
