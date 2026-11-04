package org.son.haruharu.admin.domain.audit.business.dto;

import java.time.LocalDate;
import java.util.List;

/** 선택 기간의 관리자별 조회·반출 수와 대량 조회(하루 기준치 이상) 목록. */
public record AdminAuditSummaryResponse(
        LocalDate from,
        LocalDate to,
        long heavyViewThreshold,
        List<PerAdmin> perAdmin,
        List<HeavyViewer> heavyViewers
) {
    public record PerAdmin(AdminAuditAdminResponse admin, long views, long exports) {
    }

    public record HeavyViewer(AdminAuditAdminResponse admin, LocalDate date, long count) {
    }
}
