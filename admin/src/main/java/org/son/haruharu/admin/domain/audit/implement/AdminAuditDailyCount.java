package org.son.haruharu.admin.domain.audit.implement;

import java.time.LocalDate;

/** 관리자 한 명의 하루 조회 수. 대량 조회 점검에 쓴다. */
public record AdminAuditDailyCount(Long adminId, LocalDate date, long count) {
}
