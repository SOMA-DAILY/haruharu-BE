package org.son.haruharu.domain.coin.implement;

import java.util.Optional;

public interface CoinWalletRepository {

    Optional<CoinWallet> findByUserId(Long userId);

    /** 잔액 변경 전용 조회. 행 배타 락을 걸어 동시 갱신을 직렬화한다(#298). */
    Optional<CoinWallet> findByUserIdForUpdate(Long userId);

    /**
     * soft-delete된 지갑까지 포함한 존재 여부. user_id 유니크 제약이 죽은 행에도 걸리므로
     * 지갑 생성 전 확인에는 이걸 쓴다(#298).
     */
    boolean existsIncludingDeleted(Long userId);

    CoinWallet save(CoinWallet wallet);

    void deleteByUserId(Long userId);
}
