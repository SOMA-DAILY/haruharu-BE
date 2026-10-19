package org.son.haruharu.domain.cohort.business.command;

import java.time.LocalDateTime;

/** 부분 수정 — null 이 아닌 칸만 바꾼다. 트랙은 바꿀 수 없다. */
public record UpdateCohortCommand(
        Integer sequence,
        LocalDateTime applyOpensAt,
        LocalDateTime applyClosesAt,
        LocalDateTime assignDeadlineAt,
        String periodLabel,
        Integer targetMale,
        Integer targetFemale
) {
}
