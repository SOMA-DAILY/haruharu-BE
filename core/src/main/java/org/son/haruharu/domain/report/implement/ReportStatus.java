package org.son.haruharu.domain.report.implement;

/**
 * 신고 처리 상태(#100). 접수(RECEIVED)까지가 시스템 범위 — 이후 조치(제재·기각)는 운영 수동이며,
 * 상태 전이가 필요해지는 시점(어드민 도입)에 값을 추가한다.
 */
public enum ReportStatus {
    /** 접수됨 — 운영 확인 대기 */
    RECEIVED
}
