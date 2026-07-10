package org.son.haruharu.admin.domain.room.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.domain.room.implement.RoomStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 방 목록 행. 성비·인원은 room_members에서 집계, 일차는 활성 방만 파생(그 외 0). */
public record AdminRoomSummary(
        @Schema(description = "방 id", example = "1") Long roomId,
        @Schema(description = "방 상태", example = "ACTIVE") RoomStatus status,
        @Schema(description = "예정일", example = "2026-07-09") LocalDate scheduledDate,
        @Schema(description = "현재 일차(활성 방만, 그 외 0)", example = "2") int currentDay,
        @Schema(description = "참여자 수", example = "6") int memberCount,
        @Schema(description = "여성 수", example = "3") int femaleCount,
        @Schema(description = "남성 수", example = "3") int maleCount,
        @Schema(description = "시작 시각(미시작이면 null)", nullable = true) LocalDateTime startedAt
) {
}
