package org.son.haruharu.domain.report.implement;

import java.time.LocalDateTime;

/** 사용자 신고는 대상별 1회, 익명 콘텐츠 신고는 질문별 1회 접수한다. */
public record Report(
        Long id,
        Long reporterUserId,
        Long reportedUserId,
        ReportReason reason,
        String detail,
        ReportStatus status,
        LocalDateTime createdAt,
        Long questionId
) {
    public Report(Long id,Long reporterUserId,Long reportedUserId,ReportReason reason,String detail,
            ReportStatus status,LocalDateTime createdAt) {
        this(id,reporterUserId,reportedUserId,reason,detail,status,createdAt,null);
    }
    /** Content reports use an independent deduplication scope so user reports cannot reveal an anonymous sender. */
    public static Report question(Long reporterUserId,Long reportedUserId,Long questionId,ReportReason reason,
            String detail,LocalDateTime createdAt) {
        return new Report(null,reporterUserId,reportedUserId,reason,detail,ReportStatus.RECEIVED,createdAt,questionId);
    }
    /** 신고 접수. detail은 선택 입력(null 허용). */
    public static Report received(Long reporterUserId, Long reportedUserId, ReportReason reason,
                                  String detail, LocalDateTime createdAt) {
        return new Report(null, reporterUserId, reportedUserId, reason, detail, ReportStatus.RECEIVED, createdAt);
    }
}
