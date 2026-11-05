package org.son.haruharu.admin.domain.audit.implement;

import org.springframework.stereotype.Component;

@Component
public class AdminAuditReviewWriter {

    private final AdminAuditReviewRepository adminAuditReviewRepository;

    public AdminAuditReviewWriter(AdminAuditReviewRepository adminAuditReviewRepository) {
        this.adminAuditReviewRepository = adminAuditReviewRepository;
    }

    public AdminAuditReview save(AdminAuditReview review) {
        return adminAuditReviewRepository.save(review);
    }
}
