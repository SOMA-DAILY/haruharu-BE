package org.son.haruharu.domain.replyrecommendation.business.dto;

import java.util.List;

import org.son.haruharu.domain.replyrecommendation.implement.RecommendationCard;
import org.son.haruharu.domain.replyrecommendation.implement.RecommendationReason;
import org.son.haruharu.domain.replyrecommendation.implement.RecommendationStatus;

/**
 * 추천 요청·조회 응답(recommendation-response.schema.json). 모든 키를 항상 내보낸다(null 포함).
 * PENDING: 카드 없음·set_id null·retry_after_ms&gt;0 / READY: 카드 1~3 / EMPTY: 카드 없음·set_id 있음 /
 * UNAVAILABLE: 카드 없음·set_id null·reason_code 있음.
 */
public record RecommendationResponse(
        RecommendationStatus status,
        String requestId,
        String setId,
        String versionId,
        List<RecommendationCardResponse> cards,
        Long retryAfterMs,
        String reasonCode
) {
    public static final String NO_REQUEST = "none";

    public static RecommendationResponse pending(String requestId, long retryAfterMs) {
        return new RecommendationResponse(RecommendationStatus.PENDING, requestId, null, null, List.of(),
                Math.max(1, retryAfterMs), null);
    }

    public static RecommendationResponse resolved(RecommendationStatus status, String requestId, String setId,
                                                  String versionId, List<RecommendationCard> cards) {
        List<RecommendationCardResponse> visible = status == RecommendationStatus.READY
                ? cards.stream().limit(3).map(RecommendationCardResponse::from).toList()
                : List.of();
        return new RecommendationResponse(status, requestId, setId, versionId, visible, null, null);
    }

    public static RecommendationResponse unavailable(String requestId, RecommendationReason reason) {
        return new RecommendationResponse(RecommendationStatus.UNAVAILABLE, requestId, null, null, List.of(), null,
                reason.name());
    }

    public static RecommendationResponse unavailable(RecommendationReason reason) {
        return unavailable(NO_REQUEST, reason);
    }

    public boolean pending() {
        return status == RecommendationStatus.PENDING;
    }
}
