package org.son.haruharu.admin.domain.product.business.command;

import org.son.haruharu.domain.payment.implement.IapStore;

public record CreateIapProductCommand(
        String storeProductId,
        IapStore store,
        long coinAmount,
        long bonusAmount,
        int price,
        String currency
) {
}
