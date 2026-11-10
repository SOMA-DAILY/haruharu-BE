package org.son.haruharu.domain.replyrecommendation.implement;

import java.time.Instant;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 저장된 추천을 내보내기 전 확인(명세 3장 캐시와 무효화). 만료·무효 세트·취소된 분석·삭제된 근거가 있으면
 * 요청을 무효로 한다. 세트가 무효이거나 근거가 지워졌으면 분석 요청과 그 파생 결과도 함께 막아,
 * 다음 추천 요청이 새 revision 으로 다시 만들게 한다(작업기가 의존 영상 삭제로 세트를 막은 경우 포함).
 */
@Component
@RequiredArgsConstructor
public class RecommendationGuard {

    private final AnalysisPipelineRepository pipeline;
    private final RecommendationRepository recommendations;
    private final EvidenceChecker evidence;

    /**
     * 쓸 수 있으면 true. 쓸 수 없으면 요청을 무효로 표시하고 false 다(쓰기가 있다 — 조회 API 도 쓰기 트랜잭션이다).
     */
    public boolean validateOrRevoke(RecommendationRequest request, Instant now) {
        Optional<String> invalid = invalidReason(request, now);
        invalid.ifPresent(reason -> recommendations.revokeRequest(request.requestId(), reason, now));
        return invalid.isEmpty();
    }

    Optional<String> invalidReason(RecommendationRequest request, Instant now) {
        if (request.revokedAt() != null) {
            return Optional.of("REVOKED");
        }
        if (request.expiredAt(now)) {
            return Optional.of("EXPIRED");
        }
        if (!request.resolvedState()) {
            return Optional.empty();
        }
        Optional<AnalysisRequest> analysis = pipeline.findAnalysisRequest(request.analysisRequestId());
        if (analysis.isEmpty() || analysis.get().status() == AnalysisRequestStatus.CANCELLED) {
            return Optional.of("ANALYSIS_CANCELLED");
        }
        Optional<RecommendationSet> set = recommendations.findSet(request.setId());
        if (set.isEmpty() || set.get().revokedAt() != null) {
            pipeline.revokeAnalysisRequest(analysis.get().id(), "SET_REVOKED", now);
            return Optional.of("SET_REVOKED");
        }
        if (evidence.anyDeleted(EvidenceOwner.ANALYSIS, analysis.get().analysisInputId())
                || evidence.anyDeleted(EvidenceOwner.PERSONAL, request.personalInputId())) {
            pipeline.revokeAnalysisRequest(analysis.get().id(), "SOURCE_DELETED", now);
            return Optional.of("SOURCE_DELETED");
        }
        return Optional.empty();
    }
}
