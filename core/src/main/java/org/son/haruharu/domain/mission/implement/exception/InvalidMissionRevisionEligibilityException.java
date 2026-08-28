package org.son.haruharu.domain.mission.implement.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class InvalidMissionRevisionEligibilityException extends BadRequestException {

    public InvalidMissionRevisionEligibilityException() {
        super("MISSION_REVISION_ELIGIBILITY_INVALID", "미션 revision eligibility가 올바르지 않습니다.");
    }
}
