package org.son.haruharu.admin.domain.audit.implement;

import org.springframework.stereotype.Component;

@Component
public class AdminAccessLogWriter {

    private final AdminAccessLogRepository adminAccessLogRepository;

    public AdminAccessLogWriter(AdminAccessLogRepository adminAccessLogRepository) {
        this.adminAccessLogRepository = adminAccessLogRepository;
    }

    public AdminAccessLog save(AdminAccessLog log) {
        return adminAccessLogRepository.save(log);
    }
}
