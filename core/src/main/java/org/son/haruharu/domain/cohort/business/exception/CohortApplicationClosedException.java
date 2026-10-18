package org.son.haruharu.domain.cohort.business.exception;

import org.son.haruharu.common.exception.ConflictException;

public class CohortApplicationClosedException extends ConflictException {
    public CohortApplicationClosedException() {
        super("COHORT_APPLICATION_CLOSED", "이미 끝난 신청입니다.");
    }
}
