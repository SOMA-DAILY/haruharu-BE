package org.son.haruharu.domain.ticket.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TicketLedgerReader {

    private final TicketLedgerRepository ticketLedgerRepository;

    public Optional<TicketLedger> findById(Long id) {
        return ticketLedgerRepository.findById(id);
    }

    public boolean existsByUserIdAndReason(Long userId, TicketLedgerReason reason) {
        return ticketLedgerRepository.existsByUserIdAndReason(userId, reason);
    }

    public List<TicketLedger> findPage(Long userId, LocalDateTime before, int limit) {
        return ticketLedgerRepository.findPage(userId, before, limit);
    }
}
