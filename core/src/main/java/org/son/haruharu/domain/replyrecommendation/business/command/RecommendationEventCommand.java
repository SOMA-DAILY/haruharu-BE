package org.son.haruharu.domain.replyrecommendation.business.command;

import org.son.haruharu.domain.replyrecommendation.implement.RawRecommendationEvent;

/** 앱이 보낸 사건 원본(검증 전). 초안 글은 받지 않는다. */
public record RecommendationEventCommand(
        String eventId,
        String sessionId,
        String requestId,
        String setId,
        String cardId,
        String eventType,
        String occurredAt
) {
    public RawRecommendationEvent toRaw() {
        return new RawRecommendationEvent(eventId, sessionId, requestId, setId, cardId, eventType, occurredAt);
    }
}
