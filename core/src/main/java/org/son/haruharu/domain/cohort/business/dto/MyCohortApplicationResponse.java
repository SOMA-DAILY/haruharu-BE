package org.son.haruharu.domain.cohort.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 내 최근 신청과 다음 신청서에 미리 채울 값. */
public record MyCohortApplicationResponse(
        @Schema(description = "가장 최근 신청(닫힌 신청 포함). 없으면 null", nullable = true)
        CohortApplicationResponse application,
        @Schema(description = "신청서 입력칸 기본값. 항상 내려가고 칸마다 null 일 수 있다")
        ApplicationDefaultsResponse defaults
) {
}
