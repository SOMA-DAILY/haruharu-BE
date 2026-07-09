package org.son.haruharu.domain.matching.business.exception;

import org.son.haruharu.common.exception.ConflictException;

public class MatchingRoomInProgressException extends ConflictException {

    public MatchingRoomInProgressException() {
        super("MATCHING_ROOM_IN_PROGRESS", "진행 중인 소개팅방이 있어 매칭에 참가할 수 없습니다.");
    }
}
