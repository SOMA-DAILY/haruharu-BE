package org.son.haruharu.admin.domain.mission.common.application.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import org.son.haruharu.admin.domain.mission.chat.application.dto.MissionAnnouncementRequest;
import org.son.haruharu.admin.domain.mission.types.announcement.application.dto.AnnouncementMissionRequest;
import org.son.haruharu.admin.domain.mission.types.profilereveal.application.dto.ProfileRevealMissionRequest;
import org.son.haruharu.admin.domain.mission.types.pairchat.application.dto.PairChatMissionRequest;
import org.son.haruharu.admin.domain.mission.types.recording.application.dto.RecordingMissionRequest;
import org.son.haruharu.domain.mission.common.business.command.RoomMissionCommand;
import org.son.haruharu.domain.mission.common.implement.MissionType;

public record RoomMissionRequest(
        MissionType type,
        @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "type")
        @JsonSubTypes({
                @JsonSubTypes.Type(value = AnnouncementMissionRequest.class, name = "ANNOUNCEMENT"),
                @JsonSubTypes.Type(value = ProfileRevealMissionRequest.class, name = "PROFILE_REVEAL"),
                @JsonSubTypes.Type(value = RecordingMissionRequest.class, name = "RECORDING"),
                @JsonSubTypes.Type(value = PairChatMissionRequest.class, name = "PAIR_CHAT")})
        MissionDetailsRequest details,
        @Schema(description = "먼저 업로드한 미션 사진의 key(선택, 유형 공통). 수정에서 비우면 사진을 뗀다.", nullable = true)
        String imageKey,
        LocalDateTime opensAt, LocalDateTime closesAt, MissionAnnouncementRequest announcement) {
    public RoomMissionCommand toCommand() {
        return new RoomMissionCommand(type, details == null ? null : details.toDefinition(), imageKey, opensAt, closesAt,
                announcement == null ? null : announcement.toCommand());
    }
}
