package org.son.haruharu.domain.matching.implement;

import java.time.LocalDateTime;
import java.util.UUID;

public record MatchingQueue(
        UUID id,
        UUID userId,
        String gender,
        MatchingQueueStatus status,
        LocalDateTime queuedAt
) {
    /** 큐 참가: 신규 대기 항목 생성. id는 영속화 시점에 부여. */
    public static MatchingQueue join(UUID userId, String gender, LocalDateTime queuedAt) {
        return new MatchingQueue(null, userId, gender, MatchingQueueStatus.QUEUED, queuedAt);
    }

    /** 큐 취소: 상태만 CANCELLED로 전이한 새 인스턴스 반환. */
    public MatchingQueue cancelled() {
        return new MatchingQueue(id, userId, gender, MatchingQueueStatus.CANCELLED, queuedAt);
    }

    /** 매칭 확정: 상태만 MATCHED로 전이한 새 인스턴스 반환. */
    public MatchingQueue matched() {
        return new MatchingQueue(id, userId, gender, MatchingQueueStatus.MATCHED, queuedAt);
    }
}
