package org.son.haruharu.domain.coin.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.domain.coin.implement.CoinWallet;

import java.time.LocalDateTime;

public record WalletResponse(
        @Schema(description = "총 잔액(유료+리워드)", example = "150") long totalBalance,
        @Schema(description = "유료 구매 코인 잔액(PAID)", example = "100") long paidBalance,
        @Schema(description = "리워드 코인 잔액(REWARD)", example = "50") long rewardBalance,
        @Schema(description = "잔액 최종 변경 시각") LocalDateTime updatedAt
) {
    public static WalletResponse from(CoinWallet wallet) {
        return new WalletResponse(
                wallet.totalBalance(),
                wallet.paidBalance(),
                wallet.rewardBalance(),
                wallet.updatedAt()
        );
    }
}
