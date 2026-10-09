package org.son.haruharu.admin.domain.mission.common.application.dto;

import org.son.haruharu.domain.mission.common.implement.MissionDefinition;


/** 외부 타입 이름은 RoomMissionRequest의 명시적 허용 목록으로만 해석한다. */
public interface MissionDetailsRequest {
    MissionDefinition toDefinition();
}
