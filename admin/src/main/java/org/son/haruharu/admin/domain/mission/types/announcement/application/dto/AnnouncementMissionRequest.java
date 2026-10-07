package org.son.haruharu.admin.domain.mission.types.announcement.application.dto;

import java.util.List;
import org.son.haruharu.admin.domain.mission.common.application.dto.MissionDetailsRequest;
import org.son.haruharu.domain.mission.types.announcement.implement.AnnouncementMissionDefinition;

public record AnnouncementMissionRequest(String content, List<String> revealFields) implements MissionDetailsRequest {
    @Override
    public AnnouncementMissionDefinition toDefinition() {
        return AnnouncementMissionDefinition.from(content, revealFields);
    }
}
