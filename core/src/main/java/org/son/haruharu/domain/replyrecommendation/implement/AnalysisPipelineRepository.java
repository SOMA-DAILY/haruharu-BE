package org.son.haruharu.domain.replyrecommendation.implement;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 분석 요청·입력 스냅샷·작업 대기열과, 작업기가 저장한 분석·버전 읽기(ai_reply 스키마). */
public interface AnalysisPipelineRepository {

    Optional<ActiveVersion> findActiveVersion();

    Optional<ActiveVersion> findVersion(String versionId);

    /** ai_reply 스키마가 적용됐는가. 적용 전에 스위치를 켜도 업로드가 깨지지 않게 한다. */
    boolean schemaReady();

    Optional<AnalysisRequest> findActiveAnalysisRequest(AnalysisTarget target);

    Optional<AnalysisRequest> findAnalysisRequest(Long id);

    /** 같은 키의 유효 요청이 있으면 그것을, 없으면 revision 을 올려 새로 만든다. */
    AnalysisRequest createAnalysisRequest(AnalysisTarget target, AnalysisOrigin origin, Instant now);

    Optional<Long> claimPendingSnapshot(Instant now, Instant claimUntil);

    /** 입력 스냅샷을 저장하고 id 를 돌려준다(같은 요청의 입력이 이미 있으면 그 id). */
    Long insertAnalysisInput(AnalysisInputDraft draft, Instant now);

    /** 입력에 들어간 원문 출처(원문 대신 해시)를 남긴다. */
    void insertEvidence(EvidenceOwner owner, Long ownerId, List<HistoryRecord> records);

    void markSnapshotReady(Long analysisRequestId, Long analysisInputId, Instant now);

    void retrySnapshot(Long id, Instant nextAttemptAt, String failureCode, int maxAttempts, Instant now);

    void cancelAnalysisRequest(Long id, String reason, Instant now);

    /** 분석 요청과 그 결과·세트·요청·대기 작업을 모두 무효로 한다. */
    void revokeAnalysisRequest(Long id, String reason, Instant now);

    /** 영상의 모든 분석·세트·요청·대기 작업을 무효로 한다. 새로 막은 분석·세트 수를 돌려준다. */
    int revokeVideo(Long videoId, String reason, Instant now);

    Optional<JobState> findJob(String jobKey);

    Optional<JobState> findLatestGenericJob(Long analysisId, String versionId, String gallerySnapshotId);

    boolean enqueueJob(String jobKey, String jobType, String payloadJson, int priority, Instant now);

    Optional<AnalysisSnapshotRef> findAnalysisSnapshotByInput(Long analysisInputId);

    /** 영상별 최신 유효 분석의 현재 장면 설명(과거 영상 기록에 쓴다). */
    Map<Long, String> findLatestVisualDescriptions(Collection<Long> videoIds);

    List<EvidenceRef> findEvidence(EvidenceOwner owner, Long ownerId);

    /** 분석 입력의 방 기록(보관 기간이 지나 지웠으면 빈 목록). 개인화 기록을 고르는 데 쓴다. */
    List<HistoryRecord> findAnalysisInputRecords(Long analysisInputId);

    List<Long> findRecentlyAnalyzedVideoIds(Instant since);

    /** 보관 기간이 지난 원문·파생 자료(방 기록·캡션·분석 설명·후보·카드)를 지우고 해시·삭제 사실만 남긴다. */
    int purgeSnapshotsBefore(Instant before, Instant now);
}
