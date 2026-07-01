package org.son.haruharu.domain.payment.storage.exception;

import org.son.haruharu.common.exception.NotFoundException;

public class IapProductNotFoundException extends NotFoundException {

    public IapProductNotFoundException(String storeProductId) {
        super("IAP_PRODUCT_NOT_FOUND", "등록되지 않은 상품입니다: storeProductId=" + storeProductId);
    }
}
