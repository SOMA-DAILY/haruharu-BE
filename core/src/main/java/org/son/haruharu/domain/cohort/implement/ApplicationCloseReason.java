package org.son.haruharu.domain.cohort.implement;

/**
 * 신청이 닫힌 이유. 값은 결정된 규칙을 모두 미리 둔다 — 늘릴 때마다 DB CHECK 를 고치지 않게.
 * 지금 쓰는 값은 USER_CANCELLED·COHORT_CANCELLED(#511)이고 나머지는 정산(#518)·방 연동(#517)에서 쓴다.
 */
public enum ApplicationCloseReason {
    /** 본인 취소 → CANCELLED */
    USER_CANCELLED,
    /** 운영진이 기수를 취소 → NOT_ASSIGNED */
    COHORT_CANCELLED,
    /** 배정 마감까지 방에 못 들어감 → NOT_ASSIGNED */
    DEADLINE_PASSED,
    /** 운영진이 제외 → NOT_ASSIGNED */
    OPERATOR_EXCLUDED,
    /** 배정된 방이 취소 → NOT_ASSIGNED */
    ROOM_CANCELLED,
    /** 배정된 방이 끝남 → COMPLETED */
    ROOM_ENDED
}
