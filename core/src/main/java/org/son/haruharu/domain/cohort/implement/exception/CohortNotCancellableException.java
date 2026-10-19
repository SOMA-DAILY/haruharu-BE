package org.son.haruharu.domain.cohort.implement.exception;

import org.son.haruharu.common.exception.ConflictException;

public class CohortNotCancellableException extends ConflictException {
    public CohortNotCancellableException() {
        super("COHORT_NOT_CANCELLABLE", "초안이거나 공개 중인 기수만 취소할 수 있습니다.");
    }
}
