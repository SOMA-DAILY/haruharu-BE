package org.son.haruharu.domain.coin.implement;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.coin.storage.exception.CoinWalletNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CoinWalletReader {

    private final CoinWalletRepository coinWalletRepository;

    public Optional<CoinWallet> findByUserId(Long userId) {
        return coinWalletRepository.findByUserId(userId);
    }

    public CoinWallet getByUserId(Long userId) {
        return coinWalletRepository.findByUserId(userId)
                .orElseThrow(CoinWalletNotFoundException::new);
    }
}
