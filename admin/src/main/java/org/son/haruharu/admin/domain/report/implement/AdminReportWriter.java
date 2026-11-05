package org.son.haruharu.admin.domain.report.implement;

import org.son.haruharu.admin.domain.report.storage.AdminReportQueryRepository;
import org.son.haruharu.domain.report.implement.ReportStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 어드민 신고 쓰기 협력자. 접수 상태에서만 제재/기각으로 한 번 전이한다. */
@Component
public class AdminReportWriter {

    private final AdminReportQueryRepository queryRepository;

    public AdminReportWriter(AdminReportQueryRepository queryRepository) {
        this.queryRepository = queryRepository;
    }

    /** 전이에 성공했으면 true, 이미 처리된(접수 아님) 건이면 false. */
    public boolean resolve(Long reportId, ReportStatus result, Long adminId, LocalDateTime now) {
        return queryRepository.resolveIfReceived(reportId, result, adminId, now) == 1;
    }
}
