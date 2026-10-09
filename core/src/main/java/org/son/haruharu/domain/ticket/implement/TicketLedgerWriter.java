package org.son.haruharu.domain.ticket.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TicketLedgerWriter {

    private final TicketLedgerRepository ticketLedgerRepository;

    public TicketLedger append(TicketLedger ledger) {
        return ticketLedgerRepository.save(ledger);
    }
}
