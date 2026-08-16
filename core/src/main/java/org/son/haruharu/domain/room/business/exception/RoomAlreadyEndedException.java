package org.son.haruharu.domain.room.business.exception;

import org.son.haruharu.common.exception.BadRequestException;

/**
 * 이미 종료된 방에 운영 조작(취소 등)을 시도했을 때(#320).
 * ENDED 는 읽기전용 아카이브라 되돌리지 않는다 — 종료된 세션의 최종선택 결과가 뒤집히면 안 된다.
 */
public class RoomAlreadyEndedException extends BadRequestException {

    public RoomAlreadyEndedException() {
        super("ROOM_ALREADY_ENDED", "이미 종료된 방은 변경할 수 없습니다.");
    }
}
