package org.son.haruharu.admin.domain.room.application.dto;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.son.haruharu.admin.domain.room.business.command.CreateAdminRoomCommand;
import org.son.haruharu.domain.mission.business.command.CreateRoomMissionSlotCommand;
import org.son.haruharu.domain.mission.implement.MissionPeriod;

/**
 * 방 생성 요청(#318). 시각 5개는 운영진이 방마다 직접 입력한다(9시 고정 폐기).
 * 시각 <b>순서</b> 검증은 core RoomService 가 담당하므로 여기선 필수 여부만 본다.
 */
public record CreateAdminRoomRequest(
        @Schema(description = "방 시작(영상 슬롯 기준 앵커)", example = "2026-08-20T19:00:00")
        @NotNull LocalDateTime startsAt,
        @Schema(description = "최종선택 오픈", example = "2026-08-22T18:00:00")
        @NotNull LocalDateTime finalSelectionOpensAt,
        @Schema(description = "최종선택 마감", example = "2026-08-22T21:00:00")
        @NotNull LocalDateTime finalSelectionClosesAt,
        @Schema(description = "결과 동시 공개", example = "2026-08-22T22:00:00")
        @NotNull LocalDateTime resultAt,
        @Schema(description = "방 종료", example = "2026-08-23T09:00:00")
        @NotNull LocalDateTime endsAt,
        @Schema(description = "총 일수", example = "3")
        @Positive int totalDays,
        @Schema(description = "관리자가 정하는 실제 방 정원(null이면 무제한)", example = "6", nullable = true)
        @Positive Integer maxUses,
        @Schema(description = "초대코드 만료 시각(null이면 무기한)", nullable = true)
        LocalDateTime inviteExpiresAt,
        @Schema(description = "미션 사용 여부. true면 mission_slots 필수, false면 mission_slots를 보내지 않는다.",
                example = "true")
        @NotNull Boolean missionEnabled,
        @Schema(description = "Day별 오전·오후 미션 슬롯. mission_enabled=true일 때 total_days × 2개를 전달한다.",
                nullable = true)
        List<@NotNull @Valid MissionSlotRequest> missionSlots,
        @Schema(description = "채팅 편성(#457, 선택). 어드민 화면 기본 5줄을 고친 그대로 저장된다. 서버 기본값 없음(비면 0줄). "
                + "줄마다 run_at 필수, 시각 제한 없음.", nullable = true)
        List<ScheduleItemRequest> chatSchedule
) {
    public CreateAdminRoomCommand toCommand() {
        return new CreateAdminRoomCommand(startsAt, finalSelectionOpensAt, finalSelectionClosesAt,
                resultAt, endsAt, totalDays, maxUses, inviteExpiresAt, missionEnabled,
                missionSlots == null ? null : missionSlots.stream().map(MissionSlotRequest::toCommand).toList(),
                chatSchedule == null ? List.of()
                        : chatSchedule.stream().filter(java.util.Objects::nonNull).map(ScheduleItemRequest::toCommand).toList());
    }

    public record MissionSlotRequest(
            @Schema(description = "방 진행 일차", example = "1")
            @Positive int dayNumber,
            @Schema(description = "일차 내 시간대", example = "MORNING")
            @NotNull MissionPeriod period,
            @Schema(description = "카드 노출 시작", example = "2026-08-20T21:00:00")
            @NotNull LocalDateTime opensAt,
            @Schema(description = "카드 노출 종료", example = "2026-08-21T02:00:00")
            @NotNull LocalDateTime closesAt
    ) {
        public CreateRoomMissionSlotCommand toCommand() {
            return new CreateRoomMissionSlotCommand(dayNumber, period, opensAt, closesAt);
        }
    }
}
