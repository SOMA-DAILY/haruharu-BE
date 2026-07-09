package org.son.haruharu.domain.coin.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.coin.implement.CoinLedger;
import org.son.haruharu.domain.coin.implement.CoinLedgerReason;
import org.son.haruharu.domain.coin.implement.CoinLedgerRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CoinLedgerRepositoryImpl implements CoinLedgerRepository {

    private final CoinLedgerJpaRepository jpaRepository;

    @Override
    public CoinLedger save(CoinLedger ledger) {
        return jpaRepository.save(CoinLedgerEntity.from(ledger)).toDomain();
    }

    @Override
    public Optional<CoinLedger> findById(Long id) {
        return jpaRepository.findById(id).map(CoinLedgerEntity::toDomain);
    }

    @Override
    public boolean existsByUserIdAndReason(Long userId, CoinLedgerReason reason) {
        return jpaRepository.existsByUserIdAndReason(userId, reason);
    }

    @Override
    public boolean existsByUserIdAndReasonAndRefId(Long userId, CoinLedgerReason reason, String refId) {
        return jpaRepository.existsByUserIdAndReasonAndRefId(userId, reason, refId);
    }

    @Override
    public List<CoinLedger> findPage(Long userId, LocalDateTime before, int limit) {
        PageRequest page = PageRequest.of(0, limit);
        List<CoinLedgerEntity> rows = (before == null)
                ? jpaRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId, page)
                : jpaRepository.findByUserIdAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(userId, before, page);
        return rows.stream().map(CoinLedgerEntity::toDomain).toList();
    }
}
