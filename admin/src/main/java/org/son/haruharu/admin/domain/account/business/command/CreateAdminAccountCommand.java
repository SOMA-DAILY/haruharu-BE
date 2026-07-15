package org.son.haruharu.admin.domain.account.business.command;

import org.son.haruharu.admin.domain.account.implement.AdminRole;

/** 어드민 계정 생성 입력. rawPassword는 서비스에서 BCrypt 해시로 변환된다. */
public record CreateAdminAccountCommand(
        String email,
        String rawPassword,
        String name,
        AdminRole role
) {
}
