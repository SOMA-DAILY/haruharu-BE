package org.son.haruharu.domain.mission.implement.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class InvalidRoomMissionSlotScheduleException extends BadRequestException {

    public InvalidRoomMissionSlotScheduleException() {
        super("ROOM_MISSION_SLOT_INVALID_SCHEDULE", "미션 슬롯 일정이 올바르지 않습니다.");
    }
}
