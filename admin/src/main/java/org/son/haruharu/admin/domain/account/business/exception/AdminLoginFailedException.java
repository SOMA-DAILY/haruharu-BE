package org.son.haruharu.admin.domain.account.business.exception;

import org.son.haruharu.common.exception.UnauthorizedException;

/** 이메일 불일치·비밀번호 불일치·비활성 계정 모두 동일 예외로 던져 계정 존재 여부를 노출하지 않는다. */
public class AdminLoginFailedException extends UnauthorizedException {
    public AdminLoginFailedException() {
        super("ADMIN_LOGIN_FAILED", "이메일 또는 비밀번호가 올바르지 않습니다.");
    }
}
