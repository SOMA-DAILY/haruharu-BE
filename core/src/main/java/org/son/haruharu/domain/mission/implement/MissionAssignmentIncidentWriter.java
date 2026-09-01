package org.son.haruharu.domain.mission.implement;

import org.springframework.stereotype.Component;

@Component
public class MissionAssignmentIncidentWriter {

    private final MissionAssignmentIncidentRepository repository;

    public MissionAssignmentIncidentWriter(MissionAssignmentIncidentRepository repository) {
        this.repository = repository;
    }

    public MissionAssignmentIncident save(MissionAssignmentIncident incident) {
        return repository.save(incident);
    }
}
