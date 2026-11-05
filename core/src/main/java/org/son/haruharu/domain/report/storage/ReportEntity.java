package org.son.haruharu.domain.report.storage;

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
import org.son.haruharu.domain.report.implement.Report;
import org.son.haruharu.domain.report.implement.ReportReason;
import org.son.haruharu.domain.report.implement.ReportStatus;

@Entity
@Table(name = "reports",
        uniqueConstraints = @UniqueConstraint(name = "uq_reports_reporter_reported",
                columnNames = {"reporterUserId", "reportedUserId", "reportScope"}),
        indexes = @Index(name = "idx_reports_reported_user", columnList = "reportedUserId"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long reporterUserId;

    @Column(nullable = false)
    private Long reportedUserId;

    @Column
    private Long questionId;

    @Column(nullable = false, length = 40)
    private String reportScope;

    // enum 컬럼 길이는 EnumColumnLengthIntegrationTest가 전수 검증한다(#169) — 여유 있게 40
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ReportReason reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ReportStatus status;

    // "자세한 상황을 알려주세요 (선택)" 자유 텍스트
    @Column(length = 500)
    private String detail;

    // #553 처리자(admin id)와 처리 시각. 어드민 조건부 UPDATE 만 쓰고 앱 저장(from)은 건드리지 않는다 —
    // 도메인 Report 에 넣지 않은 이유는 앱 쪽 생성 경로 5곳을 바꾸지 않기 위해서다. 어드민은 JPQL 프로젝션으로 읽는다.
    @Column(insertable = false, updatable = false)
    private Long resolvedByAdminId;

    @Column(insertable = false, updatable = false)
    private java.time.LocalDateTime resolvedAt;

    public static ReportEntity from(Report report) {
        var entity = new ReportEntity();
        entity.id = report.id();
        entity.reporterUserId = report.reporterUserId();
        entity.reportedUserId = report.reportedUserId();
        entity.questionId = report.questionId();
        entity.reportScope = report.questionId() == null ? "USER" : "QUESTION:" + report.questionId();
        entity.reason = report.reason();
        entity.status = report.status();
        entity.detail = report.detail();
        entity.createdAt = report.createdAt();
        return entity;
    }

    public Report toDomain() {
        return new Report(id, reporterUserId, reportedUserId, reason, detail, status, createdAt, questionId);
    }
}
