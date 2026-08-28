package org.son.haruharu.domain.mission.implement.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class InvalidMissionRevisionException extends BadRequestException {

    public InvalidMissionRevisionException() {
        super("MISSION_REVISION_INVALID", "미션 revision 메타데이터가 올바르지 않습니다.");
    }
}
