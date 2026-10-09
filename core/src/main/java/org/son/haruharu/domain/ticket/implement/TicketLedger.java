package org.son.haruharu.domain.ticket.implement;

import java.time.LocalDateTime;

/**
 * 티켓 원장 항목. append-only — 모든 티켓 이동의 단일 진실원천.
 * delta는 +적립 / -차감. balanceAfter는 이 항목 적용 직후의 잔액 스냅샷 — 이용내역 화면의 거래별 잔액 표시용.
 * memo는 운영진 지급 사유처럼 내부에서만 보는 기록이다(유저 응답에 싣지 않는다).
 */
public record TicketLedger(
        Long id,
        Long userId,
        long delta,
        long balanceAfter,
        TicketLedgerReason reason,
        String refType,
        String refId,
        String memo,
        LocalDateTime createdAt
) {

    public static TicketLedger of(
            Long userId,
            long delta,
            long balanceAfter,
            TicketLedgerReason reason,
            String refType,
            String refId,
            String memo,
            LocalDateTime createdAt
    ) {
        return new TicketLedger(null, userId, delta, balanceAfter, reason, refType, refId, memo, createdAt);
    }
}
