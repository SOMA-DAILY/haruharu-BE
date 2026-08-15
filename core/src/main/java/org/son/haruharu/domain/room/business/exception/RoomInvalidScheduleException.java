package org.son.haruharu.domain.room.business.exception;

import org.son.haruharu.common.exception.BadRequestException;

/**
 * 방 생성 시 스케줄 시각이 누락되거나 순서가 어긋났을 때(#311).
 * 코드·메시지는 생성자에서 고정한다(exception-handling 규칙).
 */
public class RoomInvalidScheduleException extends BadRequestException {

    public RoomInvalidScheduleException() {
        super("ROOM_INVALID_SCHEDULE",
                "방 스케줄 시각이 올바르지 않습니다. 순서: 시작 < 선택오픈 < 선택마감 ≤ 발표 ≤ 종료, totalDays ≥ 1.");
    }
}
