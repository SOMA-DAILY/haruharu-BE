package org.son.haruharu.domain.coin.business.dto;

import org.son.haruharu.domain.coin.implement.CoinBucket;
import org.son.haruharu.domain.coin.implement.CoinLedger;
import org.son.haruharu.domain.coin.implement.CoinLedgerReason;

import java.time.LocalDateTime;
import java.util.UUID;

public record LedgerResponse(
        UUID id,
        long delta,
        CoinBucket bucket,
        CoinLedgerReason reason,
        String refType,
        UUID refId,
        LocalDateTime createdAt
) {
    public static LedgerResponse from(CoinLedger ledger) {
        return new LedgerResponse(
                ledger.id(),
                ledger.delta(),
                ledger.bucket(),
                ledger.reason(),
                ledger.refType(),
                ledger.refId(),
                ledger.createdAt()
        );
    }
}
