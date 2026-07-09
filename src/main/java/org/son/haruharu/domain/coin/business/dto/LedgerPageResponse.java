package org.son.haruharu.domain.coin.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record LedgerPageResponse(
        @Schema(description = "코인 내역 항목(최신순)") List<LedgerResponse> items,
        @Schema(description = "다음 페이지 조회에 넘길 커서(마지막 항목 id). 다음 페이지가 없으면 null", example = "995")
        String nextCursor
) {
}
