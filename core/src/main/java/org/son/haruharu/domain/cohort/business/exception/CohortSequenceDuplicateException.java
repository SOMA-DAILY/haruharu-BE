package org.son.haruharu.domain.cohort.business.exception;

import org.son.haruharu.common.exception.ConflictException;

public class CohortSequenceDuplicateException extends ConflictException {
    public CohortSequenceDuplicateException() {
        super("COHORT_SEQUENCE_DUPLICATE", "이 트랙에 같은 번호의 기수가 이미 있습니다.");
    }
}
