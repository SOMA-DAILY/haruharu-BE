package org.son.haruharu.domain.coin.business.command;

import org.son.haruharu.domain.coin.implement.CoinLedgerReason;

/** 코인 소모 명령. 리워드 먼저 차감한다. */
public record CoinSpendCommand(
        Long userId,
        long amount,
        CoinLedgerReason reason,
        String refType,
        String refId
) {
}
