package org.son.haruharu.domain.cohort.storage;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.cohort.implement.Cohort;
import org.son.haruharu.domain.cohort.implement.CohortStatus;

import java.time.LocalDateTime;

/**
 * 트랙당 OPEN 기수 1개는 부분 유니크 인덱스(ux_cohorts_track_open)로 막는다. JPA 로 표현할 수 없어
 * scripts/sql/510-recruit-cohort-up.sql 에만 있다. 앱은 트랙 행 잠금으로 먼저 거른다.
 */
@Entity
@Table(name = "cohorts",
        uniqueConstraints = @UniqueConstraint(name = "uq_cohorts_track_sequence", columnNames = {"track_id", "sequence"}),
        indexes = @Index(name = "idx_cohorts_status", columnList = "status"),
        check = {
                @CheckConstraint(name = "ck_cohorts_apply_window", constraint = "apply_opens_at < apply_closes_at"),
                @CheckConstraint(name = "ck_cohorts_assign_deadline", constraint = "assign_deadline_at >= apply_closes_at")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CohortEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "track_id", nullable = false)
    private Long trackId;

    @Column(nullable = false)
    private int sequence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CohortStatus status;

    @Column(name = "apply_opens_at", nullable = false)
    private LocalDateTime applyOpensAt;

    @Column(name = "apply_closes_at", nullable = false)
    private LocalDateTime applyClosesAt;

    @Column(name = "assign_deadline_at", nullable = false)
    private LocalDateTime assignDeadlineAt;

    @Column(name = "period_label", nullable = false, length = 40)
    private String periodLabel;

    @Column(name = "target_male")
    private Integer targetMale;

    @Column(name = "target_female")
    private Integer targetFemale;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @Column(name = "created_by", length = 40)
    private String createdBy;

    public static CohortEntity from(Cohort cohort) {
        var entity = new CohortEntity();
        entity.id = cohort.id();
        entity.trackId = cohort.trackId();
        entity.sequence = cohort.sequence();
        entity.status = cohort.status();
        entity.applyOpensAt = cohort.applyOpensAt();
        entity.applyClosesAt = cohort.applyClosesAt();
        entity.assignDeadlineAt = cohort.assignDeadlineAt();
        entity.periodLabel = cohort.periodLabel();
        entity.targetMale = cohort.targetMale();
        entity.targetFemale = cohort.targetFemale();
        entity.settledAt = cohort.settledAt();
        entity.createdBy = cohort.createdBy();
        entity.createdAt = cohort.createdAt();
        entity.updatedAt = cohort.updatedAt();
        return entity;
    }

    public Cohort toDomain() {
        return new Cohort(id, trackId, sequence, status, applyOpensAt, applyClosesAt, assignDeadlineAt, periodLabel,
                targetMale, targetFemale, settledAt, createdBy, createdAt, updatedAt);
    }
}
