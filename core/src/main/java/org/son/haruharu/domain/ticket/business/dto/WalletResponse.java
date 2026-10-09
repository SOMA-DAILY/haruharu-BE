package org.son.haruharu.domain.ticket.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.domain.ticket.implement.TicketWallet;

import java.time.LocalDateTime;

public record WalletResponse(
        @Schema(description = "티켓 잔액", example = "1") long balance,
        @Schema(description = "잔액 최종 변경 시각") LocalDateTime updatedAt
) {

    public static WalletResponse from(TicketWallet wallet) {
        return new WalletResponse(wallet.balance(), wallet.updatedAt());
    }
}
