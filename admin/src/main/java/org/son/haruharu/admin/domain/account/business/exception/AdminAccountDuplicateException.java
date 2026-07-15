package org.son.haruharu.admin.domain.account.business.exception;

import org.son.haruharu.common.exception.ConflictException;

public class AdminAccountDuplicateException extends ConflictException {
    public AdminAccountDuplicateException() {
        super("ADMIN_ACCOUNT_DUPLICATE", "이미 존재하는 어드민 이메일입니다.");
    }
}
