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
    /** 마감(closes_at)을 반드시 지정해야 하는 유형. 기본은 null 이면 방 종료를 따른다. */
    default boolean requiresExplicitClose() { return false; }
    /** 같은 방의 취소되지 않은 같은 유형 미션과 시간이 겹치면 안 되는 유형. 공통 서비스가 저장 전에 확인한다. */
    default boolean exclusiveSchedule() { return false; }
}
