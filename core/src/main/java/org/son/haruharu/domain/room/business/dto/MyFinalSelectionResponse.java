package org.son.haruharu.domain.room.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 내 최종선택 제출 상태(방 재진입 시 복원용). 미제출이어도 404가 아닌 submitted=false로 응답한다.
 * 발표 전 결과 유출을 막기 위해 상호매칭 여부(matched)는 담지 않는다.
 */
public record MyFinalSelectionResponse(
        @Schema(description = "최종선택 제출 여부(기권 제출 포함)", example = "true") boolean submitted,
        @Schema(description = "선택한 방 멤버 id(기권·미제출이면 null)", example = "42", nullable = true) Long selectedMemberId,
        @Schema(description = "제출 시각(미제출이면 null)", nullable = true) LocalDateTime selectedAt
) {
    public static MyFinalSelectionResponse notSubmitted() {
        return new MyFinalSelectionResponse(false, null, null);
    }
}
