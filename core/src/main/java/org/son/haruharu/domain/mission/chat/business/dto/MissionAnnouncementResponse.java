package org.son.haruharu.domain.mission.chat.business.dto;

import java.time.LocalDateTime;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncement;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementStatus;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementType;

public record MissionAnnouncementResponse(Long announcementId, Long missionId, Long roomId,
        MissionAnnouncementType announcementType, LocalDateTime runAt, String content, boolean push,
        String pushTitle, String pushBody, MissionAnnouncementStatus status, String failReason,
        Long sentMessageId, LocalDateTime messageDeletedAt, Long legacyScheduleItemId,
        String createdBy, String updatedBy, LocalDateTime sentAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static MissionAnnouncementResponse of(MissionAnnouncement a, LocalDateTime deletedAt) {
        return new MissionAnnouncementResponse(a.id(), a.roomMissionId(), a.roomId(), a.announcementType(),
                a.runAt(), a.content(), a.push(), a.pushTitle(), a.pushBody(), a.status(), a.failReason(),
                a.sentMessageId(), deletedAt, a.legacyScheduleItemId(), a.createdBy(), a.updatedBy(),
                a.sentAt(), a.createdAt(), a.updatedAt());
    }
}
