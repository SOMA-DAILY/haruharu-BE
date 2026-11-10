package org.son.haruharu.domain.replyrecommendation.implement;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobStateTest {

    private static JobState failed(String code) {
        return new JobState("analysis:1", "FAILED", code, Instant.parse("2026-11-11T00:00:00Z"), null);
    }

    @Test
    @DisplayName("모델 계정 크레딧 소진은 비용 한도·전송 실패처럼 일시 실패다. 거절은 아니다")
    void givenFailureCodes_whenTransientFailure_thenQuotaIsTransient() {
        // given // when // then
        assertThat(failed("PROVIDER_QUOTA_EXCEEDED").transientFailure()).isTrue();
        assertThat(failed("PROVIDER_QUOTA_EXCEEDED").budgetExceeded()).isFalse();
        assertThat(failed("TRANSPORT_EXHAUSTED").transientFailure()).isTrue();
        assertThat(failed("BUDGET_EXCEEDED").transientFailure()).isTrue();
        assertThat(failed("PROVIDER_REJECTED").transientFailure()).isFalse();
        assertThat(failed("REFUSAL").transientFailure()).isFalse();
    }
}
