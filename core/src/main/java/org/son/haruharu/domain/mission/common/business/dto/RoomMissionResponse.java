package org.son.haruharu.domain.mission.common.business.dto;

import java.time.LocalDateTime;
import org.son.haruharu.domain.mission.common.implement.MissionDefinition;
import org.son.haruharu.domain.mission.common.implement.MissionStatus;
import org.son.haruharu.domain.mission.common.implement.MissionType;
import org.son.haruharu.domain.mission.common.implement.MissionTypeHandler;
import org.son.haruharu.domain.mission.common.implement.RoomMission;
import org.son.haruharu.domain.room.implement.Room;

/** 공통 생명주기와 유형별 details, 선택 사진(image, 없으면 null). 개인 응답과 운영 감사 정보는 포함하지 않는다. */
public record RoomMissionResponse(Long missionId, Long roomId, MissionType type, MissionDefinition details,
        MissionImageResponse image,
        LocalDateTime opensAt, LocalDateTime closesAt, LocalDateTime effectiveClosesAt,
        MissionStatus status, String interactionType, boolean requiresResponse, boolean canRespond) {
    public static RoomMissionResponse of(RoomMission mission, Room room, LocalDateTime now, MissionTypeHandler handler,
                                          String imageUrl) {
        return new RoomMissionResponse(mission.id(), mission.roomId(), mission.type(), mission.details(),
                MissionImageResponse.of(mission.image(), imageUrl),
                mission.opensAt(), mission.closesAt(), mission.effectiveClosesAt(room), mission.statusAt(now, room),
                mission.type().name(), handler.requiresResponse(),
                handler.requiresResponse() && mission.statusAt(now, room) == MissionStatus.OPEN);
    }
}
