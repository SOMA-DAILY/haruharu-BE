package org.son.haruharu.admin.domain.cohort.implement;

import org.son.haruharu.domain.cohort.implement.CohortApplication;

import java.util.List;

/** 신청자 표 한 줄: 신청 + PASS 이름 + 번호 + 들어갔던 방(#515). 이름·번호는 없으면 null. */
public record CohortApplicant(CohortApplication application, String name, String phone, List<Long> roomIds) {
}
