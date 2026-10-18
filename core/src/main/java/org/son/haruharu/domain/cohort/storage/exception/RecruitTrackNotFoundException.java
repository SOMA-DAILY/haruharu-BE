package org.son.haruharu.domain.cohort.storage.exception;

import org.son.haruharu.common.exception.NotFoundException;

public class RecruitTrackNotFoundException extends NotFoundException {
    public RecruitTrackNotFoundException() {
        super("RECRUIT_TRACK_NOT_FOUND", "존재하지 않는 모집 트랙입니다.");
    }
}
