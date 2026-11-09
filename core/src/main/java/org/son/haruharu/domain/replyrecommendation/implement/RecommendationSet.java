package org.son.haruharu.domain.replyrecommendation.implement;

import java.time.Instant;
import java.util.List;

/** 작업기가 만든 최종 카드 세트(내용 불변). 일반 세트는 같은 분석을 보는 시청자가 함께 쓴다. */
public record RecommendationSet(
        String setId,
        String kind,
        Long analysisId,
        Long videoId,
        Long viewerId,
        String versionId,
        int revisionNo,
        String status,
        List<RecommendationCard> cards,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt
) {
    public boolean usableAt(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    public RecommendationStatus recommendationStatus() {
        return "EMPTY".equals(status) || cards.isEmpty() ? RecommendationStatus.EMPTY : RecommendationStatus.READY;
    }

    public boolean containsCard(String cardId) {
        return cards.stream().anyMatch(card -> card.cardId().equals(cardId));
    }
}
