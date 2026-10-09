package org.son.haruharu.domain.mission.common.implement;

import java.util.Map;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementType;

/** 유형별 정의의 검증과 details JSON 변환을 소유한다. 저장소를 참조하지 않는다. */
public interface MissionTypeHandler {
    MissionType type();
    void validate(MissionDefinition definition);
    /** room_mission_instances.details 에 저장할 JSON 객체. 키 이름이 저장 계약이다. */
    Map<String, Object> toStored(MissionDefinition definition);
    MissionDefinition fromStored(Map<String, Object> stored);
    boolean requiresResponse();
    default boolean allowsEmptyAnnouncement(MissionAnnouncementType purpose, MissionDefinition definition) { return false; }
}
