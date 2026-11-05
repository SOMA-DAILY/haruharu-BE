package org.son.haruharu.domain.report.implement;

/**
 * 신고 처리 상태(#100, #553). 접수(RECEIVED) → 운영이 어드민에서 제재(SANCTIONED) 또는 기각(REJECTED)으로 한 번만 전이한다.
 * 전이는 admin 모듈의 조건부 UPDATE 가 담당한다.
 */
public enum ReportStatus {
    /** 접수됨 — 운영 확인 대기 */
    RECEIVED,
    /** 제재 승인 — 피신고자 BANNED */
    SANCTIONED,
    /** 기각 */
    REJECTED
}
