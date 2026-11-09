package org.son.haruharu.domain.replyrecommendation.implement;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 스냅샷을 기다리는 분석 요청을 하나씩 잡는다(SKIP LOCKED, 60초 점유). 실패하면 30초 뒤 다시, 5번이면 실패. */
@Component
@RequiredArgsConstructor
public class AnalysisSnapshotQueue {

    static final Duration CLAIM = Duration.ofSeconds(60);
    static final Duration RETRY_AFTER = Duration.ofSeconds(30);

    private final AnalysisPipelineRepository pipeline;
    private final ReplyRecommendationProperties properties;

    public boolean ready() {
        return pipeline.schemaReady();
    }

    public Optional<Long> claimNext(Instant now) {
        return pipeline.claimPendingSnapshot(now, now.plus(CLAIM));
    }

    public void retryLater(Long analysisRequestId, String failureCode, Instant now) {
        pipeline.retrySnapshot(analysisRequestId, now.plus(RETRY_AFTER), failureCode,
                properties.snapshot().maxAttempts(), now);
    }
}
