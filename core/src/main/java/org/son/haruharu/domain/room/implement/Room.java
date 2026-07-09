package org.son.haruharu.domain.room.implement;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public record Room(
        Long id,
        RoomStatus status,
        LocalDate scheduledDate,
        int totalDays,
        LocalDateTime startedAt,
        LocalDateTime endedAt
) {
    /**
     * 세션 결과 발표(최종선택 마감·미매칭 확정) 시각. room 세션의 고유 개념으로,
     * 매칭 큐 확정 시각(MatchingQueue.CONFIRM_HOUR)과 우연히 같은 22시일 뿐 별개다 —
     * 타 도메인 implement 결합을 피하기 위해 room이 소유한다(#143 리뷰 반영).
     */
    public static final int RESULT_ANNOUNCE_HOUR = 22;

    /** 매칭 확정으로 방 생성: scheduledDate(확정일) 당일 시작 예정. 시작 전이므로 PENDING. */
    public static Room create(LocalDate scheduledDate, int totalDays) {
        return new Room(null, RoomStatus.PENDING, scheduledDate, totalDays, null, null);
    }

    /** 방 시작: PENDING → ACTIVE, startedAt 앵커. currentDay는 startedAt에서 파생하므로 저장하지 않는다. */
    public Room started(LocalDateTime startedAt) {
        return new Room(id, RoomStatus.ACTIVE, scheduledDate, totalDays, startedAt, endedAt);
    }

    /** 세션 종료: ACTIVE → ENDED, endedAt 앵커. 종료 후 getActive()·findCurrent 조회에서 제외된다. */
    public Room ended(LocalDateTime endedAt) {
        return new Room(id, RoomStatus.ENDED, scheduledDate, totalDays, startedAt, endedAt);
    }

    /**
     * 현재 Day = min(totalDays, 1 + startedAt~now 경과일). 하루 경계 시각(dayStartHour, 예: 09:00)은
     * business 계층이 UploadSlot.dayStartHour()에서 읽어 주입한다(room 도메인이 mission에 결합되지 않도록).
     * 미시작(PENDING/startedAt 없음)이면 0. 별도 증분 잡 없이 시각에서 파생한다.
     */
    public int currentDayAt(LocalDateTime now, int dayStartHour) {
        if (status != RoomStatus.ACTIVE || startedAt == null) {
            return 0;
        }
        return (int) Math.min(totalDays, rawDayAt(now, dayStartHour));
    }

    /**
     * 마지막 날(totalDays)이 지나 세션이 끝났는지. Day+1의 하루 경계(dayStartHour)를 넘긴 ACTIVE 방이 true.
     * currentDayAt은 totalDays로 캡되어 "경과"를 구분할 수 없으므로 캡 이전 raw day로 판정한다.
     */
    public boolean hasEndedBy(LocalDateTime now, int dayStartHour) {
        if (status != RoomStatus.ACTIVE || startedAt == null) {
            return false;
        }
        return rawDayAt(now, dayStartHour) > totalDays;
    }

    private long rawDayAt(LocalDateTime now, int dayStartHour) {
        long elapsedDays = ChronoUnit.DAYS.between(
                dateOfDay(1, dayStartHour),
                now.minusHours(dayStartHour).toLocalDate());
        return 1 + Math.max(0, elapsedDays);
    }

    /**
     * Day N의 달력 날짜. currentDayAt과 같은 앵커(startedAt - dayStartHour)에서 파생해
     * Day 판정과 특정 시각 판정(예: 최종선택 오픈)이 서로 다른 경계를 쓰지 않게 한다.
     * ACTIVE + startedAt 존재를 전제한다(호출측이 currentDayAt 게이트로 보장).
     */
    public LocalDate dateOfDay(int dayNumber, int dayStartHour) {
        return startedAt.minusHours(dayStartHour).toLocalDate().plusDays(dayNumber - 1L);
    }
}
