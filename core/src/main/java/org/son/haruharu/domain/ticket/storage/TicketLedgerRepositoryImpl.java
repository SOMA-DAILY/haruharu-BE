package org.son.haruharu.domain.ticket.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.ticket.implement.TicketLedger;
import org.son.haruharu.domain.ticket.implement.TicketLedgerReason;
import org.son.haruharu.domain.ticket.implement.TicketLedgerRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class TicketLedgerRepositoryImpl implements TicketLedgerRepository {

    private final TicketLedgerJpaRepository jpaRepository;

    @Override
    public TicketLedger save(TicketLedger ledger) {
        return jpaRepository.save(TicketLedgerEntity.from(ledger)).toDomain();
    }

    @Override
    public Optional<TicketLedger> findById(Long id) {
        return jpaRepository.findById(id).map(TicketLedgerEntity::toDomain);
    }

    @Override
    public boolean existsByUserIdAndReason(Long userId, TicketLedgerReason reason) {
        return jpaRepository.existsByUserIdAndReason(userId, reason);
    }

    @Override
    public List<TicketLedger> findPage(Long userId, LocalDateTime before, int limit) {
        PageRequest page = PageRequest.of(0, limit);
        List<TicketLedgerEntity> rows = (before == null)
                ? jpaRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId, page)
                : jpaRepository.findByUserIdAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(userId, before, page);
        return rows.stream().map(TicketLedgerEntity::toDomain).toList();
    }
}
