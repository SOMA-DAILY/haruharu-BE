package org.son.haruharu.domain.mission.implement;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class MissionAssignmentIncidentReader {

    private final MissionAssignmentIncidentRepository repository;

    public MissionAssignmentIncidentReader(MissionAssignmentIncidentRepository repository) {
        this.repository = repository;
    }

    public Optional<MissionAssignmentIncident> findBySlotId(Long roomMissionSlotId) {
        return repository.findByRoomMissionSlotId(roomMissionSlotId);
    }
}
