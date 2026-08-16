package org.son.haruharu.domain.room.business.exception;

import org.son.haruharu.common.exception.BadRequestException;

/**
 * 방 시작 시각이 이미 지난 시각일 때(#320).
 * 과거로 만들면 모집(PENDING) 구간 없이 곧바로 진행 중인 방이 태어난다 — 초대코드를 배포하기도 전에.
 * 순서 규약 위반({@link RoomInvalidScheduleException})과 원인이 달라 코드를 나눈다(어드민 폼 안내용).
 */
public class RoomStartsAtInPastException extends BadRequestException {

    public RoomStartsAtInPastException() {
        super("ROOM_STARTS_AT_IN_PAST", "방 시작 시각은 현재 시각 이후여야 합니다.");
    }
}
