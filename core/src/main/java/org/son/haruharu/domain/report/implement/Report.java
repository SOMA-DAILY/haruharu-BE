package org.son.haruharu.domain.report.implement;

import java.time.LocalDateTime;

/** 유저 신고(#100). 신고자·대상 유저 쌍당 1건만 존재한다(중복 신고 불가). */
public record Report(
        Long id,
        Long reporterUserId,
        Long reportedUserId,
        ReportReason reason,
        String detail,
        ReportStatus status,
        LocalDateTime createdAt
) {
    /** 신고 접수. detail은 선택 입력(null 허용). */
    public static Report received(Long reporterUserId, Long reportedUserId, ReportReason reason,
                                  String detail, LocalDateTime createdAt) {
        return new Report(null, reporterUserId, reportedUserId, reason, detail, ReportStatus.RECEIVED, createdAt);
    }
}
