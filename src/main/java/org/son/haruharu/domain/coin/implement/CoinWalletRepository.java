package org.son.haruharu.domain.coin.implement;

import java.util.Optional;
import java.util.UUID;

public interface CoinWalletRepository {

    Optional<CoinWallet> findByUserId(UUID userId);

    CoinWallet save(CoinWallet wallet);
}
