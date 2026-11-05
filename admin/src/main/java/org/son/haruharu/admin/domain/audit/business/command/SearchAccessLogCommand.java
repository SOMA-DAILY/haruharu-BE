package org.son.haruharu.admin.domain.audit.business.command;

import org.son.haruharu.admin.domain.audit.implement.AdminAuditAction;
import org.son.haruharu.admin.domain.audit.implement.AdminAuditTargetType;

import java.time.LocalDate;

/** 접속기록 목록 조건. from/to 는 날짜(포함). */
public record SearchAccessLogCommand(
        Long adminId,
        AdminAuditAction action,
        AdminAuditTargetType targetType,
        Long targetId,
        LocalDate from,
        LocalDate to,
        int page,
        int size
) {
}
