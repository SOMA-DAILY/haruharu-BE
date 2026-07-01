package org.son.haruharu.domain.coin.business.command;

import org.son.haruharu.domain.coin.implement.CoinBucket;
import org.son.haruharu.domain.coin.implement.CoinLedgerReason;

import java.util.UUID;

/** 코인 적립 명령. 지정 버킷에 amount만큼 적립한다. */
public record CoinCreditCommand(
        UUID userId,
        CoinBucket bucket,
        long amount,
        CoinLedgerReason reason,
        String refType,
        UUID refId
) {
    /**
     * 인앱결제(IAP) 구매 적립. 버킷(PAID)·사유(PURCHASE)를 coin 도메인 내부에서 고정해,
     * 호출 도메인이 coin 내부 enum을 알 필요 없게 한다.
     */
    public static CoinCreditCommand purchase(UUID userId, long amount, String refType, UUID refId) {
        return new CoinCreditCommand(userId, CoinBucket.PAID, amount, CoinLedgerReason.PURCHASE, refType, refId);
    }
}
