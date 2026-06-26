package org.son.haruharu.domain.coin.business.command;

import org.son.haruharu.domain.coin.implement.CoinLedgerReason;

import java.util.UUID;

/** 코인 소모 명령. 리워드 먼저 차감한다. */
public record CoinSpendCommand(
        UUID userId,
        long amount,
        CoinLedgerReason reason,
        String refType,
        UUID refId
) {
}
