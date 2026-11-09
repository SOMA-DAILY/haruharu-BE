package org.son.haruharu.domain.replyrecommendation.implement;

import java.time.Instant;

/** 앱이 보낸 노출·선택·입력 시작·닫기 사건 하나(초안 글 없음). */
public record RecommendationEvent(
        String eventId,
        String sessionId,
        String requestId,
        String setId,
        String cardId,
        Long viewerId,
        Long videoId,
        String eventType,
        Instant occurredAt,
        Instant receivedAt,
        boolean late,
        String payloadSha,
        Provenance provenance
) {
    public static final String IMPRESSION = "impression";
    public static final String CARD_SELECTED = "card_selected";
    public static final String COMPOSER_STARTED = "composer_started";
    public static final String DISMISSED = "dismissed";
}
