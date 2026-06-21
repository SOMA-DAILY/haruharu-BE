package org.son.haruharu.domain.user.storage.exception;

import org.son.haruharu.common.exception.NotFoundException;

public class UserNotFoundException extends NotFoundException {
    public UserNotFoundException() {
        super("USER_NOT_FOUND", "존재하지 않는 사용자입니다.");
    }
}
