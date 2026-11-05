package org.son.haruharu.admin.domain.audit.business.dto;

import org.son.haruharu.admin.domain.audit.implement.AdminAuditReview;

import java.time.LocalDateTime;

public record AdminAuditReviewResponse(
        Long reviewId,
        String period,
        AdminAuditAdminResponse admin,
        String note,
        LocalDateTime reviewedAt
) {
    public static AdminAuditReviewResponse from(AdminAuditReview review, String adminName) {
        return new AdminAuditReviewResponse(review.id(), review.period(),
                new AdminAuditAdminResponse(review.adminId(), adminName), review.note(), review.reviewedAt());
    }
}
