package org.son.haruharu.domain.mission.common.implement.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class InvalidMissionImageException extends BadRequestException {
    public InvalidMissionImageException() {
        super("INVALID_MISSION_IMAGE", "JPG·PNG 사진(10MB 이하)을 선택해 주세요.");
    }
}
