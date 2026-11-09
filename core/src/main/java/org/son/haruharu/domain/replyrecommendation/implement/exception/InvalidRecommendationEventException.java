package org.son.haruharu.domain.replyrecommendation.implement.exception;

import org.son.haruharu.common.exception.UnprocessableEntityException;

public class InvalidRecommendationEventException extends UnprocessableEntityException {
    public InvalidRecommendationEventException(String field) {
        super("REPLY_RECOMMENDATION_EVENT_INVALID", "추천 사건 형식이 올바르지 않습니다: " + field);
    }
}
