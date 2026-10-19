package org.son.haruharu.domain.cohort.storage.exception;

import org.son.haruharu.common.exception.NotFoundException;

public class CohortNotFoundException extends NotFoundException {
    public CohortNotFoundException() {
        super("COHORT_NOT_FOUND", "존재하지 않는 기수입니다.");
    }
}
