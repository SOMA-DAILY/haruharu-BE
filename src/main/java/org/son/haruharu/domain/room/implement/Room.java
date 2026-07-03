package org.son.haruharu.domain.room.implement;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public record Room(
        UUID id,
        RoomStatus status,
        LocalDate scheduledDate,
        int totalDays,
        LocalDateTime startedAt,
        LocalDateTime endedAt
) {
    /** 매칭 확정으로 방 생성: scheduledDate(확정일) 당일 시작 예정. 시작 전이므로 PENDING. */
    public static Room create(LocalDate scheduledDate, int totalDays) {
        return new Room(null, RoomStatus.PENDING, scheduledDate, totalDays, null, null);
    }

    /** 방 시작: PENDING → ACTIVE, startedAt 앵커. currentDay는 startedAt에서 파생하므로 저장하지 않는다. */
    public Room started(LocalDateTime startedAt) {
        return new Room(id, RoomStatus.ACTIVE, scheduledDate, totalDays, startedAt, endedAt);
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
        long elapsedDays = ChronoUnit.DAYS.between(
                startedAt.minusHours(dayStartHour).toLocalDate(),
                now.minusHours(dayStartHour).toLocalDate());
        long day = 1 + Math.max(0, elapsedDays);
        return (int) Math.min(totalDays, day);
    }
}
