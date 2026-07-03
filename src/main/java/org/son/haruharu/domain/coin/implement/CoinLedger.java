package org.son.haruharu.domain.coin.implement;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 코인 원장 항목. append-only — 모든 코인 이동의 단일 진실원천.
 * delta는 +적립 / -소모. bucket은 어느 주머니가 움직였는지를 나타낸다.
 */
public record CoinLedger(
        UUID id,
        Long userId,
        long delta,
        CoinBucket bucket,
        CoinLedgerReason reason,
        String refType,
        UUID refId,
        LocalDateTime createdAt
) {
    public static CoinLedger of(
            Long userId,
            long delta,
            CoinBucket bucket,
            CoinLedgerReason reason,
            String refType,
            UUID refId,
            LocalDateTime createdAt
    ) {
        return new CoinLedger(null, userId, delta, bucket, reason, refType, refId, createdAt);
    }
}
