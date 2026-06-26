package org.son.haruharu.domain.coin.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CoinWalletWriter {

    private final CoinWalletRepository coinWalletRepository;

    public CoinWallet save(CoinWallet wallet) {
        return coinWalletRepository.save(wallet);
    }
}
