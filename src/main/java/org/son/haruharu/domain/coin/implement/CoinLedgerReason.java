package org.son.haruharu.domain.coin.implement;

/**
 * 코인 원장 변동 사유.
 */
public enum CoinLedgerReason {
    /** IAP 구매 적립 */
    PURCHASE,
    /** 방 미션 리워드 적립 */
    MISSION_REWARD,
    /** 매칭 진입 소모 */
    MATCH_ENTER,
    /** 매칭 취소·미성사 환불 적립 */
    MATCH_REFUND,
    /** 채팅(방) 열기 소모 */
    CHAT_OPEN,
    /** 환불 회수(PAID 차감) */
    REFUND_REVOKE,
    /** 첫 가입 보너스 적립 */
    SIGNUP_BONUS,
    /** 일일 업로드 슬롯 완주 보상 적립 */
    UPLOAD_COMPLETION_REWARD
}
