package org.son.haruharu.domain.profile.storage.exception;

import org.son.haruharu.common.exception.NotFoundException;

public class UserProfileNotFoundException extends NotFoundException {

    public UserProfileNotFoundException() {
        super("USER_PROFILE_NOT_FOUND", "프로필 정보를 찾을 수 없습니다.");
    }
}
