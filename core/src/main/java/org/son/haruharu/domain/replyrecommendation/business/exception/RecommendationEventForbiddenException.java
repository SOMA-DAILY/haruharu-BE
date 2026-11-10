package org.son.haruharu.domain.replyrecommendation.business.exception;

import org.son.haruharu.common.exception.ForbiddenException;

public class RecommendationEventForbiddenException extends ForbiddenException {
    public RecommendationEventForbiddenException() {
        super("REPLY_RECOMMENDATION_EVENT_FORBIDDEN", "본인이 받은 추천에만 사건을 남길 수 있습니다.");
    }
}
