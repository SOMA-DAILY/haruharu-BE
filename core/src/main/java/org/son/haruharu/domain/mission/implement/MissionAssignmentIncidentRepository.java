package org.son.haruharu.domain.mission.implement;

import java.util.Optional;

public interface MissionAssignmentIncidentRepository {

    Optional<MissionAssignmentIncident> findByRoomMissionSlotId(Long roomMissionSlotId);

    MissionAssignmentIncident save(MissionAssignmentIncident incident);
}
