package org.son.haruharu.domain.room.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 확인 대기 중인 최근 매칭 결과(홈 자동 노출용). 가장 최근 방의 결과 발표(Day3 22:00) 이후부터
 * 다음 매칭(새 방 배정) 전까지 유지되어, 방 ENDED 이후에도 홈에서 결과 화면을 띄울 수 있다.
 * "방마다 한 번만 노출"은 클라이언트가 room_id 기준 로컬 seen 플래그로 처리한다(서버 ack 없음).
 */
public record PendingMatchResultResponse(
        @Schema(description = "결과가 발표된 방 id", example = "12") Long roomId,
        @Schema(description = "매칭 결과") Outcome outcome,
        @Schema(description = "성사 시 자동 생성된 채팅방 id(FAILED거나 이벤트 처리 전이면 null)",
                example = "34", nullable = true) Long chatRoomId,
        @Schema(description = "매칭 상대 닉네임(FAILED면 null)", example = "하루", nullable = true) String partnerNickname,
        @Schema(description = "결과 발표 시각(Day3 22:00)") LocalDateTime announcedAt
) {
    /** MATCHED=상호 최종선택 성사, FAILED=미성사(미제출·기권 포함). */
    public enum Outcome { MATCHED, FAILED }

    public static PendingMatchResultResponse failed(Long roomId, LocalDateTime announcedAt) {
        return new PendingMatchResultResponse(roomId, Outcome.FAILED, null, null, announcedAt);
    }
}
