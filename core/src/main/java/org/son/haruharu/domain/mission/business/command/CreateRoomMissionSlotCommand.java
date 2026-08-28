package org.son.haruharu.domain.mission.business.command;

import java.time.LocalDateTime;

import org.son.haruharu.domain.mission.implement.MissionPeriod;

public record CreateRoomMissionSlotCommand(
        int dayNumber,
        MissionPeriod period,
        LocalDateTime opensAt,
        LocalDateTime closesAt
) {
}
