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
    ADMIN_GRANT,
    /** 사용 반환. 쓴 참가권을 돌려줌(기수 신청 배정 전 취소·미배정 종료, #511). 스토어 환불 회수(REFUND_REVOKE)와 다르다 */
    USE_REFUND
}
