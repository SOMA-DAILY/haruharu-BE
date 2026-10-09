package org.son.haruharu.admin.domain.product.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.domain.payment.implement.IapProduct;
import org.son.haruharu.domain.payment.implement.IapStore;

public record AdminIapProductResponse(
        @Schema(description = "상품 id", example = "1") Long id,
        @Schema(description = "스토어 상품 id", example = "ticket_1") String storeProductId,
        @Schema(description = "스토어", example = "APP_STORE") IapStore store,
        @Schema(description = "지급 티켓 수량", example = "1") long ticketAmount,
        @Schema(description = "표시 가격(폴백)", example = "7900") int price,
        @Schema(description = "통화", example = "KRW") String currency,
        @Schema(description = "판매 활성 여부", example = "true") boolean active
) {
    public static AdminIapProductResponse from(IapProduct product) {
        return new AdminIapProductResponse(
                product.id(), product.storeProductId(), product.store(),
                product.ticketAmount(), product.price(), product.currency(), product.active());
    }
}
