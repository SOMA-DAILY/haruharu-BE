package org.son.haruharu.admin.domain.account.storage.exception;

import org.son.haruharu.common.exception.NotFoundException;

public class AdminAccountNotFoundException extends NotFoundException {
    public AdminAccountNotFoundException() {
        super("ADMIN_ACCOUNT_NOT_FOUND", "존재하지 않는 관리자 계정입니다.");
    }
}
