package org.son.haruharu.domain.mission.implement.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class InvalidOperatorMissionContentException extends BadRequestException {
    public InvalidOperatorMissionContentException() {
        super("OPERATOR_MISSION_INVALID_CONTENT", "기본 미션을 선택하고 1~500자의 문구를 입력해 주세요.");
    }
}
