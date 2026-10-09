package org.son.haruharu.admin.domain.mission.chat.application.dto;

import java.time.LocalDateTime;
import org.son.haruharu.domain.mission.chat.business.command.MissionAnnouncementCommand;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementType;
public record MissionAnnouncementRequest(MissionAnnouncementType announcementType, LocalDateTime runAt,
        String content, Boolean push, String pushTitle, String pushBody) {
    public MissionAnnouncementCommand toCommand() {
        return new MissionAnnouncementCommand(announcementType, runAt, content, push, pushTitle, pushBody);
    }
}
