package org.son.haruharu.domain.ticket.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.domain.ticket.implement.TicketLedger;
import org.son.haruharu.domain.ticket.implement.TicketLedgerReason;

import java.time.LocalDateTime;

public record LedgerResponse(
        @Schema(description = "원장 항목 id(커서로 사용)", example = "1024") Long id,
        @Schema(description = "티켓 증감량(+적립 / -차감)", example = "1") long delta,
        @Schema(description = "티켓 증감량 이후 잔액", example = "2") long balanceAfter,
        @Schema(description = "이동 사유") TicketLedgerReason reason,
        @Schema(description = "출처 유형(예: IAP_PURCHASE). 운영 지급은 null", nullable = true, example = "IAP_PURCHASE") String refType,
        @Schema(description = "출처 식별자(구매 id 등, 다형 참조). 운영 지급은 null", nullable = true) String refId,
        @Schema(description = "기록 시각") LocalDateTime createdAt
) {

    public static LedgerResponse from(TicketLedger ledger) {
        // 운영진 지급의 ref 는 지급한 어드민 id 라 유저에게 내려보내지 않는다.
        boolean internalRef = ledger.reason() == TicketLedgerReason.ADMIN_GRANT;
        return new LedgerResponse(
                ledger.id(),
                ledger.delta(),
                ledger.balanceAfter(),
                ledger.reason(),
                internalRef ? null : ledger.refType(),
                internalRef ? null : ledger.refId(),
                ledger.createdAt()
        );
    }
}
