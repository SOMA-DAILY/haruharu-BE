package org.son.haruharu.domain.cohort.implement.exception;

import org.son.haruharu.common.exception.ConflictException;

public class CohortNotEditableException extends ConflictException {
    public CohortNotEditableException() {
        super("COHORT_NOT_EDITABLE", "정산되었거나 취소된 기수는 수정할 수 없습니다.");
    }
}
