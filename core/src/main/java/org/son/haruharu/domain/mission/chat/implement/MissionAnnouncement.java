package org.son.haruharu.domain.mission.chat.implement;

import java.time.LocalDateTime;
import org.son.haruharu.domain.mission.common.implement.RoomMission;
import org.son.haruharu.domain.mission.common.implement.exception.InvalidMissionException;

public record MissionAnnouncement(
        Long id, Long roomMissionId, Long roomId, MissionAnnouncementType announcementType,
        LocalDateTime runAt, String content, boolean push, String pushTitle, String pushBody,
        MissionAnnouncementStatus status, String failReason, Long sentMessageId,
        Long legacyScheduleItemId, String createdBy, String updatedBy,
        LocalDateTime sentAt, LocalDateTime createdAt, LocalDateTime updatedAt
) {
    public MissionAnnouncement {
        if (roomMissionId == null || roomId == null || announcementType == null || status == null) {
            throw new InvalidMissionException("announcement", "미션과 안내의 연결 정보를 확인해 주세요");
        }
        if (runAt == null) throw new InvalidMissionException("run_at", "안내 시각을 지정해 주세요");
    }

    public static MissionAnnouncement scheduled(RoomMission mission, MissionAnnouncementType type,
                                                LocalDateTime runAt, String content, boolean push,
                                                String pushTitle, String pushBody, String actor) {
        return new MissionAnnouncement(null, mission.id(), mission.roomId(), type, runAt, content, push,
                pushTitle, pushBody, MissionAnnouncementStatus.SCHEDULED, null, null, null,
                actor, actor, null, null, null);
    }

    public boolean hasMessage() {
        return content != null && !content.isBlank();
    }
}
