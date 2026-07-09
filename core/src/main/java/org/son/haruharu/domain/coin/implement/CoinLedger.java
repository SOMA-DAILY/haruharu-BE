package org.son.haruharu.domain.coin.implement;

import java.time.LocalDateTime;

/**
 * 코인 원장 항목. append-only — 모든 코인 이동의 단일 진실원천.
 * delta는 +적립 / -소모. bucket은 어느 주머니가 움직였는지를 나타낸다.
 * balanceAfter는 이 항목 적용 직후의 총 잔액(paid+reward) 스냅샷 — 이용내역 화면의 거래별 잔액 표시용.
 */
public record CoinLedger(
        Long id,
        Long userId,
        long delta,
        long balanceAfter,
        CoinBucket bucket,
        CoinLedgerReason reason,
        String refType,
        String refId,
        LocalDateTime createdAt
) {
    public static CoinLedger of(
            Long userId,
            long delta,
            long balanceAfter,
            CoinBucket bucket,
            CoinLedgerReason reason,
            String refType,
            String refId,
            LocalDateTime createdAt
    ) {
        return new CoinLedger(null, userId, delta, balanceAfter, bucket, reason, refType, refId, createdAt);
    }
}
