package org.son.haruharu.domain.mission.storage.exception;

import org.son.haruharu.common.exception.NotFoundException;

public class MissionNotFoundException extends NotFoundException {

    public MissionNotFoundException() {
        super("MISSION_404_NOT_FOUND", "존재하지 않는 미션입니다.");
    }
}
