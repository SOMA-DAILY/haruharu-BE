package org.son.haruharu.admin.domain.audit.implement;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class AdminAccessLogReader {

    private final AdminAccessLogRepository adminAccessLogRepository;

    public AdminAccessLogReader(AdminAccessLogRepository adminAccessLogRepository) {
        this.adminAccessLogRepository = adminAccessLogRepository;
    }

    public Page<AdminAccessLog> search(AdminAccessLogSearch search, Pageable pageable) {
        return adminAccessLogRepository.search(search, pageable);
    }

    public List<AdminAuditCount> countByAdmin(LocalDateTime from, LocalDateTime to) {
        return adminAccessLogRepository.countByAdmin(from, to);
    }

    public List<AdminAuditDailyCount> findHeavyViewDays(LocalDateTime from, LocalDateTime to, long minCount) {
        return adminAccessLogRepository.findHeavyViewDays(from, to, minCount);
    }
}
