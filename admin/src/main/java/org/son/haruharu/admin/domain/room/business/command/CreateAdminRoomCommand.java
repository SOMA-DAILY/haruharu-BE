package org.son.haruharu.admin.domain.room.business.command;

import java.time.LocalDateTime;
import java.util.List;
import org.son.haruharu.domain.mission.common.business.command.RoomMissionCommand;
import org.son.haruharu.domain.room.business.command.CreateRoomCommand;

/**
 * 어드민 방 생성 입력(#318). 스케줄 시각 5개 + totalDays 는 core 방 생성으로,
 * maxUses(초대코드 입장 정원, null이면 무제한)·expiresAt 은 코드 발급으로 간다.
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
        List<RoomMissionCommand> missions,
        // 편성 정보(#513). 모두 선택 — 검증은 core(RoomComposition·RoomService)가 한다.
        Long cohortId,
        String name,
        Integer maleCapacity,
        Integer femaleCapacity,
        boolean isTest
) {
    public CreateAdminRoomCommand(LocalDateTime startsAt, LocalDateTime finalSelectionOpensAt,
                                  LocalDateTime finalSelectionClosesAt, LocalDateTime resultAt,
                                  LocalDateTime endsAt, int totalDays, Integer maxUses,
                                  LocalDateTime inviteExpiresAt, List<RoomMissionCommand> missions,
                                  Long cohortId, String name, Integer maleCapacity, Integer femaleCapacity) {
        this(startsAt, finalSelectionOpensAt, finalSelectionClosesAt, resultAt, endsAt, totalDays,
                maxUses, inviteExpiresAt, missions, cohortId, name, maleCapacity, femaleCapacity, false);
    }

    /** 편성 정보 없이 만드는 방(#513 이전 모양). */
    public CreateAdminRoomCommand(LocalDateTime startsAt, LocalDateTime finalSelectionOpensAt,
                                  LocalDateTime finalSelectionClosesAt, LocalDateTime resultAt,
                                  LocalDateTime endsAt, int totalDays, Integer maxUses,
                                  LocalDateTime inviteExpiresAt, List<RoomMissionCommand> missions) {
        this(startsAt, finalSelectionOpensAt, finalSelectionClosesAt, resultAt, endsAt, totalDays, maxUses,
                inviteExpiresAt, missions, null, null, null, null, false);
    }

    public CreateRoomCommand toRoomCommand() {
        return new CreateRoomCommand(startsAt, finalSelectionOpensAt, finalSelectionClosesAt,
                resultAt, endsAt, totalDays, cohortId, name, maleCapacity, femaleCapacity, isTest);
    }
}
