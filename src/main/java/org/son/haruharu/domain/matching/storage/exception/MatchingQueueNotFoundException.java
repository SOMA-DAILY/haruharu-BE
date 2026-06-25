package org.son.haruharu.domain.matching.storage.exception;

import org.son.haruharu.common.exception.NotFoundException;

public class MatchingQueueNotFoundException extends NotFoundException {

    public MatchingQueueNotFoundException() {
        super("MATCHING_404_NOT_QUEUED", "취소할 매칭 대기 내역이 없습니다.");
    }
}
