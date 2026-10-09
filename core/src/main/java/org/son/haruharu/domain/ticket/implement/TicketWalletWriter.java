package org.son.haruharu.domain.ticket.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TicketWalletWriter {

    private final TicketWalletRepository ticketWalletRepository;

    public TicketWallet save(TicketWallet wallet) {
        return ticketWalletRepository.save(wallet);
    }

    public void deleteByUserId(Long userId) {
        ticketWalletRepository.deleteByUserId(userId);
    }
}
