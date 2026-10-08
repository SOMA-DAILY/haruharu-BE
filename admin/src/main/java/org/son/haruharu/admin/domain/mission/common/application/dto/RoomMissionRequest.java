package org.son.haruharu.admin.domain.mission.common.application.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.LocalDateTime;
import org.son.haruharu.admin.domain.mission.chat.application.dto.MissionAnnouncementRequest;
import org.son.haruharu.admin.domain.mission.types.announcement.application.dto.AnnouncementMissionRequest;
import org.son.haruharu.admin.domain.mission.types.profilereveal.application.dto.ProfileRevealMissionRequest;
import org.son.haruharu.domain.mission.common.business.command.RoomMissionCommand;
import org.son.haruharu.domain.mission.common.implement.MissionType;

public record RoomMissionRequest(
        MissionType type,
        @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "type")
        @JsonSubTypes({
                @JsonSubTypes.Type(value = AnnouncementMissionRequest.class, name = "ANNOUNCEMENT"),
                @JsonSubTypes.Type(value = ProfileRevealMissionRequest.class, name = "PROFILE_REVEAL")})
        MissionDetailsRequest details,
        LocalDateTime opensAt, LocalDateTime closesAt, MissionAnnouncementRequest announcement) {
    public RoomMissionCommand toCommand() {
        return new RoomMissionCommand(type, details == null ? null : details.toDefinition(), opensAt, closesAt,
                announcement == null ? null : announcement.toCommand());
    }
}
