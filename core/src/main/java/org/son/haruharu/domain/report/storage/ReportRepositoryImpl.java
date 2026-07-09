package org.son.haruharu.domain.report.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.report.implement.Report;
import org.son.haruharu.domain.report.implement.ReportRepository;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ReportRepositoryImpl implements ReportRepository {

    private final ReportJpaRepository jpaRepository;

    @Override
    public Report save(Report report) {
        return jpaRepository.save(ReportEntity.from(report)).toDomain();
    }

    @Override
    public boolean existsByReporterAndReported(Long reporterUserId, Long reportedUserId) {
        return jpaRepository.existsByReporterUserIdAndReportedUserId(reporterUserId, reportedUserId);
    }
}
