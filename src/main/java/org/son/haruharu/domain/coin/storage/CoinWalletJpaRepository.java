package org.son.haruharu.domain.coin.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CoinWalletJpaRepository extends JpaRepository<CoinWalletEntity, Long> {

    Optional<CoinWalletEntity> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
