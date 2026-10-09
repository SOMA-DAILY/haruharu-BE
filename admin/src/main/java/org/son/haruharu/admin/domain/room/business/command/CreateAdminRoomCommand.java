package org.son.haruharu.admin.domain.room.business.command;

import java.time.LocalDateTime;
import java.util.List;
import org.son.haruharu.domain.mission.common.business.command.RoomMissionCommand;
import org.son.haruharu.domain.room.business.command.CreateRoomCommand;

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
        // 초기 미션(#486). 비면 0개.
        List<RoomMissionCommand> missions
) {
    public CreateRoomCommand toRoomCommand() {
        return new CreateRoomCommand(startsAt, finalSelectionOpensAt, finalSelectionClosesAt,
                resultAt, endsAt, totalDays);
    }
}
