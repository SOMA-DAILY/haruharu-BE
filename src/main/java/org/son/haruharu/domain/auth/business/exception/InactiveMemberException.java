package org.son.haruharu.domain.auth.business.exception;

import org.son.haruharu.common.exception.ForbiddenException;

public class InactiveMemberException extends ForbiddenException {

    public InactiveMemberException() {
        super("AUTH_403_INACTIVE_MEMBER", "비활성화된 계정입니다.");
    }
}
