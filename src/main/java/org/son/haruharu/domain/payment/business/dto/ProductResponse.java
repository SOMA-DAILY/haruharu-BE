package org.son.haruharu.domain.payment.business.dto;

import org.son.haruharu.domain.payment.implement.IapProduct;

public record ProductResponse(
        String productId,
        String storeProductId,
        long coinAmount,
        int displayPrice,
        String currency,
        boolean isActive
) {
    public static ProductResponse from(IapProduct product) {
        return new ProductResponse(
                product.id().toString(),
                product.storeProductId(),
                product.coinAmount(),
                product.price(),
                product.currency(),
                product.active()
        );
    }
}
