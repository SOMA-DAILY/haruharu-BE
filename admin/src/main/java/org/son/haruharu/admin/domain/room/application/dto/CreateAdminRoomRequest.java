package org.son.haruharu.admin.domain.room.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.son.haruharu.admin.domain.mission.common.application.dto.RoomMissionRequest;
import org.son.haruharu.admin.domain.room.business.command.CreateAdminRoomCommand;
import org.son.haruharu.common.ApiFieldError;
import org.son.haruharu.domain.mission.common.business.command.RoomMissionCommand;
import org.son.haruharu.domain.mission.common.implement.exception.InvalidMissionException;

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
        @Schema(description = "초대코드로 들어오는 인원(웹 몫) 정원(null이면 무제한). 앱 신청자 배정 인원은 세지 않는다(#513)",
                example = "6", nullable = true)
        @Positive Integer maxUses,
        @Schema(description = "초대코드 만료 시각(null이면 무기한)", nullable = true)
        LocalDateTime inviteExpiresAt,
        @Schema(description = "초기 미션 목록(선택). 서버 기본값 없음(비면 0개). "
                + "각 미션의 opens_at 필수.", nullable = true)
        List<RoomMissionRequest> missions,
        @Schema(description = "기수 id(#513). 웹 전용 방이면 null", example = "4", nullable = true)
        Long cohortId,
        @Schema(description = "운영진용 방 이름(#513, 50자 이하)", example = "서울 6기 1조", nullable = true)
        @Size(max = 50) String name,
        @Schema(description = "남성 정원(#513, 방 전체 기준). null이면 정하지 않음", example = "3", nullable = true)
        @Positive Integer maleCapacity,
        @Schema(description = "여성 정원(#513, 방 전체 기준). null이면 정하지 않음", example = "3", nullable = true)
        @Positive Integer femaleCapacity,
        @Schema(hidden = true)
        @com.fasterxml.jackson.annotation.JsonProperty("chat_schedule")
        @jakarta.validation.constraints.Null(message = "chat_schedule은 폐기되었습니다. missions를 사용해 주세요")
        Object retiredChatSchedule,
        @Schema(description = "테스트방 여부. 생략하면 운영방(false).", defaultValue = "false")
        Boolean isTest
) {
    public CreateAdminRoomCommand toCommand() {
        return new CreateAdminRoomCommand(startsAt, finalSelectionOpensAt, finalSelectionClosesAt,
                resultAt, endsAt, totalDays, maxUses, inviteExpiresAt,
                missionCommands(), cohortId, name, maleCapacity, femaleCapacity, Boolean.TRUE.equals(isTest));
    }

    private List<RoomMissionCommand> missionCommands() {
        if (missions == null) return List.of();
        List<RoomMissionCommand> commands = new ArrayList<>();
        List<ApiFieldError> errors = new ArrayList<>();
        for (int i = 0; i < missions.size(); i++) {
            try {
                var request = missions.get(i);
                commands.add(request == null ? null : request.toCommand());
            } catch (InvalidMissionException e) {
                String prefix = "missions[" + i + "].";
                e.getFieldErrors().forEach(error -> errors.add(new ApiFieldError(prefix + error.field(), error.message())));
            }
        }
        if (!errors.isEmpty()) throw new InvalidMissionException(errors);
        return commands;
    }
}
