package org.son.haruharu.domain.coin.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.coin.implement.CoinWallet;
import org.son.haruharu.domain.coin.implement.CoinWalletRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CoinWalletRepositoryImpl implements CoinWalletRepository {

    private final CoinWalletJpaRepository jpaRepository;

    @Override
    public Optional<CoinWallet> findByUserId(Long userId) {
        return jpaRepository.findByUserId(userId).map(CoinWalletEntity::toDomain);
    }

    @Override
    public Optional<CoinWallet> findByUserIdForUpdate(Long userId) {
        return jpaRepository.findByUserIdForUpdate(userId).map(CoinWalletEntity::toDomain);
    }

    @Override
    public CoinWallet save(CoinWallet wallet) {
        return jpaRepository.save(CoinWalletEntity.from(wallet)).toDomain();
    }

    @Override
    public void deleteByUserId(Long userId) {
        jpaRepository.deleteByUserId(userId);
    }
}
