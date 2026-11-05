package org.son.haruharu.admin.domain.audit.implement;

import java.time.LocalDateTime;

/**
 * 어드민 접속기록 1행(#554). 요청/응답 본문은 담지 않는다 — 경로와 대상 ID 만 남긴다.
 *
 * @param adminId   로그인 실패처럼 주체를 모르는 경우 null
 * @param reason    반출(EXPORT) 사유. 그 외 행위는 null
 * @param detail    로그인 실패 시 입력한 이메일 등 짧은 보조 정보
 */
public record AdminAccessLog(
        Long id,
        Long adminId,
        LocalDateTime occurredAt,
        String ip,
        String method,
        String path,
        AdminAuditAction action,
        AdminAuditTargetType targetType,
        Long targetId,
        int statusCode,
        String reason,
        String detail,
        String userAgent
) {
    public static final int PATH_MAX = 300;
    public static final int IP_MAX = 45;
    public static final int DETAIL_MAX = 200;
    public static final int USER_AGENT_MAX = 200;
    public static final int REASON_MAX = 500;

    public static AdminAccessLog of(Long adminId, LocalDateTime occurredAt, String ip, String method, String path,
                                    AdminAuditAction action, AdminAuditTargetType targetType, Long targetId,
                                    int statusCode, String reason, String detail, String userAgent) {
        return new AdminAccessLog(null, adminId, occurredAt, truncate(ip, IP_MAX), method, truncate(path, PATH_MAX),
                action, targetType, targetId, statusCode, truncate(reason, REASON_MAX), truncate(detail, DETAIL_MAX),
                truncate(userAgent, USER_AGENT_MAX));
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
