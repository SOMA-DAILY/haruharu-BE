package org.son.haruharu.domain.meta.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 정책 상수 메타(인증 불필요). 유저 상태와 무관한 전역 정책 값을 도메인별 네임스페이스로 담는다.
 * 클라이언트 하드코딩 제거용 — 값 변경은 서버 설정만 수정하면 전파된다.
 */
public record MetaResponse(
        @Schema(description = "매칭 정책 상수") MatchingMeta matching
) {
    public record MatchingMeta(
            @Schema(description = "매칭 신청 비용(구름). 차감·미성사 환불이 공유하는 단일 값", example = "20") long enterCost
    ) {}
}
