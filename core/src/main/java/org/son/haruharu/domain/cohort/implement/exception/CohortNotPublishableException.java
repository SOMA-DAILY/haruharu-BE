package org.son.haruharu.domain.cohort.implement.exception;

import org.son.haruharu.common.exception.ConflictException;

public class CohortNotPublishableException extends ConflictException {
    public CohortNotPublishableException() {
        super("COHORT_NOT_PUBLISHABLE", "초안 상태의 기수만 공개할 수 있습니다.");
    }
}
