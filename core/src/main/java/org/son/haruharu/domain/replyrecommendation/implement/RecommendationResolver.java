package org.son.haruharu.domain.replyrecommendation.implement;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 대기 중인 추천 요청을 진행한다: 분석 요청 → 입력 스냅샷 → 분석 작업 → 일반 세트 → (개인화) → 최종 세트.
 * 작업기가 결과를 쓰고, 이 클래스는 그 상태를 읽어 요청을 READY/EMPTY/UNAVAILABLE 로 확정한다.
 *
 * <ul>
 *   <li>{@link Mode#ENSURE}: 추천 요청(POST). 빠진 작업을 넣고, 실패·무효 뒤의 새 요청이면 다시 만든다. 새 작업은 한도를 센다.</li>
 *   <li>{@link Mode#BACKGROUND}: 스케줄러. 개인화 입력 만들기처럼 앱 서버만 할 수 있는 다음 단계를 진행한다.</li>
 *   <li>{@link Mode#READ}: 조회(GET). 상태만 확정하고 작업을 만들지 않는다.</li>
 * </ul>
 * 검수되지 않은 후보는 세트가 되지 않으므로(작업기 계약) 실패는 항상 카드 없는 UNAVAILABLE 이다.
 * 카드를 내보내기 직전에 입력 원문이 지워지지 않았는지 다시 확인한다.
 */
@Component
@RequiredArgsConstructor
public class RecommendationResolver {

    public enum Mode { ENSURE, BACKGROUND, READ }

    public static final String PERSONAL_KEY_V1 = "personal-v1";
    /** 이 시간이 지나도록 작업이 끝나지 않으면(작업기 중단 등) 기다리지 않고 숨긴다. */
    static final Duration PENDING_DEADLINE = Duration.ofMinutes(10);

    private final AnalysisPipelineRepository pipeline;
    private final RecommendationRepository recommendations;
    private final EvidenceChecker evidence;
    private final EnsureRateLimiter rateLimiter;
    private final ReplyRecommendationProperties properties;

    public RecommendationRequest resolve(RecommendationRequest request, Mode mode, Instant now) {
        if (request.status() != RecommendationStatus.PENDING) {
            return request;
        }
        if (request.createdAt().plus(PENDING_DEADLINE).isBefore(now)) {
            return unavailable(request, RecommendationReason.TIMED_OUT, properties.transientUnavailableTtl(), now);
        }
        Optional<AnalysisRequest> found = pipeline.findAnalysisRequest(request.analysisRequestId());
        if (found.isEmpty() || found.get().status() == AnalysisRequestStatus.CANCELLED) {
            return unavailable(request, RecommendationReason.INVALIDATED, properties.transientUnavailableTtl(), now);
        }
        AnalysisRequest analysis = found.get();
        if (analysis.status() == AnalysisRequestStatus.SNAPSHOT_PENDING) {
            return request;
        }
        if (analysis.status() == AnalysisRequestStatus.SNAPSHOT_FAILED) {
            if (mode == Mode.ENSURE && analysis.updatedAt() != null && analysis.updatedAt().isBefore(request.createdAt())) {
                return retryAnalysis(request, analysis, now);
            }
            return unavailable(request, RecommendationReason.ANALYSIS_FAILED, properties.transientUnavailableTtl(), now);
        }

        Optional<JobState> analysisJob = pipeline.findJob(analysis.analysisJobKey());
        if (analysisJob.isEmpty()) {
            if (mode != Mode.READ) {
                enqueue(request, mode, analysis.analysisJobKey(), "ANALYSIS",
                        Map.of("analysis_input_id", analysis.analysisInputId()), now);
            }
            return request;
        }
        if (analysisJob.get().pending()) {
            return request;
        }
        if (analysisJob.get().failed()) {
            if (mode == Mode.ENSURE && analysisJob.get().failedBefore(request.createdAt())) {
                return retryAnalysis(request, analysis, now);
            }
            return unavailable(request, analysisJob.get().budgetExceeded()
                            ? RecommendationReason.BUDGET_EXCEEDED : RecommendationReason.ANALYSIS_FAILED,
                    ttlFor(analysisJob.get()), now);
        }

        Optional<AnalysisSnapshotRef> snapshot = pipeline.findAnalysisSnapshotByInput(analysis.analysisInputId());
        if (snapshot.isEmpty()) {
            return unavailable(request, RecommendationReason.ANALYSIS_FAILED, properties.unavailableTtl(), now);
        }
        if (!snapshot.get().ready() || evidence.anyDeleted(EvidenceOwner.ANALYSIS, analysis.analysisInputId())) {
            // 작업기가 분석을 막았거나(의존 영상 삭제 등) 입력 원문이 지워졌다. 분석부터 다시 만든다.
            return reanalyze(request, analysis, mode, "ANALYSIS_REVOKED", now);
        }
        Optional<ActiveVersion> version = pipeline.findVersion(request.versionId());
        if (version.isEmpty()) {
            return unavailable(request, RecommendationReason.NOT_CONFIGURED, properties.transientUnavailableTtl(), now);
        }

        Step generic = genericSet(request, snapshot.get(), version.get(), mode, now);
        if (generic.waiting()) {
            return request;
        }
        if (generic.reason() != null) {
            return unavailable(request, generic.reason(), generic.ttl(), now);
        }
        if (!request.personal()) {
            return finish(request, generic.set(), now);
        }
        return personal(request, analysis, snapshot.get(), generic.set(), mode, now);
    }

    /** 이용할 수 없게 된 요청을 카드 없이 닫는다(스케줄러가 권한 재확인에 실패했을 때 등). */
    public RecommendationRequest close(RecommendationRequest request, RecommendationReason reason, Instant now) {
        return unavailable(request, reason, properties.transientUnavailableTtl(), now);
    }

    /** 일반 세트: 최신 revision 작업의 상태를 보고, 실패 뒤 새 요청이거나 세트가 만료·무효면 다음 revision 을 넣는다. */
    private Step genericSet(RecommendationRequest request, AnalysisSnapshotRef snapshot, ActiveVersion version,
                            Mode mode, Instant now) {
        Optional<JobState> latest = pipeline.findLatestGenericJob(snapshot.id(), version.versionId(),
                version.gallerySnapshotId());
        if (latest.isEmpty()) {
            if (mode != Mode.READ) {
                enqueueGeneric(request, mode, snapshot, version, 1, now);
            }
            return Step.WAIT;
        }
        JobState job = latest.get();
        int revision = job.revisionNo() == null ? 1 : job.revisionNo();
        if (job.pending()) {
            return Step.WAIT;
        }
        if (job.failed()) {
            if (mode == Mode.ENSURE && job.failedBefore(request.createdAt())) {
                enqueueGeneric(request, mode, snapshot, version, revision + 1, now);
                return Step.WAIT;
            }
            return Step.unavailable(job.budgetExceeded()
                    ? RecommendationReason.BUDGET_EXCEEDED : RecommendationReason.GENERATION_FAILED, ttlFor(job));
        }
        Optional<RecommendationSet> set = recommendations.findGenericSet(snapshot.id(), version.versionId(),
                version.gallerySnapshotId(), revision);
        if (set.isEmpty()) {
            return Step.unavailable(RecommendationReason.GENERATION_FAILED, properties.unavailableTtl());
        }
        if (!set.get().usableAt(now)) {
            // 24시간 캐시가 끝났거나 무효가 됐다. 새 요청이 같은 분석으로 다시 만든다.
            if (mode == Mode.READ) {
                return Step.unavailable(RecommendationReason.INVALIDATED, properties.transientUnavailableTtl());
            }
            enqueueGeneric(request, mode, snapshot, version, revision + 1, now);
            return Step.WAIT;
        }
        return Step.ready(set.get());
    }

    /**
     * 개인화: 시청자 본인 기록이 없으면 일반 카드를 그대로 쓴다. 있으면 개인 입력을 만들고 작업을 넣는다.
     * 개인화 작업이 실패해도 이미 검수된 일반 카드를 쓴다. 채택이 없으면 작업기가 일반 세트 id 를 결과로 둔다.
     */
    private RecommendationRequest personal(RecommendationRequest request, AnalysisRequest analysis,
                                           AnalysisSnapshotRef snapshot, RecommendationSet base, Mode mode,
                                           Instant now) {
        if (request.personalInputId() == null) {
            if (mode == Mode.READ) {
                return request;
            }
            List<HistoryRecord> records = PersonalHistorySelector.select(
                    pipeline.findAnalysisInputRecords(analysis.analysisInputId()), request.viewerId(),
                    analysis.authorId());
            if (records.isEmpty()) {
                return finish(request, base, now);
            }
            Long inputId = recommendations.findOrCreatePersonalInput(request.viewerId(), request.videoId(),
                    snapshot.id(), base.setId(), records, AiReplyJson.sha256(AiReplyJson.write(records)),
                    request.versionId(), now);
            RecommendationRequest withInput = request.withPersonalInput(inputId);
            if (!recommendations.updatePendingRequest(withInput, now)) {
                return current(request);
            }
            enqueue(withInput, mode, personalJobKey(inputId), "PERSONAL", Map.of("personal_input_id", inputId), now);
            return withInput;
        }
        String key = personalJobKey(request.personalInputId());
        Optional<JobState> job = pipeline.findJob(key);
        if (job.isEmpty()) {
            if (mode != Mode.READ) {
                enqueue(request, mode, key, "PERSONAL", Map.of("personal_input_id", request.personalInputId()), now);
            }
            return request;
        }
        if (job.get().pending()) {
            return request;
        }
        if (job.get().failed() || evidence.anyDeleted(EvidenceOwner.PERSONAL, request.personalInputId())) {
            return finish(request, base, now);
        }
        Optional<GenerationAttemptResult> attempt = recommendations.findAttemptResult(key);
        if (attempt.isEmpty() || !attempt.get().succeeded()) {
            return finish(request, base, now);
        }
        RecommendationSet set = recommendations.findSet(attempt.get().resultSetId())
                .filter(candidate -> candidate.usableAt(now))
                .orElse(base);
        return finish(request, set, now);
    }

    /** 실패한 분석 뒤에 만든 요청: 분석 요청을 닫고 revision 을 올려 처음부터 다시 만든다. */
    private RecommendationRequest retryAnalysis(RecommendationRequest request, AnalysisRequest analysis, Instant now) {
        rateLimiter.acquire(request.viewerId(), now);
        pipeline.cancelAnalysisRequest(analysis.id(), "RETRY_AFTER_FAILURE", now);
        AnalysisRequest retry = pipeline.createAnalysisRequest(analysis.target(), AnalysisOrigin.ENSURE, now);
        RecommendationRequest moved = request.withAnalysisRequest(retry.id());
        return recommendations.updatePendingRequest(moved, now) ? moved : current(request);
    }

    /**
     * 분석 결과를 쓸 수 없다(작업기가 막았거나 근거 원문이 지워짐). 분석 요청과 그 파생 결과를 모두 막고,
     * 추천 요청(POST)이면 새 revision 의 분석 요청으로 새 요청을 만든다. 조회·스케줄러는 숨기고 다음 요청을 기다린다.
     */
    private RecommendationRequest reanalyze(RecommendationRequest request, AnalysisRequest analysis, Mode mode,
                                            String reason, Instant now) {
        pipeline.revokeAnalysisRequest(analysis.id(), reason, now);
        if (mode != Mode.ENSURE) {
            return request.unavailable(RecommendationReason.INVALIDATED, now, now.plus(properties.transientUnavailableTtl()));
        }
        rateLimiter.acquire(request.viewerId(), now);
        AnalysisRequest fresh = pipeline.createAnalysisRequest(analysis.target(), AnalysisOrigin.ENSURE, now);
        return recommendations.insertRequest(RecommendationRequest.pending(request.viewerId(), request.videoId(),
                request.roomId(), fresh.id(), request.versionId(), request.personal(), request.personalSnapshotHash(),
                now));
    }

    private void enqueueGeneric(RecommendationRequest request, Mode mode, AnalysisSnapshotRef snapshot,
                                ActiveVersion version, int revision, Instant now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("analysis_id", snapshot.id());
        payload.put("version_id", version.versionId());
        payload.put("gallery_snapshot_id", version.gallerySnapshotId());
        payload.put("revision_no", revision);
        enqueue(request, mode, genericJobKey(snapshot.id(), version, revision), "GENERIC", payload, now);
    }

    private void enqueue(RecommendationRequest request, Mode mode, String key, String type,
                         Map<String, Object> payload, Instant now) {
        if (pipeline.findJob(key).isPresent()) {
            return;
        }
        if (mode == Mode.ENSURE) {
            rateLimiter.acquire(request.viewerId(), now);
        }
        pipeline.enqueueJob(key, type, AiReplyJson.write(payload), AnalysisSnapshotPreparer.PRIORITY_VIEWER, now);
    }

    private RecommendationRequest finish(RecommendationRequest request, RecommendationSet set, Instant now) {
        RecommendationRequest resolved = request.resolved(set, now);
        return recommendations.updatePendingRequest(resolved, now) ? resolved : current(request);
    }

    private RecommendationRequest unavailable(RecommendationRequest request, RecommendationReason reason,
                                              Duration ttl, Instant now) {
        RecommendationRequest resolved = request.unavailable(reason, now, now.plus(ttl));
        return recommendations.updatePendingRequest(resolved, now) ? resolved : current(request);
    }

    /** 다른 요청이 먼저 확정·무효화했다. 저장된 상태를 그대로 쓴다(덮어쓰지 않는다). */
    private RecommendationRequest current(RecommendationRequest request) {
        return recommendations.findRequest(request.requestId()).orElse(request);
    }

    private Duration ttlFor(JobState job) {
        return job.transientFailure() ? properties.transientUnavailableTtl() : properties.unavailableTtl();
    }

    public static String genericJobKey(Long analysisId, ActiveVersion version, int revision) {
        return "generic:" + analysisId + ":" + version.versionId() + ":" + version.gallerySnapshotId() + ":r" + revision;
    }

    public static String personalJobKey(Long personalInputId) {
        return "personal:" + personalInputId;
    }

    private record Step(boolean waiting, RecommendationSet set, RecommendationReason reason, Duration ttl) {
        static final Step WAIT = new Step(true, null, null, null);

        static Step ready(RecommendationSet set) {
            return new Step(false, set, null, null);
        }

        static Step unavailable(RecommendationReason reason, Duration ttl) {
            return new Step(false, null, reason, ttl);
        }
    }
}
