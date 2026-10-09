package org.son.haruharu.domain.ticket.implement;

/**
 * 티켓 원장 변동 사유.
 */
public enum TicketLedgerReason {
    /** IAP 구매 적립 */
    PURCHASE,
    /** 티켓 사용 */
    USE,
    /** 환불 회수 */
    REFUND_REVOKE,
    /** 운영진 지급 */
    ADMIN_GRANT
}
