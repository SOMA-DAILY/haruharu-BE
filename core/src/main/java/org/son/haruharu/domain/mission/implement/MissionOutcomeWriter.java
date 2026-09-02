package org.son.haruharu.domain.mission.implement;

import org.springframework.stereotype.Component;

@Component
public class MissionOutcomeWriter {

    private final MissionOutcomeRepository repository;

    public MissionOutcomeWriter(MissionOutcomeRepository repository) {
        this.repository = repository;
    }

    public MissionOutcome save(MissionOutcome outcome) {
        return repository.save(outcome);
    }
}
