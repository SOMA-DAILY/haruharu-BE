package org.son.haruharu.admin.domain.audit.implement;

/** 기간 내 관리자별 조회(VIEW*) 수와 반출(EXPORT) 수. */
public record AdminAuditCount(Long adminId, long views, long exports) {
}
