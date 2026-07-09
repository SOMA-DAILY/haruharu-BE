package org.son.haruharu.domain.user.business.exception;

import org.son.haruharu.common.exception.ForbiddenException;

public class InactiveUserException extends ForbiddenException {
    public InactiveUserException() {
        super("USER_INACTIVE", "비활성화된 계정입니다.");
    }
}
