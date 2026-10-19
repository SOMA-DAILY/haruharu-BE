package org.son.haruharu.domain.cohort.business.command;

import java.time.LocalDateTime;

public record CreateCohortCommand(
        Long trackId,
        Integer sequence,
        LocalDateTime applyOpensAt,
        LocalDateTime applyClosesAt,
        LocalDateTime assignDeadlineAt,
        String periodLabel,
        Integer targetMale,
        Integer targetFemale
) {
}
