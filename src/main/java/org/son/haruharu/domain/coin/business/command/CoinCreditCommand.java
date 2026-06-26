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
}
