package org.son.haruharu.admin.domain.ticket.business.command;

public record GrantTicketCommand(
        Long adminId,
        Long userId,
        long amount,
        String memo
) {
}
