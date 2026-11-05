package org.son.haruharu.admin.domain.audit.storage;

import org.son.haruharu.admin.domain.audit.implement.AdminAuditReview;
import org.son.haruharu.admin.domain.audit.implement.AdminAuditReviewRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AdminAuditReviewRepositoryImpl implements AdminAuditReviewRepository {

    private final AdminAuditReviewJpaRepository jpaRepository;

    public AdminAuditReviewRepositoryImpl(AdminAuditReviewJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public AdminAuditReview save(AdminAuditReview review) {
        return jpaRepository.save(AdminAuditReviewEntity.from(review)).toDomain();
    }

    @Override
    public List<AdminAuditReview> findAllOrderByReviewedAtDesc() {
        return jpaRepository.findAllByOrderByReviewedAtDescIdDesc().stream()
                .map(AdminAuditReviewEntity::toDomain).toList();
    }
}
