package org.son.haruharu.domain.coin.business.command;

import java.util.UUID;

/** 코인 환불 회수 명령. PAID 잔액에서 amount만큼 회수한다(음수 허용). */
public record CoinRevokeCommand(
        UUID userId,
        long amount,
        String refType,
        UUID refId
) {
}
