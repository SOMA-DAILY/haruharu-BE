package org.son.haruharu.admin.domain.audit.implement;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AdminAuditReviewReader {

    private final AdminAuditReviewRepository adminAuditReviewRepository;

    public AdminAuditReviewReader(AdminAuditReviewRepository adminAuditReviewRepository) {
        this.adminAuditReviewRepository = adminAuditReviewRepository;
    }

    public List<AdminAuditReview> findAll() {
        return adminAuditReviewRepository.findAllOrderByReviewedAtDesc();
    }
}
