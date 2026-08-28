package org.son.haruharu.domain.mission.implement;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.son.haruharu.domain.mission.implement.exception.InvalidRoomMissionSlotScheduleException;

public final class RoomMissionSlotSchedule {

    private static final int PERIODS_PER_DAY = 2;
    private static final int HOURS_PER_DAY = 24;
    private static final int MORNING_OPEN_OFFSET_HOURS = 3;
    private static final int MORNING_CLOSE_OFFSET_HOURS = 8;
    private static final int AFTERNOON_OPEN_OFFSET_HOURS = 9;
    private static final int AFTERNOON_CLOSE_OFFSET_HOURS = 14;

    private RoomMissionSlotSchedule() {
    }

    public static List<RoomMissionSlot> validate(List<RoomMissionSlot> slots, int totalDays,
                                                  LocalDateTime startsAt,
                                                  LocalDateTime finalSelectionOpensAt) {
        if (slots == null || totalDays <= 0 || startsAt == null || finalSelectionOpensAt == null
                || !startsAt.isBefore(finalSelectionOpensAt)
                || slots.size() != totalDays * PERIODS_PER_DAY) {
            throw new InvalidRoomMissionSlotScheduleException();
        }

        Set<SlotKey> keys = new HashSet<>();
        Long roomId = null;
        for (RoomMissionSlot slot : slots) {
            if (slot == null || slot.roomId() == null || slot.dayNumber() < 1 || slot.dayNumber() > totalDays
                    || slot.period() == null || slot.opensAt() == null || slot.closesAt() == null
                    || !slot.opensAt().isBefore(slot.closesAt())
                    || slot.opensAt().isBefore(startsAt)
                    || slot.closesAt().isAfter(finalSelectionOpensAt)
                    || !keys.add(new SlotKey(slot.dayNumber(), slot.period()))) {
                throw new InvalidRoomMissionSlotScheduleException();
            }
            if (roomId == null) {
                roomId = slot.roomId();
            } else if (!roomId.equals(slot.roomId())) {
                throw new InvalidRoomMissionSlotScheduleException();
            }
        }

        List<RoomMissionSlot> ordered = slots.stream()
                .sorted(Comparator.comparingInt(RoomMissionSlot::dayNumber)
                        .thenComparing(RoomMissionSlot::period))
                .toList();
        for (int index = 1; index < ordered.size(); index++) {
            if (ordered.get(index).opensAt().isBefore(ordered.get(index - 1).closesAt())) {
                throw new InvalidRoomMissionSlotScheduleException();
            }
        }
        return List.copyOf(ordered);
    }

    public static List<RoomMissionSlot> defaults(Long roomId, int totalDays, LocalDateTime startsAt,
                                                  LocalDateTime finalSelectionOpensAt) {
        List<RoomMissionSlot> slots = new ArrayList<>();
        for (int day = 0; day < totalDays; day++) {
            long dayOffset = (long) day * HOURS_PER_DAY;
            slots.add(RoomMissionSlot.of(roomId, day + 1, MissionPeriod.MORNING,
                    startsAt.plusHours(dayOffset + MORNING_OPEN_OFFSET_HOURS),
                    startsAt.plusHours(dayOffset + MORNING_CLOSE_OFFSET_HOURS)));
            slots.add(RoomMissionSlot.of(roomId, day + 1, MissionPeriod.AFTERNOON,
                    startsAt.plusHours(dayOffset + AFTERNOON_OPEN_OFFSET_HOURS),
                    startsAt.plusHours(dayOffset + AFTERNOON_CLOSE_OFFSET_HOURS)));
        }
        return validate(slots, totalDays, startsAt, finalSelectionOpensAt);
    }

    private record SlotKey(int dayNumber, MissionPeriod period) {
    }
}
