package org.son.haruharu.domain.ticket.implement.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class InsufficientTicketException extends BadRequestException {

    public InsufficientTicketException() {
        super("TICKET_INSUFFICIENT", "티켓 잔액이 부족합니다.");
    }
}
