package org.son.haruharu.domain.matching.implement;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record MatchingQueue(
        Long id,
        Long userId,
        String gender,
        LocalDate birthday,
        MatchingQueueStatus status,
        LocalDateTime queuedAt,
        LocalDateTime matchedAt
) {
    /** 참가 비용(구름). 취소·미성사 환불액과 동일해야 한다. */
    public static final long ENTER_COST = 5L;

    /** 코인 원장 참조 타입. 차감·환불 ledger의 ref_type으로 기록된다. */
    public static final String COIN_REF_TYPE = "MATCHING_QUEUE";

    /** 매칭 확정 시각(시). MatchingScheduler의 확정 cron과 홈 카운트다운(신청 마감·결과 발표)의 공통 기준. */
    public static final int CONFIRM_HOUR = 22;

    /** 큐 참가: 신규 대기 항목 생성. id는 영속화 시점에 부여, matchedAt은 매칭 확정 전까지 null. */
    public static MatchingQueue join(Long userId, String gender, LocalDate birthday, LocalDateTime queuedAt) {
        return new MatchingQueue(null, userId, gender, birthday, MatchingQueueStatus.QUEUED, queuedAt, null);
    }

    /** 큐 취소: 상태만 CANCELLED로 전이한 새 인스턴스 반환. */
    public MatchingQueue cancelled() {
        return new MatchingQueue(id, userId, gender, birthday, MatchingQueueStatus.CANCELLED, queuedAt, matchedAt);
    }

    /** 배치 미성사 만료: 상태만 EXPIRED로 전이한 새 인스턴스 반환. */
    public MatchingQueue expired() {
        return new MatchingQueue(id, userId, gender, birthday, MatchingQueueStatus.EXPIRED, queuedAt, matchedAt);
    }

    /** 매칭 확정: MATCHED로 전이하고 확정 시각을 기록한 새 인스턴스 반환. */
    public MatchingQueue matched(LocalDateTime matchedAt) {
        return new MatchingQueue(id, userId, gender, birthday, MatchingQueueStatus.MATCHED, queuedAt, matchedAt);
    }
}
