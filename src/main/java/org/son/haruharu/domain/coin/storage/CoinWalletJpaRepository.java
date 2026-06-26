package org.son.haruharu.domain.coin.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CoinWalletJpaRepository extends JpaRepository<CoinWalletEntity, UUID> {

    Optional<CoinWalletEntity> findByUserId(UUID userId);
}
