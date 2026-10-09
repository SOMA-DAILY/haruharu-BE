package org.son.haruharu.domain.ticket.business.command;

import org.son.haruharu.domain.ticket.implement.TicketLedgerReason;

/** 티켓 적립 명령. */
public record TicketCreditCommand(
        Long userId,
        long amount,
        TicketLedgerReason reason,
        String refType,
        String refId,
        String memo
) {

    /**
     * 인앱결제(IAP) 구매 적립. 사유(PURCHASE)를 ticket 도메인 내부에서 고정해,
     * 호출 도메인이 ticket 내부 enum을 알 필요 없게 한다.
     */
    public static TicketCreditCommand purchase(Long userId, long amount, String refType, String refId) {
        return new TicketCreditCommand(userId, amount, TicketLedgerReason.PURCHASE, refType, refId, null);
    }

    /** 운영진 지급. 누가 줬는지(refId=어드민 id)와 사유 메모를 원장에 남긴다. */
    public static TicketCreditCommand adminGrant(Long userId, long amount, Long adminId, String memo) {
        return new TicketCreditCommand(userId, amount, TicketLedgerReason.ADMIN_GRANT,
                "ADMIN", String.valueOf(adminId), memo);
    }
}
