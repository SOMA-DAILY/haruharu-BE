package org.son.haruharu.domain.mission.business.exception;

import org.son.haruharu.common.exception.NotFoundException;

public class MissionMemberStateNotFoundException extends NotFoundException {

    public MissionMemberStateNotFoundException() {
        super("MISSION_MEMBER_STATE_NOT_FOUND", "미션 대상 멤버 상태가 없습니다.");
    }
}
