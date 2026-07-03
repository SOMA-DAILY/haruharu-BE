package org.son.haruharu.domain.coin.storage;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface CoinLedgerJpaRepository extends JpaRepository<CoinLedgerEntity, Long> {

    List<CoinLedgerEntity> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    List<CoinLedgerEntity> findByUserIdAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
            Long userId, LocalDateTime before, Pageable pageable);
}
