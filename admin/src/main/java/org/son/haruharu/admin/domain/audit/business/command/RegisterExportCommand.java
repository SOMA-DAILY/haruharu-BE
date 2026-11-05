package org.son.haruharu.admin.domain.audit.business.command;

import org.son.haruharu.admin.domain.audit.implement.AdminAuditTargetType;

/** 반출(다운로드) 전에 사유를 등록한다. 화면은 이 등록이 성공한 뒤에만 실제 다운로드를 진행한다. */
public record RegisterExportCommand(
        Long adminId,
        AdminAuditTargetType targetType,
        Long targetId,
        String scope,
        String reason,
        String ip,
        String userAgent
) {
}
