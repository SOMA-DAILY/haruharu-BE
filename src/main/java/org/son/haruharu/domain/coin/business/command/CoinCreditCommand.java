package org.son.haruharu.domain.coin.business.command;

import org.son.haruharu.domain.coin.implement.CoinBucket;
import org.son.haruharu.domain.coin.implement.CoinLedgerReason;

/** 코인 적립 명령. 지정 버킷에 amount만큼 적립한다. */
public record CoinCreditCommand(
        Long userId,
        CoinBucket bucket,
        long amount,
        CoinLedgerReason reason,
        String refType,
        String refId
) {
    /**
     * 인앱결제(IAP) 구매 적립. 버킷(PAID)·사유(PURCHASE)를 coin 도메인 내부에서 고정해,
     * 호출 도메인이 coin 내부 enum을 알 필요 없게 한다.
     */
    public static CoinCreditCommand purchase(Long userId, long amount, String refType, String refId) {
        return new CoinCreditCommand(userId, CoinBucket.PAID, amount, CoinLedgerReason.PURCHASE, refType, refId);
    }

    /**
     * 매칭 취소·미성사 환불 적립. 소모는 리워드 우선이므로 환불도 REWARD 버킷으로 되돌린다
     * (PAID 적립 시 실결제 환불 회수(revoke) 대상 잔액이 부풀려지는 것을 막는다).
     */
    public static CoinCreditCommand matchRefund(Long userId, long amount, String refType, String refId) {
        return new CoinCreditCommand(userId, CoinBucket.REWARD, amount, CoinLedgerReason.MATCH_REFUND, refType, refId);
    }

    /**
     * 첫 가입 보너스 적립. 리워드 버킷(환불 회수 무관)으로 적립하고, refType/refId로 유저당 1회 멱등을 보장한다.
     */
    public static CoinCreditCommand signupBonus(Long userId, long amount) {
        return new CoinCreditCommand(userId, CoinBucket.REWARD, amount, CoinLedgerReason.SIGNUP_BONUS,
                "SIGNUP_BONUS", String.valueOf(userId));
    }

    /**
     * 일일 업로드 완주 보상 적립. 리워드 버킷으로 적립한다. 멱등은 호출측(daily_upload_reward 테이블)이 보장한다.
     * refId는 (roomMemberId, dayNumber) 조합.
     */
    public static CoinCreditCommand uploadCompletion(Long userId, long amount, Long roomMemberId, int dayNumber) {
        return new CoinCreditCommand(userId, CoinBucket.REWARD, amount, CoinLedgerReason.UPLOAD_COMPLETION_REWARD,
                "UPLOAD_COMPLETION", roomMemberId + ":" + dayNumber);
    }
}
