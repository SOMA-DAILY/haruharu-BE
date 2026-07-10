package org.son.haruharu.admin.domain.account.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.admin.domain.account.implement.AdminAccount;
import org.son.haruharu.admin.domain.account.implement.AdminRole;

/** 현재 로그인한 관리자 정보. */
public record AdminMeResponse(
        @Schema(description = "관리자 계정 id", example = "1") Long adminId,
        @Schema(description = "이메일", example = "admin@haruharu.app") String email,
        @Schema(description = "이름", example = "슈퍼관리자") String name,
        @Schema(description = "역할", example = "SUPER") AdminRole role
) {
    public static AdminMeResponse from(AdminAccount account) {
        return new AdminMeResponse(account.id(), account.email(), account.name(), account.role());
    }
}
