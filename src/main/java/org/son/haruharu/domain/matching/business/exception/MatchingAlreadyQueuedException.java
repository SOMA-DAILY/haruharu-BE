package org.son.haruharu.domain.matching.business.exception;

import org.son.haruharu.common.exception.ConflictException;

public class MatchingAlreadyQueuedException extends ConflictException {

    public MatchingAlreadyQueuedException() {
        super("MATCHING_409_ALREADY_QUEUED", "이미 매칭 신청한 상태입니다.");
    }
}
