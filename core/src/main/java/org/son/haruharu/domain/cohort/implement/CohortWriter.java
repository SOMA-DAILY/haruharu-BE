package org.son.haruharu.domain.cohort.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CohortWriter {

    private final CohortRepository cohortRepository;

    public Cohort save(Cohort cohort) {
        return cohortRepository.save(cohort);
    }
}
