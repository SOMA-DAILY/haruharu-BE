package org.son.haruharu.domain.report.implement;

import org.springframework.stereotype.Component;

@Component
public class ReportReader {

    private final ReportRepository reportRepository;

    public ReportReader(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    /** 신고자가 이 대상을 이미 신고했는지(유저·대상당 1회 정책). */
    public boolean hasReported(Long reporterUserId, Long reportedUserId) {
        return reportRepository.existsByReporterAndReported(reporterUserId, reportedUserId);
    }
}
