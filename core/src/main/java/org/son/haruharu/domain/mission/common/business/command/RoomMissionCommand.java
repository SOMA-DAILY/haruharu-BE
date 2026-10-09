package org.son.haruharu.domain.mission.common.business.command;

import java.time.LocalDateTime;
import org.son.haruharu.domain.mission.chat.business.command.MissionAnnouncementCommand;
import org.son.haruharu.domain.mission.common.implement.MissionDefinition;
import org.son.haruharu.domain.mission.common.implement.MissionType;

/** details는 유형이 소유한다. imageKey는 먼저 업로드한 사진의 키(선택, 유형 공통). announcement는 생성 시 선택적으로 등록하는 채팅 게시 계획이다. */
public record RoomMissionCommand(MissionType type, MissionDefinition details, String imageKey, LocalDateTime opensAt,
                                  LocalDateTime closesAt, MissionAnnouncementCommand announcement) {
    public RoomMissionCommand(MissionType type, MissionDefinition details, LocalDateTime opensAt,
                              LocalDateTime closesAt, MissionAnnouncementCommand announcement) {
        this(type, details, null, opensAt, closesAt, announcement);
    }
}
