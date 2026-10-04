package org.son.haruharu.domain.mission.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OperatorMissionWriter {
    private final OperatorMissionRepository repository;
    public OperatorMission save(OperatorMission mission) { return repository.save(mission); }
}
