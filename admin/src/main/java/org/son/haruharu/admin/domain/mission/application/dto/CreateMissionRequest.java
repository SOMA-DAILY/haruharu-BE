package org.son.haruharu.admin.domain.mission.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.son.haruharu.admin.domain.mission.business.command.CreateMissionCommand;

public record CreateMissionRequest(
        @Schema(description = "미션 문구", example = "오늘 먹은 가장 맛있는 음식을 보여주세요")
        @NotBlank @Size(max = 500) String description,
        @Schema(description = "완주 포인트(열쇠, 현재 미사용·향후 보상 재개 대비)", example = "5")
        @PositiveOrZero int points
) {
    public CreateMissionCommand toCommand() {
        return new CreateMissionCommand(description, points);
    }
}
