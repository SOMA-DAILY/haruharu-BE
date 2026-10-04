package org.son.haruharu.domain.mission.storage.exception;

import org.son.haruharu.common.exception.NotFoundException;

public class OperatorMissionNotFoundException extends NotFoundException {
    public OperatorMissionNotFoundException() {
        super("OPERATOR_MISSION_NOT_FOUND", "미션 발송 이력을 찾을 수 없습니다.");
    }
}
