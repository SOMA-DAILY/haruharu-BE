package org.son.haruharu.admin.domain.payment.storage.exception;

import org.son.haruharu.common.exception.NotFoundException;

public class AdminPurchaseNotFoundException extends NotFoundException {
    public AdminPurchaseNotFoundException() {
        super("ADMIN_PURCHASE_NOT_FOUND", "존재하지 않는 결제 내역입니다.");
    }
}
