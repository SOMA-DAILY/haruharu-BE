package org.son.haruharu.domain.replyrecommendation.business.dto;

import org.son.haruharu.domain.replyrecommendation.implement.RecommendationCard;

public record RecommendationCardResponse(String cardId, String category, String title) {

    public static RecommendationCardResponse from(RecommendationCard card) {
        return new RecommendationCardResponse(card.cardId(), card.category(), card.title());
    }
}
