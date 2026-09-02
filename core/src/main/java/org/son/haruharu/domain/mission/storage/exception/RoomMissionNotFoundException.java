package org.son.haruharu.domain.mission.storage.exception;

import org.son.haruharu.common.exception.NotFoundException;

public class RoomMissionNotFoundException extends NotFoundException {

    public RoomMissionNotFoundException() {
        super("ROOM_MISSION_NOT_FOUND", "존재하지 않는 방 미션입니다.");
    }
}
