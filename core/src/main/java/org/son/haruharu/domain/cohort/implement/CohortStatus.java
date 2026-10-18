package org.son.haruharu.domain.cohort.implement;

/**
 * 기수 상태. DRAFT → OPEN → SETTLED, 또는 DRAFT/OPEN → CANCELLED.
 * 신청 가능 여부는 상태와 신청 기간을 함께 본다({@link Cohort#isAcceptingApplications}). 시각에 따라 상태를 바꾸는 배치는 없다.
 */
public enum CohortStatus {
    DRAFT,
    OPEN,
    SETTLED,
    CANCELLED
}
