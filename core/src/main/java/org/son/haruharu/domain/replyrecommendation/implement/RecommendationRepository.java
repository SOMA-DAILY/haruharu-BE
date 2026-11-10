package org.son.haruharu.domain.replyrecommendation.implement;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 시청자 추천 요청·카드 세트·개인화 입력(ai_reply 스키마). */
public interface RecommendationRepository {

    Optional<RecommendationRequest> findRequest(String requestId);

    Optional<RecommendationRequest> findActiveRequest(Long viewerId, Long analysisRequestId, String versionId,
                                                      String personalSnapshotHash);

    Optional<RecommendationRequest> findLatestRequest(Long viewerId, Long videoId);

    /** 같은 키의 유효 요청이 이미 있으면 그것을 돌려준다(동시 요청에도 하나만 남는다). */
    RecommendationRequest insertRequest(RecommendationRequest request);

    /** 대기(PENDING) 중인 유효 요청만 바꾼다. 다른 요청이 먼저 확정했으면 false(덮어쓰지 않는다). */
    boolean updatePendingRequest(RecommendationRequest request, Instant now);

    void revokeRequest(String requestId, String reason, Instant now);

    Optional<RecommendationSet> findSet(String setId);

    /** 이 시청자가 이 영상에서 받은 요청 중 이 세트를 돌려준 것이 있는가(답장 출처 확인). */
    boolean viewerReceivedSet(Long viewerId, Long videoId, String setId);

    /** 개인화 입력을 아직 만들지 못한 대기 요청(백그라운드 진행용). */
    List<RecommendationRequest> findPendingPersonalRequests(Instant since, int limit);

    Optional<RecommendationSet> findGenericSet(Long analysisId, String versionId, String gallerySnapshotId,
                                               int revisionNo);

    Optional<GenerationAttemptResult> findAttemptResult(String attemptKey);

    /** 같은 기본 세트·시청자·기록 해시·버전이면 기존 입력을 돌려준다. 새로 만들면 출처도 남긴다. */
    Long findOrCreatePersonalInput(Long viewerId, Long videoId, Long analysisId, String baseSetId,
                                   List<HistoryRecord> records, String personalSnapshotHash, String versionId,
                                   Instant now);
}
