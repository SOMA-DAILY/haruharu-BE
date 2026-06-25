package org.son.haruharu.domain.room.implement;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record Room(
        UUID id,
        RoomStatus status,
        LocalDate scheduledDate,
        int totalDays,
        int currentDay,
        LocalDateTime startedAt,
        LocalDateTime endedAt
) {
    /** 매칭 확정으로 방 생성: scheduledDate(확정일) 당일 시작 예정. 시작 전이므로 PENDING. */
    public static Room create(LocalDate scheduledDate, int totalDays) {
        return new Room(null, RoomStatus.PENDING, scheduledDate, totalDays, 0, null, null);
    }

    /** 방 시작: PENDING → ACTIVE, 첫째 날(currentDay=1)로 진입. */
    public Room started(LocalDateTime startedAt) {
        return new Room(id, RoomStatus.ACTIVE, scheduledDate, totalDays, 1, startedAt, endedAt);
    }
}
