package org.son.haruharu.domain.matching.business.exception;

import org.son.haruharu.common.exception.ConflictException;

public class MatchingClosedException extends ConflictException {

    public MatchingClosedException() {
        super("MATCHING_CLOSED", "오늘 신청이 마감되었습니다. 내일 다시 신청해주세요.");
    }
}
