package org.son.haruharu.domain.room.business.exception;

import org.son.haruharu.common.exception.NotFoundException;

/** 존재하지 않는 초대코드(#312). */
public class InviteCodeNotFoundException extends NotFoundException {

    public InviteCodeNotFoundException() {
        super("INVITE_CODE_NOT_FOUND", "유효하지 않은 초대코드입니다.");
    }
}
