package org.son.haruharu.admin.domain.user.storage.exception;

import org.son.haruharu.common.exception.NotFoundException;

public class AdminUserNotFoundException extends NotFoundException {
    public AdminUserNotFoundException() {
        super("ADMIN_USER_NOT_FOUND", "존재하지 않는 유저입니다.");
    }
}
