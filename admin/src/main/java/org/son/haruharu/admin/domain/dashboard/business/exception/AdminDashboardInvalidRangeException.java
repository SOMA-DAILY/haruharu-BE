package org.son.haruharu.admin.domain.dashboard.business.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class AdminDashboardInvalidRangeException extends BadRequestException {
    public AdminDashboardInvalidRangeException() {
        super("ADMIN_DASHBOARD_INVALID_RANGE", "from은 to보다 이후일 수 없습니다.");
    }
}
