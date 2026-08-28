package org.son.haruharu.admin.domain.mission.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.domain.mission.implement.Mission;

public record AdminMissionResponse(
        @Schema(description = "미션 id", example = "1") Long id,
        @Schema(description = "현재 revision id", example = "10") Long activeRevisionId,
        @Schema(description = "활성 여부(신규 배정 후보 풀 포함 여부)", example = "true") boolean active,
        AdminMissionRevisionResponse activeRevision
) {
    public static AdminMissionResponse of(Mission mission, AdminMissionRevisionResponse activeRevision) {
        return new AdminMissionResponse(mission.id(), mission.activeRevisionId(), mission.active(), activeRevision);
    }
}
