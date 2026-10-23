package org.son.haruharu.domain.chat.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import org.son.haruharu.domain.chat.implement.PairChatWindow;

/** 방의 1:1 대화 시간 상태(#540). 앱은 프로필 시트의 1:1 아이콘을 켜거나(OPEN) 잠그거나(SCHEDULED) 숨긴다(NONE). */
public record PairChatWindowResponse(
        @Schema(description = "OPEN=지금 열림, SCHEDULED=다음 시간 있음, NONE=남은 시간 없음", example = "SCHEDULED")
        String state,
        @Schema(description = "OPEN 이면 지금 시간의 시작, SCHEDULED 면 다음 시간의 시작", nullable = true) LocalDateTime opensAt,
        @Schema(description = "opens_at 의 방 일차(1부터)", example = "1", nullable = true) Integer opensDay,
        @Schema(description = "그 대화 시간의 끝", nullable = true) LocalDateTime closesAt
) {
    public static PairChatWindowResponse from(PairChatWindow window) {
        return new PairChatWindowResponse(window.state().name(), window.opensAt(), window.opensDay(), window.closesAt());
    }
}
