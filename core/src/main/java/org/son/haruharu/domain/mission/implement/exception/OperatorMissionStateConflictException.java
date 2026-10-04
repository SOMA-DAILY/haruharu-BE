package org.son.haruharu.domain.mission.implement.exception;

import org.son.haruharu.common.exception.ConflictException;

public class OperatorMissionStateConflictException extends ConflictException {
    public OperatorMissionStateConflictException() {
        super("OPERATOR_MISSION_STATE_CONFLICT", "미션 상태가 변경됐어요. 이력을 새로 확인해 주세요.");
    }
}
