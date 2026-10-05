package org.son.haruharu.domain.mission.implement.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class InvalidOperatorMissionScheduleException extends BadRequestException {
    public InvalidOperatorMissionScheduleException() {
        super("OPERATOR_MISSION_INVALID_SCHEDULE", "방 촬영 기간 안의 미래 시각을 선택해 주세요.");
    }
}
