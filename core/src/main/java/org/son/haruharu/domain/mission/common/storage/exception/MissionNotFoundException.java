package org.son.haruharu.domain.mission.common.storage.exception;

import org.son.haruharu.common.exception.NotFoundException;

public class MissionNotFoundException extends NotFoundException {
    public MissionNotFoundException() {
        super("MISSION_NOT_FOUND", "미션을 찾을 수 없습니다.");
    }
}
