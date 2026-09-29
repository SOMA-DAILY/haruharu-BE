package org.son.haruharu.admin.domain.room.business.command;

import java.time.LocalDateTime;
import java.util.List;

import org.son.haruharu.domain.mission.business.command.CreateRoomMissionSlotCommand;
import org.son.haruharu.domain.room.business.command.CreateRoomCommand;
import org.son.haruharu.domain.roomschedule.business.command.ScheduleItemCommand;

/**
 * 어드민 방 생성 입력(#318). 스케줄 시각 5개 + totalDays 는 core 방 생성으로,
 * maxUses(실제 방 정원, null이면 무제한)·expiresAt 은 코드 발급으로 간다.
 * 시각 순서 검증은 core RoomService 가 소유한다(어드민이 중복 검증하지 않는다).
 */
public record CreateAdminRoomCommand(
        LocalDateTime startsAt,
        LocalDateTime finalSelectionOpensAt,
        LocalDateTime finalSelectionClosesAt,
        LocalDateTime resultAt,
        LocalDateTime endsAt,
        int totalDays,
        Integer maxUses,
        LocalDateTime inviteExpiresAt,
        List<CreateRoomMissionSlotCommand> missionSlots,
        // 채팅 편성(#457). 비면 0줄.
        List<ScheduleItemCommand> chatSchedule
) {
    /** 편성 이전(#457) 9-arg 호환. 편성 없음. */
    public CreateAdminRoomCommand(LocalDateTime startsAt, LocalDateTime finalSelectionOpensAt,
                                  LocalDateTime finalSelectionClosesAt, LocalDateTime resultAt, LocalDateTime endsAt,
                                  int totalDays, Integer maxUses, LocalDateTime inviteExpiresAt,
                                  List<CreateRoomMissionSlotCommand> missionSlots) {
        this(startsAt, finalSelectionOpensAt, finalSelectionClosesAt, resultAt, endsAt, totalDays, maxUses,
                inviteExpiresAt, missionSlots, List.of());
    }

    public CreateRoomCommand toRoomCommand() {
        return new CreateRoomCommand(startsAt, finalSelectionOpensAt, finalSelectionClosesAt,
                resultAt, endsAt, totalDays);
    }
}
