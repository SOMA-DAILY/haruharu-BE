package org.son.haruharu.admin.domain.ticket.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record AdminTicketGrantResponse(
        @Schema(description = "지급받은 유저 id", example = "42") Long userId,
        @Schema(description = "지급한 티켓 수량", example = "1") long grantedAmount,
        @Schema(description = "지급 후 잔액", example = "2") long balance
) {
}
