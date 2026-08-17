package org.son.haruharu.domain.mission.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.domain.mission.implement.Mission;
import org.son.haruharu.domain.mission.implement.RoomMission;

import java.time.LocalDateTime;

/** 방의 현재 열린 미션 카드. JSON은 전역 SNAKE_CASE. */
public record RoomMissionResponse(
        @Schema(description = "방 미션 인스턴스 id", example = "5") Long roomMissionId,
        @Schema(description = "미션 문구", example = "오늘 먹은 가장 맛있는 음식을 보여주세요") String description,
        @Schema(description = "미션 오픈 시각") LocalDateTime opensAt,
        @Schema(description = "미션 마감 시각(무기한이면 null)", nullable = true) LocalDateTime closesAt,
        @Schema(description = "방 내 순번", example = "0") int sequence
) {
    public static RoomMissionResponse of(RoomMission roomMission, Mission mission) {
        return new RoomMissionResponse(
                roomMission.id(), mission.description(),
                roomMission.opensAt(), roomMission.closesAt(), roomMission.sequence());
    }
}
