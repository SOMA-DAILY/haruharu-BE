package org.son.haruharu.domain.cohort.business.exception;

import org.son.haruharu.common.exception.ConflictException;

public class CohortApplicationNotAssignableException extends ConflictException {
    public CohortApplicationNotAssignableException() {
        super("COHORT_APPLICATION_NOT_ASSIGNABLE", "편성 대기이고 PASS 성별이 확인된, 이 방 기수의 신청만 넣을 수 있습니다(이미 이 방 멤버이거나 탈퇴·정지한 유저면 안 됩니다).");
    }
}
