package org.son.haruharu.domain.cohort.business.exception;

import org.son.haruharu.common.exception.ConflictException;

public class CohortApplicationNotCancellableException extends ConflictException {
    public CohortApplicationNotCancellableException() {
        super("COHORT_APPLICATION_NOT_CANCELLABLE", "지금은 이 신청을 취소할 수 없습니다.");
    }
}
