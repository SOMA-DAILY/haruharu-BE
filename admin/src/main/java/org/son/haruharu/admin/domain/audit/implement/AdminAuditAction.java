package org.son.haruharu.admin.domain.audit.implement;

/**
 * 어드민 접속기록의 행위 분류(#554). 안전성 확보조치 기준 제8조의 "수행 업무" 칸.
 * 조회 계열은 이름이 {@code VIEW} 로 시작해 요약 집계가 접두어로 묶는다.
 */
public enum AdminAuditAction {
    LOGIN,
    LOGIN_FAIL,
    LOGOUT,
    VIEW,
    VIEW_USERS,
    VIEW_USER,
    VIEW_ROOMS,
    VIEW_ROOM,
    VIEW_ROOM_ACTIVITY,
    VIEW_REPORTS,
    VIEW_REPORT,
    CREATE,
    UPDATE,
    DELETE,
    EXPORT;

    public boolean isView() {
        return name().startsWith("VIEW");
    }
}
