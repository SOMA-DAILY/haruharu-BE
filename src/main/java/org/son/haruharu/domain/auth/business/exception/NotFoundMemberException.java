package org.son.haruharu.domain.auth.business.exception;

import org.son.haruharu.common.exception.NotFoundException;

public class NotFoundMemberException extends NotFoundException {

    public NotFoundMemberException() {
        super("AUTH_404_NOT_FOUND", "가입되지 않은 회원입니다.");
    }
}
