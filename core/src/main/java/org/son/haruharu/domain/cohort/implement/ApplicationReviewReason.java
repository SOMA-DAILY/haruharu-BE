package org.son.haruharu.domain.cohort.implement;

/** 확인 필요(NEEDS_REVIEW)가 된 이유. 여러 개가 겹치면 위에 있는 것 하나만 남긴다. */
public enum ApplicationReviewReason {
    /** PASS 성별이 MALE·FEMALE 이 아니다. */
    UNKNOWN_GENDER,
    /** PASS 성별이 신청 성별(프로필 성별)과 다르다. */
    GENDER_MISMATCH,
    /** PASS 생년월일로 계산한 연 나이(올해 − 출생연도)가 트랙 범위 밖이다. */
    AGE_OUT_OF_RANGE
}
