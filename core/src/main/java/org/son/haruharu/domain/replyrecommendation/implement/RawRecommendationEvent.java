package org.son.haruharu.domain.replyrecommendation.implement;

/** 앱이 보낸 사건 값 그대로(검증 전). */
public record RawRecommendationEvent(
        String eventId,
        String sessionId,
        String requestId,
        String setId,
        String cardId,
        String eventType,
        String occurredAt
) {}
