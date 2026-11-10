package org.son.haruharu.admin.domain.room.business.command;

import org.son.haruharu.domain.mission.common.business.command.RoomMissionCommand;
import org.son.haruharu.domain.room.business.command.CreateRoomCommand;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 어드민 방 생성 입력(#318). 스케줄 시각 5개 + totalDays 는 core 방 생성으로, maxUses(초대코드 입장 정원, null이면 무제한)·expiresAt
 * 은 코드 발급으로 간다. 시각 순서 검증은 core RoomService 가 소유한다(어드민이 중복 검증하지 않는다).
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
        Long groupId,
        Boolean isTest,
        // #560: 방 단위 영상 답장 추천 스위치. null 이면 false.
        Boolean replyRecommendationEnabled) {
    /** 답장 추천 스위치 없이 만드는 방(#560 이전 모양). 스위치는 꺼짐. */
    public CreateAdminRoomCommand(
            LocalDateTime startsAt,
            LocalDateTime finalSelectionOpensAt,
            LocalDateTime finalSelectionClosesAt,
            LocalDateTime resultAt,
            LocalDateTime endsAt,
            int totalDays,
            Integer maxUses,
            LocalDateTime inviteExpiresAt,
            List<RoomMissionCommand> missions,
            Long cohortId,
            String name,
            Integer maleCapacity,
            Integer femaleCapacity,
            Long groupId,
            Boolean isTest) {
        this(
                startsAt,
                finalSelectionOpensAt,
                finalSelectionClosesAt,
                resultAt,
                endsAt,
                totalDays,
                maxUses,
                inviteExpiresAt,
                missions,
                cohortId,
                name,
                maleCapacity,
                femaleCapacity,
                groupId,
                isTest,
                null);
    }

    public CreateAdminRoomCommand(
            LocalDateTime a,
            LocalDateTime b,
            LocalDateTime c,
            LocalDateTime d,
            LocalDateTime e,
            int days,
            Integer max,
            LocalDateTime expiry,
            List<RoomMissionCommand> missions,
            Long cohort,
            String name,
            Integer male,
            Integer female) {
        this(a, b, c, d, e, days, max, expiry, missions, cohort, name, male, female, null, null);
    }

    /** 편성 정보 없이 만드는 방(#513 이전 모양). */
    public CreateAdminRoomCommand(
            LocalDateTime startsAt,
            LocalDateTime finalSelectionOpensAt,
            LocalDateTime finalSelectionClosesAt,
            LocalDateTime resultAt,
            LocalDateTime endsAt,
            int totalDays,
            Integer maxUses,
            LocalDateTime inviteExpiresAt,
            List<RoomMissionCommand> missions) {
        this(
                startsAt,
                finalSelectionOpensAt,
                finalSelectionClosesAt,
                resultAt,
                endsAt,
                totalDays,
                maxUses,
                inviteExpiresAt,
                missions,
                null,
                null,
                null,
                null);
    }

    public CreateRoomCommand toRoomCommand() {
        return new CreateRoomCommand(
                startsAt,
                finalSelectionOpensAt,
                finalSelectionClosesAt,
                resultAt,
                endsAt,
                totalDays,
                cohortId,
                name,
                maleCapacity,
                femaleCapacity,
                Boolean.TRUE.equals(replyRecommendationEnabled));
    }
}
