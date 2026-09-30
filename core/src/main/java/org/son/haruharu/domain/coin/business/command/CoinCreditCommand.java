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
     * 첫 가입 보너스 적립. 리워드 버킷(환불 회수 무관)으로 적립하고, refType/refId로 유저당 1회 멱등을 보장한다.
     */
    public static CoinCreditCommand signupBonus(Long userId, long amount) {
        return new CoinCreditCommand(userId, CoinBucket.REWARD, amount, CoinLedgerReason.SIGNUP_BONUS,
                "SIGNUP_BONUS", String.valueOf(userId));
    }

    /**
     * 세션 미매칭 위로 구름 적립(#143). 리워드 버킷으로 적립하고, refId(roomId)로 유저·방당 1회 멱등을 보장한다.
     */
    public static CoinCreditCommand matchConsolation(Long userId, long amount, Long roomId) {
        return new CoinCreditCommand(userId, CoinBucket.REWARD, amount, CoinLedgerReason.MATCH_CONSOLATION,
                "ROOM", roomId.toString());
    }
}
