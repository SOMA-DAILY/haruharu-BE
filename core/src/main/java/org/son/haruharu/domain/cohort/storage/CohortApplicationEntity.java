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
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.cohort.implement.ApplicantGender;
import org.son.haruharu.domain.cohort.implement.ApplicationCloseReason;
import org.son.haruharu.domain.cohort.implement.ApplicationReviewReason;
import org.son.haruharu.domain.cohort.implement.CohortApplication;
import org.son.haruharu.domain.cohort.implement.CohortApplicationStatus;
import org.son.haruharu.domain.cohort.implement.FriendPreference;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 진행 중 신청 사람당 1건은 부분 유니크 인덱스(ux_cohort_applications_user_active)로 막는다. JPA 로 표현할 수 없어
 * scripts/sql/511-cohort-application-up.sql 에만 있다. 앱은 지갑 행 잠금으로 같은 유저의 신청을 먼저 한 줄로 세운다.
 */
@Entity
@Table(name = "cohort_applications",
        indexes = {
                @Index(name = "idx_cohort_applications_pool", columnList = "cohort_id, status, priority DESC, queued_at"),
                @Index(name = "idx_cohort_applications_user", columnList = "user_id")
        },
        check = @CheckConstraint(name = "ck_cohort_applications_priority", constraint = "priority >= 0"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CohortApplicationEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "cohort_id", nullable = false)
    private Long cohortId;

    @Column(name = "track_id", nullable = false)
    private Long trackId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CohortApplicationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "declared_gender", length = 10)
    private ApplicantGender declaredGender;

    // PASS 성별 원문 스냅샷. 해석은 ApplicantGender.parse 가 한다.
    @Column(length = 20)
    private String gender;

    private LocalDate birthday;

    @Column(nullable = false, length = 40)
    private String school;

    @Column(nullable = false, length = 15)
    private String department;

    // 트랙 추가 문항의 답(PostgreSQL jsonb 객체, 문항 key → 답).
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, String> answers;

    @Column(name = "referral_source", length = 40)
    private String referralSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "friend_preference", length = 20)
    private FriendPreference friendPreference;

    @Column(length = 300)
    private String motivation;

    @Column(name = "survey_submitted_at")
    private LocalDateTime surveySubmittedAt;

    @Column(nullable = false)
    private int priority;

    @Column(name = "applied_at", nullable = false)
    private LocalDateTime appliedAt;

    @Column(name = "queued_at")
    private LocalDateTime queuedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_reason", length = 30)
    private ApplicationReviewReason reviewReason;

    @Column(name = "room_id")
    private Long roomId;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "assigned_by", length = 40)
    private String assignedBy;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "close_reason", length = 30)
    private ApplicationCloseReason closeReason;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Column(name = "admin_memo", length = 500)
    private String adminMemo;

    public static CohortApplicationEntity from(CohortApplication application) {
        var entity = new CohortApplicationEntity();
        entity.id = application.id();
        entity.userId = application.userId();
        entity.cohortId = application.cohortId();
        entity.trackId = application.trackId();
        entity.status = application.status();
        entity.declaredGender = application.declaredGender();
        entity.gender = application.passGender();
        entity.birthday = application.birthday();
        entity.school = application.school();
        entity.department = application.department();
        entity.answers = new LinkedHashMap<>(application.answers());
        entity.referralSource = application.referralSource();
        entity.friendPreference = application.friendPreference();
        entity.motivation = application.motivation();
        entity.surveySubmittedAt = application.surveySubmittedAt();
        entity.priority = application.priority();
        entity.appliedAt = application.appliedAt();
        entity.queuedAt = application.queuedAt();
        entity.reviewReason = application.reviewReason();
        entity.roomId = application.roomId();
        entity.assignedAt = application.assignedAt();
        entity.assignedBy = application.assignedBy();
        entity.closedAt = application.closedAt();
        entity.closeReason = application.closeReason();
        entity.refundedAt = application.refundedAt();
        entity.adminMemo = application.adminMemo();
        entity.createdAt = application.createdAt();
        entity.updatedAt = application.updatedAt();
        return entity;
    }

    public CohortApplication toDomain() {
        return new CohortApplication(id, userId, cohortId, trackId, status, declaredGender, gender, birthday,
                school, department, answers, referralSource, friendPreference, motivation, surveySubmittedAt,
                priority, appliedAt, queuedAt, reviewReason, roomId, assignedAt, assignedBy, closedAt, closeReason,
                refundedAt, adminMemo, createdAt, updatedAt);
    }
}
