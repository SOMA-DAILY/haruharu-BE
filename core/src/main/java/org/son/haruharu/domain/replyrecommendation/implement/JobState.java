package org.son.haruharu.domain.replyrecommendation.implement;

import java.time.Instant;

/** 작업기 작업 하나의 상태(앱은 읽기만 한다). */
public record JobState(String jobKey, String status, String failureCode, Instant finishedAt, Integer revisionNo) {

    public boolean pending() {
        return "QUEUED".equals(status) || "RUNNING".equals(status) || "RETRY_WAIT".equals(status);
    }

    public boolean succeeded() {
        return "SUCCEEDED".equals(status);
    }

    public boolean failed() {
        return "FAILED".equals(status) || "CANCELLED".equals(status);
    }

    public boolean budgetExceeded() {
        return "BUDGET_EXCEEDED".equals(failureCode);
    }

    /** 다시 해 볼 만한 실패(비용 한도·전송 재시도 소진·모델 계정 크레딧 소진). 거절·형식 위반 같은 의미 실패는 아니다. */
    public boolean transientFailure() {
        return budgetExceeded() || "TRANSPORT_EXHAUSTED".equals(failureCode)
                || "PROVIDER_QUOTA_EXCEEDED".equals(failureCode);
    }

    /** 이 실패가 주어진 시각보다 먼저 끝났는가. 그 뒤에 만든 요청은 새로 시도할 수 있다. */
    public boolean failedBefore(Instant time) {
        return failed() && finishedAt != null && finishedAt.isBefore(time);
    }
}
