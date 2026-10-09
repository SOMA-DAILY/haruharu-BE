package org.son.haruharu.domain.payment.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.domain.payment.implement.IapProduct;

public record ProductResponse(
        @Schema(description = "백엔드 내부 상품 id", example = "1") String productId,
        @Schema(description = "스토어·RevenueCat 상품 id(프런트 매칭 키)", example = "ticket_1") String storeProductId,
        @Schema(description = "지급 티켓 수량", example = "1") long ticketAmount,
        @Schema(description = "표시용 가격(정수). 실제 결제 금액은 스토어 현지가 기준", example = "7900") int displayPrice,
        @Schema(description = "통화 코드", example = "KRW") String currency,
        @Schema(description = "판매 활성 여부(목록엔 true만)", example = "true") boolean isActive
) {
    public static ProductResponse from(IapProduct product) {
        return new ProductResponse(
                product.id().toString(),
                product.storeProductId(),
                product.ticketAmount(),
                product.price(),
                product.currency(),
                product.active()
        );
    }
}
