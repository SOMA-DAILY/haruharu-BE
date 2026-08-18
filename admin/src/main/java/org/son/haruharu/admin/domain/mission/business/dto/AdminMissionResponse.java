package org.son.haruharu.admin.domain.mission.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.domain.mission.implement.Mission;

public record AdminMissionResponse(
        @Schema(description = "미션 id", example = "1") Long id,
        @Schema(description = "미션 문구", example = "오늘 먹은 가장 맛있는 음식을 보여주세요") String description,
        @Schema(description = "완주 포인트(열쇠, 현재 미사용)", example = "5") int points,
        @Schema(description = "활성 여부(배정 후보 풀 포함 여부)", example = "true") boolean active
) {
    public static AdminMissionResponse from(Mission mission) {
        return new AdminMissionResponse(mission.id(), mission.description(), mission.points(), mission.isActive());
    }
}
