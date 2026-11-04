package org.son.haruharu.admin.domain.audit.storage;

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
import org.son.haruharu.admin.domain.audit.implement.AdminAccessLog;
import org.son.haruharu.admin.domain.audit.implement.AdminAuditAction;
import org.son.haruharu.admin.domain.audit.implement.AdminAuditTargetType;

import java.time.LocalDateTime;

/**
 * 어드민 접속기록(#554). 추가 전용이라 BaseEntity(소프트삭제·수정시각)를 상속하지 않는다.
 * 테이블 생성은 scripts/sql/554-admin-audit-up.sql (prod admin 태스크는 ddl-auto=update 환경변수라 자동으로도 생긴다).
 */
@Entity
@Table(name = "admin_access_logs", indexes = {
        @Index(name = "idx_admin_access_logs_occurred", columnList = "occurred_at"),
        @Index(name = "idx_admin_access_logs_admin", columnList = "admin_id, occurred_at"),
        @Index(name = "idx_admin_access_logs_target", columnList = "target_type, target_id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAccessLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id")
    private Long adminId;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(length = AdminAccessLog.IP_MAX)
    private String ip;

    @Column(nullable = false, length = 10)
    private String method;

    @Column(nullable = false, length = AdminAccessLog.PATH_MAX)
    private String path;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AdminAuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 30)
    private AdminAuditTargetType targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "status_code", nullable = false)
    private int statusCode;

    @Column(length = AdminAccessLog.REASON_MAX)
    private String reason;

    @Column(length = AdminAccessLog.DETAIL_MAX)
    private String detail;

    @Column(name = "user_agent", length = AdminAccessLog.USER_AGENT_MAX)
    private String userAgent;

    public static AdminAccessLogEntity from(AdminAccessLog log) {
        var entity = new AdminAccessLogEntity();
        entity.id = log.id();
        entity.adminId = log.adminId();
        entity.occurredAt = log.occurredAt();
        entity.ip = log.ip();
        entity.method = log.method();
        entity.path = log.path();
        entity.action = log.action();
        entity.targetType = log.targetType();
        entity.targetId = log.targetId();
        entity.statusCode = log.statusCode();
        entity.reason = log.reason();
        entity.detail = log.detail();
        entity.userAgent = log.userAgent();
        return entity;
    }

    public AdminAccessLog toDomain() {
        return new AdminAccessLog(id, adminId, occurredAt, ip, method, path, action, targetType, targetId,
                statusCode, reason, detail, userAgent);
    }
}
