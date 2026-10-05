package org.son.haruharu.domain.mission.implement.exception;
import org.son.haruharu.common.exception.BadRequestException;
public class InvalidMissionImageException extends BadRequestException {
    public InvalidMissionImageException() { super("INVALID_MISSION_IMAGE", "이 방에 업로드한 JPG·PNG 사진(10MB 이하)을 선택해 주세요."); }
}
