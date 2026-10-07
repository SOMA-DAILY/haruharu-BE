package org.son.haruharu.domain.mission.common.implement;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncement;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementType;
import org.son.haruharu.domain.mission.chat.implement.MissionNotification;

/** 유형별 데이터·검증·저장·조회와 안내 후 알림을 소유한다. 호출 서비스의 트랜잭션을 사용한다. */
public interface MissionTypeHandler {
    MissionType type();
    void validate(MissionDefinition definition);
    void saveDefinition(Long missionId, MissionDefinition definition);
    Map<Long, MissionDefinition> loadDefinitions(Collection<Long> missionIds);
    boolean requiresResponse();
    default boolean allowsEmptyAnnouncement(MissionAnnouncementType purpose, MissionDefinition definition) { return false; }
    default List<MissionNotification> notificationsAfterAnnouncement(RoomMission mission, MissionAnnouncement announcement) {
        return List.of();
    }
}
