package org.son.haruharu.domain.mission.implement;

import java.time.LocalDateTime;

import org.son.haruharu.domain.mission.implement.exception.InvalidRoomMissionSlotScheduleException;

public record RoomMissionSlot(
        Long id,
        Long roomId,
        int dayNumber,
        MissionPeriod period,
        LocalDateTime opensAt,
        LocalDateTime closesAt,
        LocalDateTime createdAt
) {
    public static RoomMissionSlot of(Long roomId, int dayNumber, MissionPeriod period,
                                     LocalDateTime opensAt, LocalDateTime closesAt) {
        if (roomId == null || dayNumber <= 0 || period == null || opensAt == null || closesAt == null
                || !opensAt.isBefore(closesAt)) {
            throw new InvalidRoomMissionSlotScheduleException();
        }
        return new RoomMissionSlot(null, roomId, dayNumber, period, opensAt, closesAt, null);
    }
}
