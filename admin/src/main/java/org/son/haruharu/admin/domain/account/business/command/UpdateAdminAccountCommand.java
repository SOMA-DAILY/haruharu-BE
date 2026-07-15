package org.son.haruharu.admin.domain.account.business.command;

import org.son.haruharu.admin.domain.account.implement.AdminAccountStatus;
import org.son.haruharu.admin.domain.account.implement.AdminRole;

/** 어드민 계정 부분 수정 입력. null 필드는 기존 값 유지. */
public record UpdateAdminAccountCommand(
        AdminRole role,
        AdminAccountStatus status
) {
}
