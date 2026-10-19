package org.son.haruharu.domain.cohort.business.exception;

import org.son.haruharu.common.exception.ConflictException;

public class CohortHasAssignedApplicationsException extends ConflictException {
    public CohortHasAssignedApplicationsException() {
        super("COHORT_HAS_ASSIGNED_APPLICATIONS", "방에 배정된 신청이 있어 기수를 취소할 수 없습니다.");
    }
}
