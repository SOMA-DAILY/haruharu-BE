package org.son.haruharu.domain.ticket.storage.exception;

import org.son.haruharu.common.exception.NotFoundException;

public class TicketWalletNotFoundException extends NotFoundException {

    public TicketWalletNotFoundException() {
        super("TICKET_WALLET_NOT_FOUND", "티켓 지갑을 찾을 수 없습니다.");
    }
}
