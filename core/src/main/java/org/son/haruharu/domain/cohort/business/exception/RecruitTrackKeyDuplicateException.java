package org.son.haruharu.domain.cohort.business.exception;

import org.son.haruharu.common.exception.ConflictException;

public class RecruitTrackKeyDuplicateException extends ConflictException {
    public RecruitTrackKeyDuplicateException() {
        super("RECRUIT_TRACK_KEY_DUPLICATE", "이미 있는 트랙 키입니다.");
    }
}
