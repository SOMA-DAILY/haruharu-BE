package org.son.haruharu.domain.identity.business.exception;

import org.son.haruharu.common.exception.ForbiddenException;

public class UnderageNotAllowedException extends ForbiddenException {

    public UnderageNotAllowedException() {
        super("IDENTITY_UNDERAGE_BLOCKED", "만 19세 미만은 서비스를 이용할 수 없습니다.");
    }
}
