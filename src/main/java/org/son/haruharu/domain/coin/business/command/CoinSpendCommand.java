package org.son.haruharu.domain.coin.business.command;

import org.son.haruharu.domain.coin.implement.CoinLedgerReason;

import java.util.UUID;

/** 코인 소모 명령. 리워드 먼저 차감한다. */
public record CoinSpendCommand(
        Long userId,
        long amount,
        CoinLedgerReason reason,
        String refType,
        UUID refId
) {
    /** 매칭 큐 진입 소모. 사유(MATCH_ENTER)를 coin 도메인 내부에서 고정한다. */
    public static CoinSpendCommand matchEnter(Long userId, long amount, String refType, UUID refId) {
        return new CoinSpendCommand(userId, amount, CoinLedgerReason.MATCH_ENTER, refType, refId);
    }
}
