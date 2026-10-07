package org.son.haruharu.domain.mission.types.announcement.implement;

import java.time.LocalDateTime;
import org.son.haruharu.domain.mission.common.implement.exception.InvalidMissionException;

/** RoomMission과 ID로 연결되는 안내형 데이터. 채팅 게시 계획(MissionAnnouncement)과 별개다. */
public record AnnouncementMission(Long roomMissionId, AnnouncementMissionDefinition definition,
                                  LocalDateTime createdAt, LocalDateTime updatedAt) {
    public AnnouncementMission {
        if (roomMissionId == null || roomMissionId <= 0)
            throw new InvalidMissionException("mission_id", "공통 미션이 필요해요");
        if (definition == null) throw new InvalidMissionException("details", "안내형 내용을 입력해 주세요");
        definition.validate();
    }

    public static AnnouncementMission create(Long missionId, AnnouncementMissionDefinition definition) {
        return new AnnouncementMission(missionId, definition, null, null);
    }

    public AnnouncementMission withDefinition(AnnouncementMissionDefinition definition) {
        return new AnnouncementMission(roomMissionId, definition, createdAt, updatedAt);
    }
}
