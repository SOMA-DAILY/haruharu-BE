package org.son.haruharu.domain.matching.implement;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record MatchingQueue(
        Long id,
        Long userId,
        String gender,
        LocalDate birthday,
        MatchingQueueStatus status,
        LocalDateTime queuedAt,
        LocalDateTime matchedAt
) {
    /** 코인 원장 참조 타입. 차감·환불 ledger의 ref_type으로 기록된다. */
    public static final String COIN_REF_TYPE = "MATCHING_QUEUE";

    /** 매칭 확정(결과 발표) 시각(시). MatchingScheduler의 확정 cron과 홈 결과 발표 카운트다운의 기준. */
    public static final int CONFIRM_HOUR = 22;

    /** 신청 마감 시각(시). 매일 00:00~21:00만 신청 가능하고, 21:00부터 마감된다(다음날 00:00 자동 재개). */
    public static final int APPLY_DEADLINE_HOUR = 21;

    /** 지금 신청 가능 시간대인가. 00:00~20:59:59.…까지 허용, 21:00부터 마감. 00:00에 hour<21이 되어 자동 재개된다. */
    public static boolean isApplyOpen(LocalTime now) {
        return now.isBefore(LocalTime.of(APPLY_DEADLINE_HOUR, 0));
    }

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
