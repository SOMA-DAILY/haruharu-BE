package org.son.haruharu.domain.cohort.business.exception;

import org.son.haruharu.common.exception.ConflictException;

public class CohortNotAcceptingApplicationsException extends ConflictException {
    public CohortNotAcceptingApplicationsException() {
        super("COHORT_NOT_ACCEPTING", "지금은 이 기수에 신청할 수 없습니다.");
    }
}
