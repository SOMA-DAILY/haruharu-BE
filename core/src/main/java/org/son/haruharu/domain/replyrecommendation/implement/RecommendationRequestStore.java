package org.son.haruharu.domain.replyrecommendation.implement;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 시청자 추천 요청과 그 요청이 가리키는 카드 세트를 읽고 만든다. */
@Component
@RequiredArgsConstructor
public class RecommendationRequestStore {

    private final RecommendationRepository recommendations;

    public Optional<RecommendationRequest> find(String requestId) {
        return recommendations.findRequest(requestId);
    }

    public Optional<RecommendationRequest> findActive(Long viewerId, Long analysisRequestId, String versionId,
                                                      String personalKey) {
        return recommendations.findActiveRequest(viewerId, analysisRequestId, versionId, personalKey);
    }

    public Optional<RecommendationRequest> findLatest(Long viewerId, Long videoId) {
        return recommendations.findLatestRequest(viewerId, videoId);
    }

    /** 같은 키의 유효 요청이 이미 있으면(동시 요청) 그것을 돌려준다. */
    public RecommendationRequest createPending(Long viewerId, Long videoId, Long roomId, Long analysisRequestId,
                                               String versionId, boolean personal, String personalKey, Instant now) {
        return recommendations.insertRequest(RecommendationRequest.pending(viewerId, videoId, roomId,
                analysisRequestId, versionId, personal, personalKey, now));
    }

    public List<RecommendationRequest> findPendingPersonal(Instant since, int limit) {
        return recommendations.findPendingPersonalRequests(since, limit);
    }

    public Optional<RecommendationSet> findSet(String setId) {
        return recommendations.findSet(setId);
    }
}
