package org.son.haruharu.domain.cohort.business.exception;

import org.son.haruharu.common.exception.ConflictException;

public class RecruitTrackInactiveException extends ConflictException {
    public RecruitTrackInactiveException() {
        super("RECRUIT_TRACK_INACTIVE", "비활성 트랙의 기수는 공개할 수 없습니다.");
    }
}
