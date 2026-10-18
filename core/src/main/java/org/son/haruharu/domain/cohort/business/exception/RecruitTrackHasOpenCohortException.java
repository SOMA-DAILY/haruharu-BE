package org.son.haruharu.domain.cohort.business.exception;

import org.son.haruharu.common.exception.ConflictException;

public class RecruitTrackHasOpenCohortException extends ConflictException {
    public RecruitTrackHasOpenCohortException() {
        super("RECRUIT_TRACK_HAS_OPEN_COHORT", "공개 중인 기수가 있는 트랙은 비활성화할 수 없습니다.");
    }
}
