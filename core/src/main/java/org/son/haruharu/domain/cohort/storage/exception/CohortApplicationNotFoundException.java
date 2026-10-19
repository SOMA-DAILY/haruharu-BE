package org.son.haruharu.domain.cohort.storage.exception;

import org.son.haruharu.common.exception.NotFoundException;

public class CohortApplicationNotFoundException extends NotFoundException {
    public CohortApplicationNotFoundException() {
        super("COHORT_APPLICATION_NOT_FOUND", "신청 내역을 찾을 수 없습니다.");
    }
}
