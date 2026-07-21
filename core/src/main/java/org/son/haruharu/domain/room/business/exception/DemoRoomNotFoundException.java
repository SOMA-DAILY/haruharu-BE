package org.son.haruharu.domain.room.business.exception;

import org.son.haruharu.common.exception.NotFoundException;

/** 데모 방이 없는데 advance-day 등을 호출한 경우. 먼저 데모 방을 생성해야 한다. */
public class DemoRoomNotFoundException extends NotFoundException {
    public DemoRoomNotFoundException() {
        super("DEMO_ROOM_NOT_FOUND", "데모 방이 없습니다. 먼저 방을 생성하세요.");
    }
}
