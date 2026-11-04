package org.son.haruharu.admin.domain.audit.business.dto;

import org.son.haruharu.admin.domain.audit.implement.AdminAccessLog;
import org.son.haruharu.admin.domain.audit.implement.AdminAuditAction;
import org.son.haruharu.admin.domain.audit.implement.AdminAuditTargetType;

import java.time.LocalDateTime;

public record AdminAccessLogResponse(
        Long logId,
        LocalDateTime occurredAt,
        AdminAuditAdminResponse admin,
        String ip,
        AdminAuditAction action,
        AdminAuditTargetType targetType,
        Long targetId,
        String method,
        String path,
        int statusCode,
        String reason,
        String detail
) {
    public static AdminAccessLogResponse from(AdminAccessLog log, String adminName) {
        AdminAuditAdminResponse admin = log.adminId() == null ? null : new AdminAuditAdminResponse(log.adminId(), adminName);
        return new AdminAccessLogResponse(log.id(), log.occurredAt(), admin, log.ip(), log.action(), log.targetType(),
                log.targetId(), log.method(), log.path(), log.statusCode(), log.reason(), log.detail());
    }
}
