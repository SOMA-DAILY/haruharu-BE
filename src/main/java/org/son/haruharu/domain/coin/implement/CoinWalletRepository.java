package org.son.haruharu.domain.coin.implement;

import java.util.Optional;

public interface CoinWalletRepository {

    Optional<CoinWallet> findByUserId(Long userId);

    CoinWallet save(CoinWallet wallet);
}
