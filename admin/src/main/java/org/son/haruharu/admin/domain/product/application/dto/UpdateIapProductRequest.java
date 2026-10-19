package org.son.haruharu.admin.domain.product.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.son.haruharu.admin.domain.product.business.command.UpdateIapProductCommand;

/** 부분 수정 — 보낸 필드만 갱신. storeProductId·store는 수정 불가. */
public record UpdateIapProductRequest(
        @Schema(description = "지급 티켓 수량", example = "1") @Positive Long ticketAmount,
        @Schema(description = "표시 가격(폴백)", example = "7900") @Positive Integer price,
        // 보내면 갱신, 안 보내면 유지. 보낼 경우 빈 문자열로 덮어쓰지 못하게 막는다.
        @Schema(description = "통화", example = "KRW") @Size(min = 1) String currency,
        @Schema(description = "판매 활성 여부", example = "true") Boolean active
) {
    public UpdateIapProductCommand toCommand() {
        return new UpdateIapProductCommand(ticketAmount, price, currency, active);
    }
}
