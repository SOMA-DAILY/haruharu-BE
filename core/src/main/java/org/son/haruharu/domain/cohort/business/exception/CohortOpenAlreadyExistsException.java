package org.son.haruharu.domain.cohort.business.exception;

import org.son.haruharu.common.exception.ConflictException;

public class CohortOpenAlreadyExistsException extends ConflictException {
    public CohortOpenAlreadyExistsException() {
        super("COHORT_OPEN_ALREADY_EXISTS", "이 트랙에 이미 공개 중인 기수가 있습니다.");
    }
}
