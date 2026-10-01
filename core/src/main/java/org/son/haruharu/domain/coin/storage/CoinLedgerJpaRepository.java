package org.son.haruharu.domain.coin.storage;

import org.son.haruharu.domain.coin.implement.CoinLedgerReason;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface CoinLedgerJpaRepository extends JpaRepository<CoinLedgerEntity, Long> {

    boolean existsByUserIdAndReason(Long userId, CoinLedgerReason reason);

    boolean existsByUserIdAndReasonAndRefId(Long userId, CoinLedgerReason reason, String refId);

    List<CoinLedgerEntity> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    List<CoinLedgerEntity> findByUserIdAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
            Long userId, LocalDateTime before, Pageable pageable);
}
