package org.son.haruharu.domain.mission.chat.storage.exception;

import org.son.haruharu.common.exception.NotFoundException;

public class MissionAnnouncementNotFoundException extends NotFoundException {
    public MissionAnnouncementNotFoundException() {
        super("MISSION_ANNOUNCEMENT_NOT_FOUND", "미션 안내를 찾을 수 없습니다.");
    }
}
