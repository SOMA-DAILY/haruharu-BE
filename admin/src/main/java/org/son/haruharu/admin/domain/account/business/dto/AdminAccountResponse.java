package org.son.haruharu.admin.domain.account.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.admin.domain.account.implement.AdminAccount;
import org.son.haruharu.admin.domain.account.implement.AdminAccountStatus;
import org.son.haruharu.admin.domain.account.implement.AdminRole;

import java.time.LocalDateTime;

/** 어드민 계정 응답. passwordHash는 절대 노출하지 않는다. */
public record AdminAccountResponse(
        @Schema(description = "어드민 id", example = "1") Long id,
        @Schema(description = "이메일", example = "ops@haruharu.co.kr") String email,
        @Schema(description = "이름", example = "운영자") String name,
        @Schema(description = "역할", example = "OPERATOR") AdminRole role,
        @Schema(description = "상태", example = "ACTIVE") AdminAccountStatus status,
        @Schema(description = "마지막 로그인 시각") LocalDateTime lastLoginAt
) {
    public static AdminAccountResponse from(AdminAccount account) {
        return new AdminAccountResponse(
                account.id(), account.email(), account.name(),
                account.role(), account.status(), account.lastLoginAt());
    }
}
