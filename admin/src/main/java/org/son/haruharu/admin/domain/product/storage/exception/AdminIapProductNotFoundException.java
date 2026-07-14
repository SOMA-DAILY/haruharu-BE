package org.son.haruharu.admin.domain.product.storage.exception;

import org.son.haruharu.common.exception.NotFoundException;

public class AdminIapProductNotFoundException extends NotFoundException {
    public AdminIapProductNotFoundException() {
        super("ADMIN_IAP_PRODUCT_NOT_FOUND", "존재하지 않는 코인 상품입니다.");
    }
}
