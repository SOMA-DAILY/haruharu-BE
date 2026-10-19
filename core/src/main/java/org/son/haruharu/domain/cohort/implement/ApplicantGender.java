package org.son.haruharu.domain.cohort.implement;

import java.util.Optional;

/** 신청 판정에 쓰는 성별. 방이 남녀 정원으로 나뉘어 두 값만 다룬다. */
public enum ApplicantGender {
    MALE, FEMALE;

    /** PASS·프로필의 성별 문자열을 해석한다. 그 외 값(OTHER 등)·null 은 해석하지 않는다. */
    public static Optional<ApplicantGender> parse(String value) {
        if ("MALE".equals(value)) return Optional.of(MALE);
        if ("FEMALE".equals(value)) return Optional.of(FEMALE);
        return Optional.empty();
    }
}
