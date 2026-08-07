package org.son.haruharu.domain.coin.implement;

import java.util.Optional;

public interface CoinWalletRepository {

    Optional<CoinWallet> findByUserId(Long userId);

    /** 잔액 변경 전용 조회. 행 배타 락을 걸어 동시 갱신을 직렬화한다(#298). */
    Optional<CoinWallet> findByUserIdForUpdate(Long userId);

    CoinWallet save(CoinWallet wallet);

    void deleteByUserId(Long userId);
}
