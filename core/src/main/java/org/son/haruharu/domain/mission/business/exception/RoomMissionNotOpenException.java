package org.son.haruharu.domain.mission.business.exception;

import org.son.haruharu.common.exception.ConflictException;

public class RoomMissionNotOpenException extends ConflictException {

    public RoomMissionNotOpenException() {
        super("ROOM_MISSION_NOT_OPEN", "현재 열려 있는 방 미션이 아닙니다.");
    }
}
