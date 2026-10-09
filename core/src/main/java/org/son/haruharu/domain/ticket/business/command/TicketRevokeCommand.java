package org.son.haruharu.domain.ticket.business.command;

/** 티켓 환불 회수 명령. 남은 잔액 한도에서만 회수한다. */
public record TicketRevokeCommand(
        Long userId,
        long amount,
        String refType,
        String refId
) {
}
