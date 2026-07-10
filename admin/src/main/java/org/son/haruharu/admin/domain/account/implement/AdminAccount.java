package org.son.haruharu.admin.domain.account.implement;

import java.time.LocalDateTime;

/** 어드민 staff 계정. 비밀번호는 해시(BCrypt)만 보관한다. */
public record AdminAccount(
        Long id,
        String email,
        String passwordHash,
        String name,
        AdminRole role,
        AdminAccountStatus status,
        LocalDateTime lastLoginAt
) {
    /** 신규 ACTIVE 계정 생성. id는 저장 시 생성. */
    public static AdminAccount create(String email, String passwordHash, String name, AdminRole role) {
        return new AdminAccount(null, email, passwordHash, name, role, AdminAccountStatus.ACTIVE, null);
    }

    public boolean isActive() {
        return status == AdminAccountStatus.ACTIVE;
    }

    /** 로그인 시각 갱신본 반환(불변). */
    public AdminAccount withLastLoginAt(LocalDateTime at) {
        return new AdminAccount(id, email, passwordHash, name, role, status, at);
    }
}
