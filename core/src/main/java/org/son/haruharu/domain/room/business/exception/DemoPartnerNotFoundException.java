package org.son.haruharu.domain.room.business.exception;

import org.son.haruharu.common.exception.NotFoundException;

/**
 * 데모 방에 성사시킬 상대(가상 참여자)가 없을 때(#266).
 * 정상 데모 방은 본인+가상 5명이라 발생하지 않지만, 데이터가 어긋난 경우를 500이 아닌 404로 드러낸다.
 */
public class DemoPartnerNotFoundException extends NotFoundException {

    public DemoPartnerNotFoundException() {
        super("ROOM_404_DEMO_PARTNER_NOT_FOUND", "데모 방에 매칭할 상대가 없습니다.");
    }
}
