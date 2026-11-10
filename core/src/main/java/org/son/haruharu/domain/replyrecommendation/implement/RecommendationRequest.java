package org.son.haruharu.domain.replyrecommendation.implement;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * 시청자 한 명의 추천 요청. request_id 는 앱에 나간다. 같은 키(분석 요청·버전·개인 해시)의 유효 요청은 하나다.
 * 상태만 바뀌고, 무효가 되면 revoked_at 을 남기고 새 요청을 만든다.
 */
public record RecommendationRequest(
        String requestId,
        Long viewerId,
        Long videoId,
        Long roomId,
        Long analysisRequestId,
        String versionId,
        boolean personal,
        String personalSnapshotHash,
        Long personalInputId,
        RecommendationStatus status,
        RecommendationReason reason,
        String setId,
        Instant createdAt,
        Instant resolvedAt,
        Instant expiresAt,
        Instant revokedAt
) {
    public static final String NO_PERSONAL_HASH = "none";

    /** 항상 일시 실패 유지 시간으로 저장되는 이유. */
    private static final Set<RecommendationReason> TRANSIENT_REASONS = EnumSet.of(
            RecommendationReason.TIMED_OUT, RecommendationReason.BUDGET_EXCEEDED,
            RecommendationReason.INVALIDATED, RecommendationReason.NOT_CONFIGURED);
    /** 실패 종류(전송 실패·거절 등)에 따라 유지 시간이 갈리는 이유. */
    private static final Set<RecommendationReason> FAILURE_REASONS = EnumSet.of(
            RecommendationReason.ANALYSIS_FAILED, RecommendationReason.GENERATION_FAILED);

    public static RecommendationRequest pending(Long viewerId, Long videoId, Long roomId, Long analysisRequestId,
                                                String versionId, boolean personal, String personalSnapshotHash,
                                                Instant now) {
        return new RecommendationRequest(UUID.randomUUID().toString(), viewerId, videoId, roomId, analysisRequestId,
                versionId, personal, personalSnapshotHash, null, RecommendationStatus.PENDING, null, null,
                now, null, null, null);
    }

    public RecommendationRequest resolved(RecommendationSet set, Instant now) {
        return new RecommendationRequest(requestId, viewerId, videoId, roomId, analysisRequestId, versionId, personal,
                personalSnapshotHash, personalInputId, set.recommendationStatus(), null, set.setId(),
                createdAt, now, set.expiresAt(), revokedAt);
    }

    public RecommendationRequest unavailable(RecommendationReason reason, Instant now, Instant expiresAt) {
        return new RecommendationRequest(requestId, viewerId, videoId, roomId, analysisRequestId, versionId, personal,
                personalSnapshotHash, personalInputId, RecommendationStatus.UNAVAILABLE, reason, null,
                createdAt, now, expiresAt, revokedAt);
    }

    public RecommendationRequest withPersonalInput(Long personalInputId) {
        return new RecommendationRequest(requestId, viewerId, videoId, roomId, analysisRequestId, versionId, personal,
                personalSnapshotHash, personalInputId, status, reason, setId, createdAt, resolvedAt, expiresAt,
                revokedAt);
    }

    public RecommendationRequest withAnalysisRequest(Long analysisRequestId) {
        return new RecommendationRequest(requestId, viewerId, videoId, roomId, analysisRequestId, versionId, personal,
                personalSnapshotHash, personalInputId, status, reason, setId, createdAt, resolvedAt, expiresAt,
                revokedAt);
    }

    /** 이 시청자가 이 방·영상에서 받은, 이 세트를 돌려준 요청인가(사건 소유 확인). */
    public boolean isIssuedTo(Long viewer, Long room, Long video, String set) {
        return viewerId.equals(viewer) && roomId.equals(room) && videoId.equals(video)
                && setId != null && setId.equals(set);
    }

    public boolean resolvedState() {
        return status == RecommendationStatus.READY || status == RecommendationStatus.EMPTY;
    }

    /**
     * 다시 요청하면 복구될 수 있는 UNAVAILABLE 인가(시간 초과·비용 한도·무효화·전송 실패 등).
     * 분석·생성 실패는 영구 실패 유지 시간보다 짧게 저장된 것(일시 실패)만 해당한다.
     * 두 유지 시간이 같으면 구분할 수 없으므로 분석·생성 실패는 해당하지 않는다.
     * 권한·소유(OWN_VIDEO 등)·거절로 닫힌 요청은 해당하지 않는다.
     */
    public boolean retryableUnavailable(Duration permanentTtl) {
        if (status != RecommendationStatus.UNAVAILABLE || reason == null) {
            return false;
        }
        if (TRANSIENT_REASONS.contains(reason)) {
            return true;
        }
        if (!FAILURE_REASONS.contains(reason) || resolvedAt == null || expiresAt == null) {
            return false;
        }
        return Duration.between(resolvedAt, expiresAt).compareTo(permanentTtl) < 0;
    }

    public boolean expiredAt(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }
}
