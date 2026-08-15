package org.son.haruharu.domain.room.business.exception;

import org.son.haruharu.common.exception.ConflictException;

/** 이미 입장한 방에 다시 입장 시도(#312). */
public class AlreadyJoinedRoomException extends ConflictException {

    public AlreadyJoinedRoomException() {
        super("ROOM_ALREADY_JOINED", "이미 참여 중인 소개팅방이 있습니다.");
    }
}
