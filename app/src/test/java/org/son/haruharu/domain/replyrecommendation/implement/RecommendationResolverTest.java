package org.son.haruharu.domain.replyrecommendation.implement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.replyrecommendation.implement.RecommendationResolver.Mode;
import org.son.haruharu.domain.replyrecommendation.implement.exception.RecommendationRateLimitedException;

@ExtendWith(MockitoExtension.class)
class RecommendationResolverTest {

    private static final Instant NOW = Instant.parse("2026-11-08T05:00:00Z");
    private static final ActiveVersion VERSION = new ActiveVersion("v1", 1, "pre-1", "gallery-1");
    private static final String GENERIC_R1 = "generic:900:v1:gallery-1:r1";

    @Mock AnalysisPipelineRepository pipeline;
    @Mock RecommendationRepository recommendations;
    @Mock EvidenceChecker evidence;

    RecommendationResolver resolver;
    ReplyRecommendationProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ReplyRecommendationProperties(true, false, 2, 1000, Duration.ofMinutes(30),
                Duration.ofHours(24), new ReplyRecommendationProperties.Collection(false, List.of()),
                new ReplyRecommendationProperties.Snapshot(3000, 20, 5, Duration.ofDays(30)), List.of());
        resolver = new RecommendationResolver(pipeline, recommendations, evidence, new EnsureRateLimiter(properties),
                properties);
        lenient().when(recommendations.updatePendingRequest(any(), any())).thenReturn(true);
    }

    private static RecommendationRequest pending(boolean personal) {
        return new RecommendationRequest("rq-1", 10L, 77L, 5L, 300L, "v1", personal,
                personal ? RecommendationResolver.PERSONAL_KEY_V1 : RecommendationRequest.NO_PERSONAL_HASH, null,
                RecommendationStatus.PENDING, null, null, NOW.minusSeconds(5), null, null, null);
    }

    private static AnalysisRequest analysis(AnalysisRequestStatus status) {
        return new AnalysisRequest(300L, 77L, 5L, 20L, "scope", List.of(), "cap", "media", "pre-1", 1, AnalysisOrigin.PUBLISH,
                status, status == AnalysisRequestStatus.SNAPSHOT_PENDING ? null : 400L, 1, NOW.minusSeconds(60),
                NOW.minusSeconds(60));
    }

    private static JobState job(String key, String status, String failure, Instant finishedAt, Integer revision) {
        return new JobState(key, status, failure, finishedAt, revision);
    }

    private static RecommendationSet set(String id, String status, Instant expiresAt, RecommendationCard... cards) {
        return new RecommendationSet(id, "GENERIC", 900L, 77L, null, "v1", 1, status, List.of(cards),
                NOW.minusSeconds(30), expiresAt, null);
    }

    private void analysisSucceeded() {
        given(pipeline.findAnalysisRequest(300L)).willReturn(Optional.of(analysis(AnalysisRequestStatus.SNAPSHOT_READY)));
        given(pipeline.findJob("analysis:400")).willReturn(Optional.of(job("analysis:400", "SUCCEEDED", null, NOW, null)));
        given(pipeline.findAnalysisSnapshotByInput(400L)).willReturn(Optional.of(new AnalysisSnapshotRef(900L, 400L, 77L, "READY")));
        lenient().when(pipeline.findVersion("v1")).thenReturn(Optional.of(VERSION));
    }

    @Test
    @DisplayName("스냅샷 대기 중이면 PENDING 으로 남고 아무것도 바꾸지 않는다")
    void givenSnapshotPending_whenResolve_thenStaysPending() {
        given(pipeline.findAnalysisRequest(300L)).willReturn(Optional.of(analysis(AnalysisRequestStatus.SNAPSHOT_PENDING)));

        var result = resolver.resolve(pending(false), Mode.ENSURE, NOW);

        assertThat(result.status()).isEqualTo(RecommendationStatus.PENDING);
        then(recommendations).should(never()).updatePendingRequest(any(), any());
    }

    @Test
    @DisplayName("검수된 일반 세트가 있으면 READY 로 확정하고 세트 만료 시각을 캐시 만료로 쓴다")
    void givenGenericSetReady_whenResolve_thenReady() {
        analysisSucceeded();
        given(pipeline.findLatestGenericJob(900L, "v1", "gallery-1"))
                .willReturn(Optional.of(job(GENERIC_R1, "SUCCEEDED", null, NOW, 1)));
        var ready = set("set-1", "READY", NOW.plusSeconds(3600), new RecommendationCard("c1", "question", "물어볼까요?"));
        given(recommendations.findGenericSet(900L, "v1", "gallery-1", 1)).willReturn(Optional.of(ready));

        var result = resolver.resolve(pending(false), Mode.READ, NOW);

        assertThat(result.status()).isEqualTo(RecommendationStatus.READY);
        assertThat(result.setId()).isEqualTo("set-1");
        assertThat(result.expiresAt()).isEqualTo(ready.expiresAt());
    }

    @Test
    @DisplayName("좋은 후보가 0개인 세트는 EMPTY 이며 카드를 채우지 않는다(GEN01)")
    void givenEmptySet_whenResolve_thenEmpty() {
        analysisSucceeded();
        given(pipeline.findLatestGenericJob(900L, "v1", "gallery-1"))
                .willReturn(Optional.of(job(GENERIC_R1, "SUCCEEDED", null, NOW, 1)));
        given(recommendations.findGenericSet(900L, "v1", "gallery-1", 1))
                .willReturn(Optional.of(set("set-0", "EMPTY", NOW.plusSeconds(3600))));

        var result = resolver.resolve(pending(false), Mode.ENSURE, NOW);

        assertThat(result.status()).isEqualTo(RecommendationStatus.EMPTY);
        assertThat(result.setId()).isEqualTo("set-0");
    }

    @Test
    @DisplayName("검수 응답 불완전·거절로 생성이 실패하면 카드 없이 UNAVAILABLE 이다(ASYNC03)")
    void givenGenerationFailed_whenResolve_thenUnavailableWithoutCards() {
        analysisSucceeded();
        given(pipeline.findLatestGenericJob(900L, "v1", "gallery-1"))
                .willReturn(Optional.of(job(GENERIC_R1, "FAILED", "SCHEMA_INVALID", NOW, 1)));

        var result = resolver.resolve(pending(false), Mode.ENSURE, NOW);

        assertThat(result.status()).isEqualTo(RecommendationStatus.UNAVAILABLE);
        assertThat(result.reason()).isEqualTo(RecommendationReason.GENERATION_FAILED);
        assertThat(result.setId()).isNull();
        assertThat(result.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        then(pipeline).should(never()).enqueueJob(anyString(), anyString(), anyString(), anyInt(), any());
    }

    @Test
    @DisplayName("비용 한도로 실패하면 BUDGET_EXCEEDED 이고 짧게만 유지한다")
    void givenBudgetExceeded_whenResolve_thenShortUnavailable() {
        given(pipeline.findAnalysisRequest(300L)).willReturn(Optional.of(analysis(AnalysisRequestStatus.SNAPSHOT_READY)));
        given(pipeline.findJob("analysis:400"))
                .willReturn(Optional.of(job("analysis:400", "FAILED", "BUDGET_EXCEEDED", NOW, null)));

        var result = resolver.resolve(pending(false), Mode.ENSURE, NOW);

        assertThat(result.reason()).isEqualTo(RecommendationReason.BUDGET_EXCEEDED);
        assertThat(result.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("이전 분석 실패 뒤에 만든 요청이면 분석 요청을 닫고 revision 을 올려 다시 만든다")
    void givenAnalysisFailedBeforeRequest_whenEnsure_thenRetriesWithNewRevision() {
        given(pipeline.findAnalysisRequest(300L)).willReturn(Optional.of(analysis(AnalysisRequestStatus.SNAPSHOT_READY)));
        given(pipeline.findJob("analysis:400"))
                .willReturn(Optional.of(job("analysis:400", "FAILED", "TRANSPORT_EXHAUSTED", NOW.minusSeconds(600), null)));
        given(pipeline.createAnalysisRequest(any(), eq(AnalysisOrigin.ENSURE), eq(NOW)))
                .willReturn(new AnalysisRequest(301L, 77L, 5L, 20L, "scope", List.of(), "cap", "media", "pre-1", 2,
                        AnalysisOrigin.ENSURE, AnalysisRequestStatus.SNAPSHOT_PENDING, null, 0, NOW, NOW));

        var result = resolver.resolve(pending(false), Mode.ENSURE, NOW);

        assertThat(result.status()).isEqualTo(RecommendationStatus.PENDING);
        assertThat(result.analysisRequestId()).isEqualTo(301L);
        then(pipeline).should().cancelAnalysisRequest(300L, "RETRY_AFTER_FAILURE", NOW);
    }

    @Test
    @DisplayName("조회(GET)는 빠진 작업이 있어도 만들지 않는다")
    void givenMissingGenericJob_whenRead_thenDoesNotEnqueue() {
        analysisSucceeded();
        given(pipeline.findLatestGenericJob(900L, "v1", "gallery-1")).willReturn(Optional.empty());

        var result = resolver.resolve(pending(false), Mode.READ, NOW);

        assertThat(result.status()).isEqualTo(RecommendationStatus.PENDING);
        then(pipeline).should(never()).enqueueJob(anyString(), anyString(), anyString(), anyInt(), any());
    }

    @Test
    @DisplayName("24시간이 지난 세트는 다음 revision 으로 다시 만들고, 새 AI 작업은 분당 한도를 센다")
    void givenExpiredSet_whenEnsure_thenEnqueuesNextRevisionUnderRateLimit() {
        analysisSucceeded();
        given(pipeline.findLatestGenericJob(900L, "v1", "gallery-1"))
                .willReturn(Optional.of(job(GENERIC_R1, "SUCCEEDED", null, NOW.minusSeconds(90000), 1)));
        given(recommendations.findGenericSet(900L, "v1", "gallery-1", 1))
                .willReturn(Optional.of(set("set-old", "READY", NOW.minusSeconds(1))));

        var result = resolver.resolve(pending(false), Mode.ENSURE, NOW);

        assertThat(result.status()).isEqualTo(RecommendationStatus.PENDING);
        then(pipeline).should().enqueueJob(eq("generic:900:v1:gallery-1:r2"), eq("GENERIC"), anyString(), anyInt(), eq(NOW));

        resolver.resolve(pending(false), Mode.ENSURE, NOW);
        assertThatThrownBy(() -> resolver.resolve(pending(false), Mode.ENSURE, NOW))
                .isInstanceOf(RecommendationRateLimitedException.class);
    }

    @Test
    @DisplayName("개인화가 켜져도 본인 기록이 없으면 일반 카드를 그대로 쓴다")
    void givenNoOwnRecords_whenPersonal_thenKeepsGenericSet() {
        analysisSucceeded();
        given(pipeline.findLatestGenericJob(900L, "v1", "gallery-1"))
                .willReturn(Optional.of(job(GENERIC_R1, "SUCCEEDED", null, NOW, 1)));
        given(recommendations.findGenericSet(900L, "v1", "gallery-1", 1))
                .willReturn(Optional.of(set("set-1", "READY", NOW.plusSeconds(60), new RecommendationCard("c1", "question", "t"))));
        given(pipeline.findAnalysisInputRecords(400L)).willReturn(List.of(new HistoryRecord("chat:1", "30", "chat",
                "room", "2026-11-08T01:00:00Z", "2026-11-08T01:00:00Z", "r", "남의 경험", List.of())));

        var result = resolver.resolve(pending(true), Mode.ENSURE, NOW);

        assertThat(result.status()).isEqualTo(RecommendationStatus.READY);
        assertThat(result.setId()).isEqualTo("set-1");
        then(recommendations).should(never()).findOrCreatePersonalInput(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("개인화 후보가 채택되지 않으면 작업기가 남긴 일반 세트 id 를 그대로 돌려준다(PER02)")
    void givenPersonalAttemptKeptBase_whenResolve_thenReturnsBaseSetId() {
        analysisSucceeded();
        given(pipeline.findLatestGenericJob(900L, "v1", "gallery-1"))
                .willReturn(Optional.of(job(GENERIC_R1, "SUCCEEDED", null, NOW, 1)));
        var base = set("set-1", "READY", NOW.plusSeconds(60), new RecommendationCard("c1", "question", "t"));
        given(recommendations.findGenericSet(900L, "v1", "gallery-1", 1)).willReturn(Optional.of(base));
        given(pipeline.findJob("personal:55")).willReturn(Optional.of(job("personal:55", "SUCCEEDED", null, NOW, null)));
        given(recommendations.findAttemptResult("personal:55"))
                .willReturn(Optional.of(new GenerationAttemptResult("SUCCEEDED", null, "set-1")));
        given(recommendations.findSet("set-1")).willReturn(Optional.of(base));
        var request = pending(true).withPersonalInput(55L);

        var result = resolver.resolve(request, Mode.READ, NOW);

        assertThat(result.status()).isEqualTo(RecommendationStatus.READY);
        assertThat(result.setId()).isEqualTo("set-1");
    }

    @Test
    @DisplayName("개인화 작업이 실패해도 이미 검수된 일반 카드를 보여 준다")
    void givenPersonalJobFailed_whenResolve_thenFallsBackToGeneric() {
        analysisSucceeded();
        given(pipeline.findLatestGenericJob(900L, "v1", "gallery-1"))
                .willReturn(Optional.of(job(GENERIC_R1, "SUCCEEDED", null, NOW, 1)));
        var base = set("set-1", "READY", NOW.plusSeconds(60), new RecommendationCard("c1", "question", "t"));
        given(recommendations.findGenericSet(900L, "v1", "gallery-1", 1)).willReturn(Optional.of(base));
        given(pipeline.findJob("personal:55")).willReturn(Optional.of(job("personal:55", "FAILED", "REFUSAL", NOW, null)));

        var result = resolver.resolve(pending(true).withPersonalInput(55L), Mode.READ, NOW);

        assertThat(result.setId()).isEqualTo("set-1");
    }

    @Test
    @DisplayName("작업기가 분석을 막았으면(의존 영상 삭제 등) 분석 요청을 닫고 새 revision 으로 새 요청을 만든다")
    void givenRevokedSnapshot_whenEnsure_thenReanalyzesWithNewRequest() {
        given(pipeline.findAnalysisRequest(300L)).willReturn(Optional.of(analysis(AnalysisRequestStatus.SNAPSHOT_READY)));
        given(pipeline.findJob("analysis:400")).willReturn(Optional.of(job("analysis:400", "SUCCEEDED", null, NOW, null)));
        given(pipeline.findAnalysisSnapshotByInput(400L)).willReturn(Optional.of(new AnalysisSnapshotRef(900L, 400L, 77L, "REVOKED")));
        given(pipeline.createAnalysisRequest(any(), eq(AnalysisOrigin.ENSURE), eq(NOW)))
                .willReturn(new AnalysisRequest(302L, 77L, 5L, 20L, "scope", List.of(), "cap", "media", "pre-1", 2,
                        AnalysisOrigin.ENSURE, AnalysisRequestStatus.SNAPSHOT_PENDING, null, 0, NOW, NOW));
        given(recommendations.insertRequest(any())).willAnswer(invocation -> invocation.getArgument(0));

        var result = resolver.resolve(pending(false), Mode.ENSURE, NOW);

        then(pipeline).should().revokeAnalysisRequest(300L, "ANALYSIS_REVOKED", NOW);
        assertThat(result.status()).isEqualTo(RecommendationStatus.PENDING);
        assertThat(result.analysisRequestId()).isEqualTo(302L);
        assertThat(result.requestId()).isNotEqualTo("rq-1");
    }

    @Test
    @DisplayName("카드를 내보내기 직전에 입력 원문이 지워졌으면 카드 대신 숨기고 분석을 막는다(조회)")
    void givenDeletedSourceBeforeFinish_whenRead_thenInvalidatedWithoutCards() {
        analysisSucceeded();
        given(evidence.anyDeleted(EvidenceOwner.ANALYSIS, 400L)).willReturn(true);

        var result = resolver.resolve(pending(false), Mode.READ, NOW);

        assertThat(result.status()).isEqualTo(RecommendationStatus.UNAVAILABLE);
        assertThat(result.reason()).isEqualTo(RecommendationReason.INVALIDATED);
        then(pipeline).should().revokeAnalysisRequest(300L, "ANALYSIS_REVOKED", NOW);
        then(recommendations).should(never()).findGenericSet(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("10분 넘게 끝나지 않은 요청은 기다리지 않고 TIMED_OUT 으로 숨긴다")
    void givenStalePending_whenResolve_thenTimedOut() {
        var stale = new RecommendationRequest("rq-1", 10L, 77L, 5L, 300L, "v1", false,
                RecommendationRequest.NO_PERSONAL_HASH, null, RecommendationStatus.PENDING, null, null,
                NOW.minusSeconds(601), null, null, null);

        var result = resolver.resolve(stale, Mode.READ, NOW);

        assertThat(result.reason()).isEqualTo(RecommendationReason.TIMED_OUT);
        then(pipeline).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("다른 요청이 먼저 확정했으면 덮어쓰지 않고 저장된 상태를 돌려준다")
    void givenConcurrentResolution_whenUpdateLoses_thenReturnsStoredRequest() {
        analysisSucceeded();
        given(pipeline.findLatestGenericJob(900L, "v1", "gallery-1"))
                .willReturn(Optional.of(job(GENERIC_R1, "SUCCEEDED", null, NOW, 1)));
        given(recommendations.findGenericSet(900L, "v1", "gallery-1", 1))
                .willReturn(Optional.of(set("set-1", "READY", NOW.plusSeconds(60), new RecommendationCard("c1", "question", "t"))));
        given(recommendations.updatePendingRequest(any(), any())).willReturn(false);
        var stored = pending(false).unavailable(RecommendationReason.INVALIDATED, NOW, NOW.plusSeconds(60));
        given(recommendations.findRequest("rq-1")).willReturn(Optional.of(stored));

        var result = resolver.resolve(pending(false), Mode.READ, NOW);

        assertThat(result).isEqualTo(stored);
    }
}
