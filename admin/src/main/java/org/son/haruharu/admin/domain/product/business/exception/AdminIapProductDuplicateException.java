package org.son.haruharu.admin.domain.product.business.exception;

import org.son.haruharu.common.exception.ConflictException;

public class AdminIapProductDuplicateException extends ConflictException {
    public AdminIapProductDuplicateException() {
        super("ADMIN_IAP_PRODUCT_DUPLICATE", "이미 존재하는 상품 id입니다.");
    }
}
