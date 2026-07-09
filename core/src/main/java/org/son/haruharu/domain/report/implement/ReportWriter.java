package org.son.haruharu.domain.report.implement;

import org.springframework.stereotype.Component;

@Component
public class ReportWriter {

    private final ReportRepository reportRepository;

    public ReportWriter(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    public Report save(Report report) {
        return reportRepository.save(report);
    }
}
