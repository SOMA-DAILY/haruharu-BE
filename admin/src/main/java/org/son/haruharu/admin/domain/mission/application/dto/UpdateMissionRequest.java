package org.son.haruharu.admin.domain.mission.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.son.haruharu.admin.domain.mission.business.command.UpdateMissionCommand;

/** 부분 수정 — 보낸 필드만 갱신. */
public record UpdateMissionRequest(
        // 보내면 갱신, 안 보내면 유지. 보낼 경우 빈 문자열로 덮어쓰지 못하게 막는다.
        @Schema(description = "미션 문구", example = "지금 보이는 풍경을 찍어 공유해요")
        @Size(min = 1, max = 500) String description,
        @Schema(description = "완주 포인트", example = "5") @PositiveOrZero Integer points,
        @Schema(description = "활성 여부(false면 배정 후보에서 제외 = 소프트 삭제)", example = "true") Boolean active
) {
    public UpdateMissionCommand toCommand() {
        return new UpdateMissionCommand(description, points, active);
    }
}
