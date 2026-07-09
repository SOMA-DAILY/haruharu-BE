package org.son.haruharu.domain.report.storage;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportJpaRepository extends JpaRepository<ReportEntity, Long> {

    boolean existsByReporterUserIdAndReportedUserId(Long reporterUserId, Long reportedUserId);
}
