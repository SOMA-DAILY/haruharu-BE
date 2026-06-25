package org.son.haruharu.domain.matching.business.exception;

import org.son.haruharu.common.exception.ForbiddenException;

public class MatchingNotVerifiedException extends ForbiddenException {

    public MatchingNotVerifiedException() {
        super("MATCHING_403_NOT_VERIFIED", "본인인증 후 매칭에 참가할 수 있습니다.");
    }
}
