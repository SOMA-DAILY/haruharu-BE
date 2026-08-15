package org.son.haruharu.domain.room.business.exception;

import org.son.haruharu.common.exception.BadRequestException;

/** 사용할 수 없는 초대코드 — 비활성·만료·정원 초과, 또는 종료/취소된 방(#312). */
public class InviteCodeNotUsableException extends BadRequestException {

    public InviteCodeNotUsableException() {
        super("INVITE_CODE_NOT_USABLE", "사용할 수 없는 초대코드입니다. (만료·비활성·정원 초과)");
    }
}
