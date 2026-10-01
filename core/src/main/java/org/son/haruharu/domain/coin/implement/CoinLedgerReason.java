package org.son.haruharu.domain.coin.implement;

/**
 * 코인 원장 변동 사유.
 */
public enum CoinLedgerReason {
    /** IAP 구매 적립 */
    PURCHASE,
    /** 방 미션 리워드 적립 */
    MISSION_REWARD,
    /** 폐기된 자동매칭 소모. 기존 원장 역직렬화용으로 유지. */
    MATCH_ENTER,
    /** 폐기된 자동매칭 환불. 기존 원장 역직렬화용으로 유지. */
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
