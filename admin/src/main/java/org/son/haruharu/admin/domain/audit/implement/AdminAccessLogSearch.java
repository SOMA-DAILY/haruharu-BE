package org.son.haruharu.admin.domain.audit.implement;

import java.time.LocalDateTime;

/** 접속기록 목록 필터. null 인 조건은 적용하지 않는다. {@code to} 는 미포함 상한. */
public record AdminAccessLogSearch(
        Long adminId,
        AdminAuditAction action,
        AdminAuditTargetType targetType,
        Long targetId,
        LocalDateTime from,
        LocalDateTime to
) {
}
