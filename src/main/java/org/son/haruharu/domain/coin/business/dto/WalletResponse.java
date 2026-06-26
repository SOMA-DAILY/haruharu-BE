package org.son.haruharu.domain.coin.business.dto;

import org.son.haruharu.domain.coin.implement.CoinWallet;

import java.time.LocalDateTime;

public record WalletResponse(
        long totalBalance,
        long paidBalance,
        long rewardBalance,
        LocalDateTime updatedAt
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
