package org.son.haruharu.domain.ticket.business.command;

/** 티켓 사용 명령. */
public record TicketSpendCommand(
        Long userId,
        long amount,
        String refType,
        String refId
) {
}
