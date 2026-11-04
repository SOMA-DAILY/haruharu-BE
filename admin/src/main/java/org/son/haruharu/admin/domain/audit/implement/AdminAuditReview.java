package org.son.haruharu.admin.domain.audit.implement;

import java.time.LocalDateTime;

/**
 * 접속기록 정기 점검 기록(#554). 안전성 확보조치 기준 제8조의 "월 1회 이상 점검" 증빙.
 *
 * @param period 점검 대상 월, {@code YYYY-MM}
 */
public record AdminAuditReview(
        Long id,
        String period,
        Long adminId,
        String note,
        LocalDateTime reviewedAt
) {
    public static final int NOTE_MAX = 500;

    public static AdminAuditReview create(String period, Long adminId, String note, LocalDateTime reviewedAt) {
        return new AdminAuditReview(null, period, adminId, note, reviewedAt);
    }
}
