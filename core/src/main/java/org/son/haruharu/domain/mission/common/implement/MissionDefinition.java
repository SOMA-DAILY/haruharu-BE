package org.son.haruharu.domain.mission.common.implement;

/** 각 유형의 개설 데이터. 공통 미션은 구체적인 필드를 알지 않는다. */
public interface MissionDefinition {
    MissionType type();
}
