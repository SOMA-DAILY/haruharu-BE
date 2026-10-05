package org.son.haruharu.domain.mission.implement;

import java.time.LocalDateTime;
import org.son.haruharu.domain.mission.implement.exception.InvalidOperatorMissionScheduleException;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomStatus;

public final class OperatorMissionSchedule {
    private OperatorMissionSchedule() {}

    public static void validate(Room room, LocalDateTime scheduledAt, LocalDateTime now) {
        if (scheduledAt == null) {
            if (room.statusAt(now) != RoomStatus.ACTIVE) throw new InvalidOperatorMissionScheduleException();
            return;
        }
        if (!scheduledAt.isAfter(now) || room.statusAt(now) == RoomStatus.CANCELLED
                || room.startsAt() == null || room.finalSelectionOpensAt() == null
                || scheduledAt.isBefore(room.startsAt())
                || !scheduledAt.isBefore(room.finalSelectionOpensAt())
                || room.statusAt(scheduledAt) != RoomStatus.ACTIVE) {
            throw new InvalidOperatorMissionScheduleException();
        }
    }
}
