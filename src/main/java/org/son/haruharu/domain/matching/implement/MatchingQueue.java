package org.son.haruharu.domain.matching.implement;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record MatchingQueue(
        UUID id,
        UUID userId,
        String gender,
        LocalDate birthday,
        MatchingQueueStatus status,
        LocalDateTime queuedAt,
        LocalDateTime matchedAt
) {
    /** 큐 참가: 신규 대기 항목 생성. id는 영속화 시점에 부여, matchedAt은 매칭 확정 전까지 null. */
    public static MatchingQueue join(UUID userId, String gender, LocalDate birthday, LocalDateTime queuedAt) {
        return new MatchingQueue(null, userId, gender, birthday, MatchingQueueStatus.QUEUED, queuedAt, null);
    }

    /** 큐 취소: 상태만 CANCELLED로 전이한 새 인스턴스 반환. */
    public MatchingQueue cancelled() {
        return new MatchingQueue(id, userId, gender, birthday, MatchingQueueStatus.CANCELLED, queuedAt, matchedAt);
    }

    /** 매칭 확정: MATCHED로 전이하고 확정 시각을 기록한 새 인스턴스 반환. */
    public MatchingQueue matched(LocalDateTime matchedAt) {
        return new MatchingQueue(id, userId, gender, birthday, MatchingQueueStatus.MATCHED, queuedAt, matchedAt);
    }
}
