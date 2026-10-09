package org.son.haruharu.domain.ticket.storage;

import org.son.haruharu.domain.ticket.implement.TicketLedgerReason;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface TicketLedgerJpaRepository extends JpaRepository<TicketLedgerEntity, Long> {

    boolean existsByUserIdAndReason(Long userId, TicketLedgerReason reason);

    List<TicketLedgerEntity> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    List<TicketLedgerEntity> findByUserIdAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
            Long userId, LocalDateTime before, Pageable pageable);
}
