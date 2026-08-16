package org.son.haruharu.domain.room.implement.exception;

import org.son.haruharu.common.exception.BadRequestException;

/**
 * 방 멤버 성별로 허용되지 않은 값(#318).
 * 최종선택의 "반대 성별" 판정이 이 스냅샷을 그대로 쓰므로 MALE·FEMALE 외의 값은 도메인이 거부한다.
 */
public class InvalidRoomMemberGenderException extends BadRequestException {

    public InvalidRoomMemberGenderException() {
        super("ROOM_MEMBER_INVALID_GENDER", "성별은 MALE 또는 FEMALE 만 지정할 수 있습니다.");
    }
}
