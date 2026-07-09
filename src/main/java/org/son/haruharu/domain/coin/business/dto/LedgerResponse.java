package org.son.haruharu.domain.coin.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.domain.coin.implement.CoinBucket;
import org.son.haruharu.domain.coin.implement.CoinLedger;
import org.son.haruharu.domain.coin.implement.CoinLedgerReason;

import java.time.LocalDateTime;

public record LedgerResponse(
        @Schema(description = "원장 항목 id(커서로 사용)", example = "1024") Long id,
        @Schema(description = "코인 증감량(+적립 / -소모)", example = "100") long delta,
        @Schema(description = "이동한 버킷") CoinBucket bucket,
        @Schema(description = "이동 사유") CoinLedgerReason reason,
        @Schema(description = "출처 유형(예: IAP_PURCHASE, MATCHING_QUEUE)", example = "IAP_PURCHASE") String refType,
        @Schema(description = "출처 식별자(구매 id·큐 id 등, 다형 참조)") String refId,
        @Schema(description = "기록 시각") LocalDateTime createdAt
) {
    public static LedgerResponse from(CoinLedger ledger) {
        return new LedgerResponse(
                ledger.id(),
                ledger.delta(),
                ledger.bucket(),
                ledger.reason(),
                ledger.refType(),
                ledger.refId(),
                ledger.createdAt()
        );
    }
}
