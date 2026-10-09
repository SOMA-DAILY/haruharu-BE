package org.son.haruharu.admin.domain.product.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.son.haruharu.admin.domain.product.business.command.CreateIapProductCommand;
import org.son.haruharu.domain.payment.implement.IapStore;

public record CreateIapProductRequest(
        @Schema(description = "스토어 상품 id(App Store Connect와 정확히 일치)", example = "ticket_1")
        @NotBlank String storeProductId,
        @Schema(description = "스토어", example = "APP_STORE")
        @NotNull IapStore store,
        @Schema(description = "지급 티켓 수량", example = "1")
        @Positive long ticketAmount,
        @Schema(description = "표시 가격(폴백)", example = "7900")
        @Positive int price,
        @Schema(description = "통화", example = "KRW")
        @NotBlank String currency
) {
    public CreateIapProductCommand toCommand() {
        return new CreateIapProductCommand(storeProductId, store, ticketAmount, price, currency);
    }
}
