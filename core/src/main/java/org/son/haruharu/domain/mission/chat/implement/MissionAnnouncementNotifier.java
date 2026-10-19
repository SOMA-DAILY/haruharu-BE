package org.son.haruharu.domain.mission.chat.implement;

import java.util.List;
import org.son.haruharu.domain.mission.common.implement.MissionType;
import org.son.haruharu.domain.mission.common.implement.RoomMission;

/** 안내가 나간 뒤 유형이 추가로 보낼 알림. 필요한 유형만 구현하고, 실행기가 발송 트랜잭션 안에서 부른다. */
public interface MissionAnnouncementNotifier {
    MissionType type();
    List<MissionNotification> afterAnnouncement(RoomMission mission, MissionAnnouncement announcement);
}
