package org.son.haruharu.domain.replyrecommendation.business.exception;

import org.son.haruharu.common.exception.ConflictException;

public class RecommendationEventConflictException extends ConflictException {
    public RecommendationEventConflictException() {
        super("REPLY_RECOMMENDATION_EVENT_CONFLICT", "같은 사건 id 로 다른 내용이 이미 기록됐습니다.");
    }
}
