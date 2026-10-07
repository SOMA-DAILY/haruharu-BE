package org.son.haruharu.domain.mission.chat.business.exception;
import org.son.haruharu.common.exception.ConflictException;
public class AnnouncementNotEditableException extends ConflictException {
    public AnnouncementNotEditableException() {
        super("MISSION_ANNOUNCEMENT_NOT_EDITABLE", "현재 상태에서는 안내를 변경할 수 없어요");
    }
}
