package org.son.haruharu.domain.mission.common.business.exception;
import org.son.haruharu.common.exception.ConflictException;
public class MissionNotEditableException extends ConflictException {
    public MissionNotEditableException() {
        super("MISSION_NOT_EDITABLE", "시작되었거나 취소된 미션은 수정할 수 없어요");
    }
}
