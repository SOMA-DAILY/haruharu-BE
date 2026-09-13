package org.son.haruharu.domain.report.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportJpaRepository extends JpaRepository<ReportEntity, Long> {

    @Query("select (count(r)>0) from ReportEntity r where r.reporterUserId=:reporter and r.reportedUserId=:reported and r.questionId is null")
    boolean existsByReporterUserIdAndReportedUserId(
            @Param("reporter") Long reporterUserId,
            @Param("reported") Long reportedUserId);
}
