package org.son.haruharu.domain.replyrecommendation.implement.exception;

import org.son.haruharu.common.exception.TooManyRequestsException;

public class RecommendationRateLimitedException extends TooManyRequestsException {
    public RecommendationRateLimitedException() {
        super("REPLY_RECOMMENDATION_RATE_LIMITED", "잠시 뒤 다시 시도해 주세요.");
    }
}
