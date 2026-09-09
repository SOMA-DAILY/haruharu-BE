package org.son.haruharu.domain.room.business.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class CannotSelectBlockedException extends BadRequestException {

    public CannotSelectBlockedException() {
        super("CANNOT_SELECT_BLOCKED", "차단 관계인 상대는 선택할 수 없습니다.");
    }
}
