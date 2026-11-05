package org.son.haruharu.admin.domain.audit.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminAuditReviewJpaRepository extends JpaRepository<AdminAuditReviewEntity, Long> {
    List<AdminAuditReviewEntity> findAllByOrderByReviewedAtDescIdDesc();
}
