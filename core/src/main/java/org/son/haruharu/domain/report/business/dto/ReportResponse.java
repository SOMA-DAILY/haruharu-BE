package org.son.haruharu.domain.report.business.dto;

import org.son.haruharu.domain.report.implement.Report;

/** 신고 접수 결과. 전역 SNAKE_CASE 직렬화로 report_id로 응답된다. */
public record ReportResponse(Long reportId) {

    public static ReportResponse from(Report report) {
        return new ReportResponse(report.id());
    }
}
