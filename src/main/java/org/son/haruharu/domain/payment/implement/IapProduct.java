package org.son.haruharu.domain.payment.implement;

import java.util.UUID;

/** 코인 상품 카탈로그. storeProductId ↔ 지급 코인 수량 매핑의 신뢰 원천. */
public record IapProduct(
        UUID id,
        String storeProductId,
        IapStore store,
        long coinAmount,
        int price,
        String currency,
        boolean active
) {
}
