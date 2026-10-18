package org.son.haruharu.domain.cohort.business.exception;

import org.son.haruharu.common.exception.ConflictException;

public class CohortApplicationInProgressException extends ConflictException {
    public CohortApplicationInProgressException() {
        super("COHORT_APPLICATION_IN_PROGRESS", "이미 진행 중인 신청이 있습니다.");
    }
}
