package org.son.haruharu.devsupport.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class DevMemberNotVerifiedException extends BadRequestException {
    public DevMemberNotVerifiedException(Long userId) {
        super("DEV_MEMBER_NOT_VERIFIED", "본인인증이 없는 유저는 방 멤버가 될 수 없습니다: userId=" + userId);
    }
}
