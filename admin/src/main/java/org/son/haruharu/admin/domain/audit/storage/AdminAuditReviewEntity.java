package org.son.haruharu.admin.domain.audit.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.admin.domain.audit.implement.AdminAuditReview;

import java.time.LocalDateTime;

/** 접속기록 정기 점검 기록(#554). 추가 전용. */
@Entity
@Table(name = "admin_audit_reviews", indexes = @Index(name = "idx_admin_audit_reviews_period", columnList = "period"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAuditReviewEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 7)
    private String period;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Column(length = AdminAuditReview.NOTE_MAX)
    private String note;

    @Column(name = "reviewed_at", nullable = false)
    private LocalDateTime reviewedAt;

    public static AdminAuditReviewEntity from(AdminAuditReview review) {
        var entity = new AdminAuditReviewEntity();
        entity.id = review.id();
        entity.period = review.period();
        entity.adminId = review.adminId();
        entity.note = review.note();
        entity.reviewedAt = review.reviewedAt();
        return entity;
    }

    public AdminAuditReview toDomain() {
        return new AdminAuditReview(id, period, adminId, note, reviewedAt);
    }
}
