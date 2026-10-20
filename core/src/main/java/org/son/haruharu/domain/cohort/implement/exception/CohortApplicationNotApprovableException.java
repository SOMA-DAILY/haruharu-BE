package org.son.haruharu.domain.cohort.implement.exception;

import org.son.haruharu.common.exception.ConflictException;

public class CohortApplicationNotApprovableException extends ConflictException {
    public CohortApplicationNotApprovableException() {
        super("COHORT_APPLICATION_NOT_APPROVABLE", "확인 필요 상태이고 PASS 성별이 남성·여성인 신청만 승인할 수 있습니다.");
    }
}
