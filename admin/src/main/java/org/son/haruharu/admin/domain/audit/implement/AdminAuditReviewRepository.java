package org.son.haruharu.admin.domain.audit.implement;

import java.util.List;

public interface AdminAuditReviewRepository {

    AdminAuditReview save(AdminAuditReview review);

    /** 최근 점검부터. */
    List<AdminAuditReview> findAllOrderByReviewedAtDesc();
}
