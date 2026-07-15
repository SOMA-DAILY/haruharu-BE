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

    /** 신청 마감 시각(시). 당일 22:00 배치에 포함되려면 이 시각(21:00) 전에 신청해야 한다. 홈 카운트다운(applyDeadlineAt) 기준. */
    public static final int APPLY_DEADLINE_HOUR = 21;

    /**
     * 지금 신청 가능 시간대인가. 발표 시간대(21:00~22:00)만 차단하고 그 외 전 시간은 허용한다(#229).
     * 즉 now &lt; 21:00 또는 now &gt;= 22:00이면 허용. 21:00 정각 차단 시작, 22:00 정각 허용 재개.
     * (22:00 이후 신청은 당일 배치를 지나 다음 22:00 배치 대상이 된다.)
     */
    public static boolean isApplyOpen(LocalTime now) {
        return now.isBefore(LocalTime.of(APPLY_DEADLINE_HOUR, 0))
            || !now.isBefore(LocalTime.of(CONFIRM_HOUR, 0));
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
