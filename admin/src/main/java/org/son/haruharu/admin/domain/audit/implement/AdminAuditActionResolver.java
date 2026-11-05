package org.son.haruharu.admin.domain.audit.implement;

import java.util.Map;
import java.util.Optional;

/**
 * 요청의 메서드·매칭 패턴·path variable 로 접속기록의 행위와 대상을 정한다(#554).
 * <p>
 * 순수 함수라 인터셉터와 분리해 단위 테스트한다. 패턴은 {@code /api/v1/admin/rooms/{roomId}/activities} 처럼
 * 핸들러 매핑이 고른 템플릿이고, 매칭 핸들러가 없으면(404) 실제 URI 가 들어온다.
 */
public final class AdminAuditActionResolver {

    private static final String PREFIX = "/api/v1/admin";
    private static final String LOGIN = PREFIX + "/auth/login";
    private static final String LOGOUT = PREFIX + "/auth/logout";
    private static final String ME = PREFIX + "/me";
    private static final String EXPORTS = PREFIX + "/audit/exports";

    /** 기록할 행위와 대상. 기록 대상이 아니면 {@link #resolve} 가 빈 값을 돌려준다. */
    public record Resolved(AdminAuditAction action, AdminAuditTargetType targetType, Long targetId) {
    }

    private AdminAuditActionResolver() {
    }

    public static Optional<Resolved> resolve(String method, String pattern, Map<String, String> variables, int status) {
        if (pattern == null || !pattern.startsWith(PREFIX) || "OPTIONS".equalsIgnoreCase(method)) {
            return Optional.empty();
        }
        if (LOGIN.equals(pattern)) {
            return Optional.of(new Resolved(status < 400 ? AdminAuditAction.LOGIN : AdminAuditAction.LOGIN_FAIL, null, null));
        }
        if (LOGOUT.equals(pattern)) {
            return Optional.of(new Resolved(AdminAuditAction.LOGOUT, null, null));
        }
        // 내 정보는 열람이 아니다. 반출 사유 등록은 서비스가 EXPORT 로 남기므로 CREATE 중복 기록을 막는다.
        // 접속기록 열람 자체(관리자 IP·이메일이 보임)는 일반 VIEW 로 남긴다.
        if (ME.equals(pattern) || EXPORTS.equals(pattern)) {
            return Optional.empty();
        }

        Target target = resolveTarget(pattern, variables == null ? Map.of() : variables);
        AdminAuditAction action = switch (method == null ? "" : method.toUpperCase()) {
            case "GET", "HEAD" -> viewAction(pattern, target);
            case "POST" -> AdminAuditAction.CREATE;
            case "PUT", "PATCH" -> AdminAuditAction.UPDATE;
            case "DELETE" -> AdminAuditAction.DELETE;
            default -> AdminAuditAction.VIEW;
        };
        return Optional.of(new Resolved(action, target.type(), target.id()));
    }

    private static AdminAuditAction viewAction(String pattern, Target target) {
        String rest = pattern.substring(PREFIX.length());
        if (target.type() == AdminAuditTargetType.USER) {
            return AdminAuditAction.VIEW_USER;
        }
        if (target.type() == AdminAuditTargetType.ROOM) {
            if (rest.endsWith("/activities") || rest.endsWith("/activity/summary") || rest.endsWith("/selections")) {
                return AdminAuditAction.VIEW_ROOM_ACTIVITY;
            }
            return AdminAuditAction.VIEW_ROOM;
        }
        if (target.type() == AdminAuditTargetType.REPORT) {
            return AdminAuditAction.VIEW_REPORT;
        }
        if (rest.equals("/users")) {
            return AdminAuditAction.VIEW_USERS;
        }
        if (rest.equals("/rooms")) {
            return AdminAuditAction.VIEW_ROOMS;
        }
        if (rest.equals("/reports")) {
            return AdminAuditAction.VIEW_REPORTS;
        }
        return AdminAuditAction.VIEW;
    }

    private record Target(AdminAuditTargetType type, Long id) {
        static final Target NONE = new Target(null, null);
    }

    private static Target resolveTarget(String pattern, Map<String, String> variables) {
        // 신고는 경로 어디에 있든 신고 건이 주체(#553 어드민 신고 API 와 호환).
        if (pattern.contains("/reports/")) {
            return target(AdminAuditTargetType.REPORT, firstOf(variables, "reportId", "id"));
        }
        if (variables.containsKey("userId")) {
            return target(AdminAuditTargetType.USER, variables.get("userId"));
        }
        if (variables.containsKey("roomId")) {
            return target(AdminAuditTargetType.ROOM, variables.get("roomId"));
        }
        if (variables.containsKey("cohortId")) {
            return target(AdminAuditTargetType.COHORT, variables.get("cohortId"));
        }
        if (variables.containsKey("trackId")) {
            return target(AdminAuditTargetType.RECRUIT_TRACK, variables.get("trackId"));
        }
        String id = variables.get("id");
        if (id == null) {
            return Target.NONE;
        }
        if (pattern.contains("/admins/")) {
            return target(AdminAuditTargetType.ADMIN, id);
        }
        if (pattern.contains("/iap/products/")) {
            return target(AdminAuditTargetType.IAP_PRODUCT, id);
        }
        if (pattern.contains("/iap/purchases/")) {
            return target(AdminAuditTargetType.IAP_PURCHASE, id);
        }
        if (pattern.contains("/mid-participation/campaigns/")) {
            return target(AdminAuditTargetType.MID_PARTICIPATION_CAMPAIGN, id);
        }
        return Target.NONE;
    }

    private static String firstOf(Map<String, String> variables, String... keys) {
        for (String key : keys) {
            if (variables.containsKey(key)) {
                return variables.get(key);
            }
        }
        return null;
    }

    private static Target target(AdminAuditTargetType type, String rawId) {
        if (rawId == null) {
            return new Target(type, null);
        }
        try {
            return new Target(type, Long.parseLong(rawId));
        } catch (NumberFormatException e) {
            return new Target(type, null);
        }
    }
}
