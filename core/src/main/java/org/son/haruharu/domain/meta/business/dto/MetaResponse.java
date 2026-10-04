package org.son.haruharu.domain.meta.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 정책 상수 메타(인증 불필요). 유저 상태와 무관한 전역 정책 값을 도메인별 네임스페이스로 담는다.
 * 클라이언트 하드코딩 제거용 — 값 변경은 서버 설정만 수정하면 전파된다.
 */
public record MetaResponse(
        @Schema(description = "세션(소개팅방) 정책 상수") SessionMeta session
) {
    public record SessionMeta(
            @Schema(description = "하루 시작 시각(시). 방 시작·하루 경계가 이 시각 기준", example = "9") int dayStartHour,
            @Schema(description = "세션 일수", example = "3") int totalDays
    ) {}
}
