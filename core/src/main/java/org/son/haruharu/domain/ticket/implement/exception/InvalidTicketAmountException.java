package org.son.haruharu.domain.ticket.implement.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class InvalidTicketAmountException extends BadRequestException {

    public InvalidTicketAmountException() {
        super("TICKET_INVALID_AMOUNT", "티켓 금액은 0보다 커야 합니다.");
    }
}
