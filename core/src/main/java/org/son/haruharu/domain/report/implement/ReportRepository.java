package org.son.haruharu.domain.report.implement;

public interface ReportRepository {

    Report save(Report report);

    boolean existsByReporterAndReported(Long reporterUserId, Long reportedUserId);
}
